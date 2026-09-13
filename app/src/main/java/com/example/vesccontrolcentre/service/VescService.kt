package com.example.vesccontrolcentre.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
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
import com.example.vesccontrolcentre.widget.BaseProfileWidgetProvider
import com.example.vesccontrolcentre.widget.BaseTelemetryWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

class VescService : Service() {

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

    private var locationManager: LocationManager? = null
    private var locationListener: LocationListener? = null
    private var currentLat = 0.0
    private var currentLon = 0.0
    private var currentAlt = 0.0

    private var wasConnected = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val prefs = getSharedPreferences("vesc_prefs", MODE_PRIVATE)
        _activeProfileState.value = prefs.getString("active_profile", null)
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
        val logGpx = prefs.getBoolean("log_gpx_enabled", false)
        val logCsv = prefs.getBoolean("log_csv_enabled", false)

        if (logGpx) {
            gpxLogger = GpxLogger(this)
            startGpsUpdates()
        }
        if (logCsv) {
            csvLogger = CsvLogger(this)
        }

        val initialNotification = buildNotification(0f, 0f, false, "Connecting to VESC...")
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
                        updateNotification(data.mph, data.voltage, true, data.statusText)

                        // Log CSV Telemetry Data
                        csvLogger?.logData(
                            mph = data.mph,
                            voltage = data.voltage,
                            motorAmps = data.motorCurrent,
                            batteryAmps = data.batteryCurrent,
                            timestampMs = System.currentTimeMillis(),
                            dutyCycle = data.dutyCycle,
                            tempMosfet = data.tempMosfet,
                            tempMotor = data.tempMotor,
                            wattHoursUsed = data.wattHoursUsed,
                            ampHoursCharged = data.ampHoursCharged
                        )

                        // Log GPX Location Data if available
                        if (currentLat != 0.0 || currentLon != 0.0) {
                            gpxLogger?.logTrackPoint(currentLat, currentLon, currentAlt, System.currentTimeMillis())
                        }

                        BaseTelemetryWidget.sendTelemetryBroadcast(this@VescService, data)
                    } else if (wasConnected) {
                        // Detect Scooter Shutdown / Disconnection
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
                        gpxLogger?.logTrackPoint(currentLat, currentLon, currentAlt, System.currentTimeMillis())
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
        Log.d(TAG, "Finalizing ride logs...")

        gpxLogger?.closeLog()
        gpxLogger = null

        csvLogger?.closeLog()
        csvLogger = null

        stopGpsUpdates()

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
    private fun updateNotification(mph: Float, voltage: Float, isConnected: Boolean, statusText: String) {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val notification = buildNotification(mph, voltage, isConnected, statusText)
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

    private fun buildNotification(
        mph: Float,
        voltage: Float,
        isConnected: Boolean,
        statusText: String
    ): Notification {
        val mainIntent = Intent(this, MainActivity::class.java)
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, VescService::class.java).apply {
            action = ACTION_STOP_TELEMETRY
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val activeProfile = _activeProfileState.value ?: "Default"

        val title = if (isConnected) {
            String.format(Locale.US, "%.1f MPH | %.1f V [%s]", mph, voltage, activeProfile)
        } else {
            "VESC Control ($statusText)"
        }

        val contentText = if (isConnected) {
            String.format(Locale.US, "Voltage: %.1fV - Profile: %s Active", voltage, activeProfile)
        } else {
            "Status: $statusText"
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
            .addAction(R.drawable.ic_telemetry, "Disconnect", stopPendingIntent)
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
        Log.d(TAG, "onDestroy called. Closing loggers and cleaning up...")
        gpxLogger?.closeLog()
        gpxLogger = null
        csvLogger?.closeLog()
        csvLogger = null
        stopGpsUpdates()

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
