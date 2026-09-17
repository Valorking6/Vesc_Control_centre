package com.example.vesccontrolcentre.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.edit
import com.example.vesccontrolcentre.MainActivity
import com.example.vesccontrolcentre.R
import com.example.vesccontrolcentre.ble.VescBleManager
import com.example.vesccontrolcentre.logging.CsvLogger
import com.example.vesccontrolcentre.logging.GpxLogger
import com.example.vesccontrolcentre.model.ProfileConfig
import com.example.vesccontrolcentre.model.ProfileType
import com.example.vesccontrolcentre.model.TelemetryData
import com.example.vesccontrolcentre.sound.EngineSoundManager
import com.example.vesccontrolcentre.widget.BaseProfileWidgetProvider
import com.example.vesccontrolcentre.widget.BaseTelemetryWidget
import com.example.vesccontrolcentre.widget.SoundToggleWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.example.vesccontrolcentre.health.HealthConnectManager
import java.util.Locale

class VescService : Service(), SensorEventListener, SharedPreferences.OnSharedPreferenceChangeListener {

    companion object {
        private const val TAG = "VescService"

        const val CHANNEL_ID = "vesc_control_centre_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START_TELEMETRY = "com.example.vesccontrolcentre.ACTION_START_TELEMETRY"
        const val ACTION_STOP_TELEMETRY = "com.example.vesccontrolcentre.ACTION_STOP_TELEMETRY"
        const val ACTION_APPLY_PROFILE = "com.example.vesccontrolcentre.ACTION_APPLY_PROFILE"

        const val EXTRA_DEVICE_ADDRESS = "extra_device_address"
        const val EXTRA_POLE_PAIRS = "extra_pole_pairs"
        const val EXTRA_WHEEL_DIAMETER = "extra_wheel_diameter"
        const val EXTRA_PROFILE_KEY = "extra_profile_key"

        private val _telemetryState = MutableStateFlow(TelemetryData())
        val telemetryState: StateFlow<TelemetryData> = _telemetryState.asStateFlow()

        private val _activeProfileState = MutableStateFlow<String?>(null)
        val activeProfileState: StateFlow<String?> = _activeProfileState.asStateFlow()

        var isServiceRunning = false
            private set

        fun startTelemetry(context: Context, deviceAddress: String, polePairs: Int = 7, wheelDiameter: Float = 10.0f) {
            val intent = Intent(context, VescService::class.java).apply {
                action = ACTION_START_TELEMETRY
                putExtra(EXTRA_DEVICE_ADDRESS, deviceAddress)
                putExtra(EXTRA_POLE_PAIRS, polePairs)
                putExtra(EXTRA_WHEEL_DIAMETER, wheelDiameter)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopTelemetry(context: Context) {
            val intent = Intent(context, VescService::class.java).apply {
                action = ACTION_STOP_TELEMETRY
            }
            context.startService(intent)
        }

        fun applyProfile(context: Context, profileType: ProfileType) {
            val intent = Intent(context, VescService::class.java).apply {
                action = ACTION_APPLY_PROFILE
                putExtra(EXTRA_PROFILE_KEY, profileType.key)
            }
            if (isServiceRunning) {
                context.startService(intent)
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }
    }

    private var vescBleManager: VescBleManager? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main)
    private var observeJob: Job? = null
    private var eventsJob: Job? = null

    private var gpxLogger: GpxLogger? = null
    private var csvLogger: CsvLogger? = null
    private var engineSoundManager: EngineSoundManager? = null
    private var currentEngineSoundResId: Int = 0

    private var locationManager: LocationManager? = null
    private var locationListener: LocationListener? = null
    private var currentLat = 0.0
    private var currentLon = 0.0
    private var currentAlt = 0.0

    private var sensorManager: SensorManager? = null
    private var accelX = 0f
    private var accelY = 0f
    private var accelZ = 0f
    private var gyroX = 0f
    private var gyroY = 0f
    private var gyroZ = 0f

    private var wasConnected = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val prefs = getSharedPreferences("vesc_prefs", MODE_PRIVATE)
        _activeProfileState.value = prefs.getString("active_profile", null)
        prefs.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.d(TAG, "onStartCommand action=$action")

        when (action) {
            ACTION_STOP_TELEMETRY -> {
                finalizeRideLogsAndStop()
                return START_NOT_STICKY
            }

            ACTION_APPLY_PROFILE -> {
                val profileKey = intent.getStringExtra(EXTRA_PROFILE_KEY) ?: ProfileType.NORMAL.key
                val profileType = ProfileType.fromKey(profileKey) ?: ProfileType.NORMAL
                handleApplyProfile(profileType)
                return START_STICKY
            }

            ACTION_START_TELEMETRY, null -> {
                val deviceAddress = intent?.getStringExtra(EXTRA_DEVICE_ADDRESS)
                    ?: getSharedPreferences("vesc_prefs", MODE_PRIVATE).getString("mac_address", "") ?: ""
                val polePairs = intent?.getIntExtra(EXTRA_POLE_PAIRS, 7)
                    ?: getSharedPreferences("vesc_prefs", MODE_PRIVATE).getInt("pole_pairs", 7)
                val wheelDiameter = intent?.getFloatExtra(EXTRA_WHEEL_DIAMETER, 10.0f)
                    ?: getSharedPreferences("vesc_prefs", MODE_PRIVATE).getFloat("wheel_diameter", 10.0f)

                if (deviceAddress.isBlank()) {
                    Log.e(TAG, "Target BLE MAC address is missing!")
                    Toast.makeText(this, "Target VESC MAC address missing!", Toast.LENGTH_SHORT).show()
                    if (!isServiceRunning) stopSelf()
                    return START_NOT_STICKY
                }

                initAndConnect(deviceAddress, polePairs, wheelDiameter)
                return START_STICKY
            }

            else -> return START_NOT_STICKY
        }
    }

    private fun initAndConnect(deviceAddress: String, polePairs: Int, wheelDiameter: Float) {
        isServiceRunning = true
        wasConnected = false

        val prefs = getSharedPreferences("vesc_prefs", MODE_PRIVATE)
        val logGpx = prefs.getBoolean("log_gpx_enabled", true)
        val logCsv = prefs.getBoolean("log_csv_enabled", true)

        if (logGpx) {
            gpxLogger = GpxLogger(this)
            startGpsUpdates()
        }
        if (logCsv) {
            csvLogger = CsvLogger(this)
            startSensorUpdates()
        }
        val initialNotification = buildNotification(null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                initialNotification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, initialNotification)
        }

        if (vescBleManager == null) {
            val manager = VescBleManager(this, polePairs, wheelDiameter)
            vescBleManager = manager

            observeJob?.cancel()
            observeJob = serviceScope.launch {
                manager.telemetryData.collect { data ->
                    _telemetryState.value = data

                    if (data.isConnected) {
                        wasConnected = true
                        updateNotification(data)

                        // Engine Sound Simulator Processing
                        val livePrefs = getSharedPreferences("vesc_prefs", MODE_PRIVATE)
                        val soundEnabled = livePrefs.getBoolean("engine_sound_enabled", false)
                        val soundResId = livePrefs.getInt("engine_sound_res_id", R.raw.snd_636066_lumamorph_eight_cylinder_engine_idling)

                        if (soundEnabled) {
                            if (engineSoundManager == null || currentEngineSoundResId != soundResId) {
                                currentEngineSoundResId = soundResId
                                if (engineSoundManager == null) {
                                    engineSoundManager = EngineSoundManager()
                                }
                                engineSoundManager?.startEngineSound(this@VescService, soundResId)
                            }
                            engineSoundManager?.updatePitchAndVolume(data.erpm, data.mph)
                        } else {
                            if (engineSoundManager != null) {
                                engineSoundManager?.stopEngineSound()
                                engineSoundManager = null
                                currentEngineSoundResId = 0
                            }
                        }

                        // Log CSV Telemetry Data
                        csvLogger?.logData(
                            mph = data.mph,
                            voltage = data.voltage,
                            motorAmps = data.motorCurrent,
                            batteryAmps = data.batteryCurrent,
                            dutyCycle = data.dutyCycle,
                            tempMosfet = data.tempMosfet,
                            tempMotor = data.tempMotor,
                            wattHoursUsed = data.wattHoursUsed,
                            ampHoursCharged = data.ampHoursCharged,
                            tachAbs = data.tachometerAbs,
                            faultCode = data.faultCode,
                            accelX = accelX,
                            accelY = accelY,
                            accelZ = accelZ,
                            gyroX = gyroX,
                            gyroY = gyroY,
                            gyroZ = gyroZ,
                            adcThrottle = data.adcThrottle,
                            adcBrake = data.adcBrake,
                            activeRideDurationMs = data.activeRideDurationMs,
                            timestampMs = System.currentTimeMillis()
                        )

                        BaseTelemetryWidget.sendTelemetryBroadcast(this@VescService, data)
                    } else if (wasConnected) {
                        Log.d(TAG, "Scooter disconnected after active connection. Finalizing logs...")
                        finalizeRideLogsAndStop()
                    }
                }
            }

            eventsJob?.cancel()
            eventsJob = serviceScope.launch {
                manager.profileEvents.collect { msg ->
                    Toast.makeText(this@VescService, msg, Toast.LENGTH_SHORT).show()
                }
            }

            manager.connect(deviceAddress)
        } else {
            vescBleManager?.updateConfig(polePairs, wheelDiameter)
            vescBleManager?.connect(deviceAddress)
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == "engine_sound_enabled" && isServiceRunning) {
            val enabled = sharedPreferences?.getBoolean("engine_sound_enabled", false) ?: false
            if (!enabled) {
                engineSoundManager?.stopEngineSound()
                engineSoundManager = null
                currentEngineSoundResId = 0
            }
        }
    }

    private fun handleApplyProfile(profileType: ProfileType) {
        val prefs = getSharedPreferences("vesc_prefs", MODE_PRIVATE)
        val deviceAddress = prefs.getString("mac_address", "") ?: ""
        val polePairs = prefs.getInt("pole_pairs", 7)
        val wheelDiameter = prefs.getFloat("wheel_diameter", 10.0f)

        prefs.edit { putString("active_profile", profileType.key) }
        _activeProfileState.value = profileType.key

        BaseProfileWidgetProvider.updateAllWidgets(this)

        if (!isServiceRunning || vescBleManager == null) {
            if (deviceAddress.isBlank()) {
                Toast.makeText(this, "Set VESC MAC Address first!", Toast.LENGTH_SHORT).show()
                return
            }
            initAndConnect(deviceAddress, polePairs, wheelDiameter)
        }

        val config = ProfileConfig.load(this, profileType)
        vescBleManager?.applyProfile(config)
    }

    private fun startSensorUpdates() {
        try {
            sensorManager = getSystemService(SENSOR_SERVICE) as? SensorManager
            val accel = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            val gyro = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
            
            accel?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
            gyro?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting sensor updates: ${e.message}")
        }
    }
    
    private fun stopSensorUpdates() {
        try {
            sensorManager?.unregisterListener(this)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping sensor updates: ${e.message}")
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                accelX = event.values[0]
                accelY = event.values[1]
                accelZ = event.values[2]
            }
            Sensor.TYPE_GYROSCOPE -> {
                gyroX = event.values[0]
                gyroY = event.values[1]
                gyroZ = event.values[2]
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    }

    @SuppressLint("MissingPermission")
    private fun startGpsUpdates() {
        try {
            locationManager = getSystemService(LOCATION_SERVICE) as? LocationManager
            locationListener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    currentLat = location.latitude
                    currentLon = location.longitude
                    currentAlt = location.altitude
                    if (currentLat != 0.0 || currentLon != 0.0) {
                        val speed = if (location.hasSpeed()) location.speed else 0f
                        val bearing = if (location.hasBearing()) location.bearing else 0f
                        val accuracy = if (location.hasAccuracy()) location.accuracy else 0f
                        val satellites = location.extras?.getInt("satellites", 0) ?: 0
                        
                        gpxLogger?.logTrackPoint(
                            currentLat, 
                            currentLon, 
                            currentAlt, 
                            speed, 
                            bearing, 
                            accuracy, 
                            satellites, 
                            System.currentTimeMillis()
                        )
                    }
                }
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }

            locationManager?.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000L,
                1f,
                locationListener!!,
                Looper.getMainLooper()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error starting GPS updates: ${e.message}", e)
        }
    }

    private fun stopGpsUpdates() {
        try {
            locationListener?.let { locationManager?.removeUpdates(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping GPS: ${e.message}")
        } finally {
            locationListener = null
            locationManager = null
        }
    }

    private fun finalizeRideLogsAndStop() {
        Log.d(TAG, "Finalizing ride logs & stopping engine sound...")

        engineSoundManager?.stopEngineSound()
        engineSoundManager = null
        currentEngineSoundResId = 0
        getSharedPreferences("vesc_prefs", MODE_PRIVATE).edit { putBoolean("engine_sound_enabled", false) }
        SoundToggleWidgetProvider.updateAllWidgets(this)

        val totalActiveTimeMs = _telemetryState.value.activeRideDurationMs

        val prefs = getSharedPreferences("vesc_prefs", MODE_PRIVATE)
        val syncHealthConnect = prefs.getBoolean("sync_health_connect", false)

        val gpsTrackPoints = gpxLogger?.getGpsTrackPoints() ?: emptyList()
        val totalDistanceMeters = gpxLogger?.getTotalDistanceMeters() ?: 0f

        if (syncHealthConnect && totalActiveTimeMs > 0) {
            val healthManager = HealthConnectManager(this)
            val startTimeMs = System.currentTimeMillis() - totalActiveTimeMs
            val endTimeMs = System.currentTimeMillis()

            serviceScope.launch(Dispatchers.IO) {
                healthManager.writeExerciseSession(
                    startTimeMs = startTimeMs,
                    endTimeMs = endTimeMs,
                    totalDistanceMeters = totalDistanceMeters,
                    gpsTrack = gpsTrackPoints
                )
            }
        }

        gpxLogger?.closeLog()
        gpxLogger = null

        csvLogger?.closeLog()
        csvLogger = null

        stopGpsUpdates()
        stopSensorUpdates()

        Handler(Looper.getMainLooper()).post {
            Toast.makeText(
                applicationContext,
                "Ride Logs Successfully Saved to Documents",
                Toast.LENGTH_LONG
            ).show()
        }

        updateNotificationOnDisconnect("VESC Disconnected", "Ride logs saved.")

        observeJob?.cancel()
        observeJob = null
        eventsJob?.cancel()
        eventsJob = null
        vescBleManager?.disconnect()
        vescBleManager = null
        isServiceRunning = false
        wasConnected = false

        _telemetryState.value = TelemetryData(isConnected = false, statusText = "Disconnected")
        BaseTelemetryWidget.sendTelemetryBroadcast(this, TelemetryData())

        serviceScope.launch {
            delay(1000)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    @SuppressLint("MissingPermission")
    private fun updateNotification(data: TelemetryData) {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val notification = buildNotification(data)
        try {
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Error posting notification: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    private fun updateNotificationOnDisconnect(title: String, contentText: String) {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val mainIntent = Intent(this, MainActivity::class.java)
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_telemetry)
            .setOngoing(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentPendingIntent)
            .build()

        try {
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating disconnect notification: ${e.message}")
        }
    }

    private fun buildNotification(data: TelemetryData?): Notification {
        val mainIntent = Intent(this, MainActivity::class.java)
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val activeProfile = _activeProfileState.value ?: "Default"

        val title = if (data?.isConnected == true) {
            "VESC Control Centre [$activeProfile]"
        } else {
            "VESC Control Centre (${data?.statusText ?: "Connecting..."})"
        }

        val contentText = if (data?.isConnected == true) {
            val seconds = (data.activeRideDurationMs / 1000) % 60
            val minutes = (data.activeRideDurationMs / (1000 * 60)) % 60
            val hours = (data.activeRideDurationMs / (1000 * 60 * 60))
            val timeStr = if (hours > 0) {
                String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format(Locale.US, "%02d:%02d", minutes, seconds)
            }
            String.format(Locale.US, "Speed: %.1f MPH | Battery: %.1f V | Amps: %.1f A | Time: %s", data.mph, data.voltage, data.batteryCurrent, timeStr)
        } else {
            "Status: ${data?.statusText ?: "Connecting..."}"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_telemetry)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentPendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "VESC Control Centre",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Live VESC speed, battery telemetry, and profile status."
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy called. Stopping engine sound and closing loggers...")
        getSharedPreferences("vesc_prefs", MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(this)
        
        engineSoundManager?.stopEngineSound()
        engineSoundManager = null
        currentEngineSoundResId = 0

        gpxLogger?.closeLog()
        gpxLogger = null
        csvLogger?.closeLog()
        csvLogger = null
        stopGpsUpdates()
        stopSensorUpdates()

        observeJob?.cancel()
        observeJob = null
        eventsJob?.cancel()
        eventsJob = null
        vescBleManager?.disconnect()
        vescBleManager = null
        isServiceRunning = false
        super.onDestroy()
    }
}
