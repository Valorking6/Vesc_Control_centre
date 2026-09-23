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
import com.example.vesccontrolcentre.ai.GeminiAnalyst
import com.example.vesccontrolcentre.ai.OnnxWakeWordEngine
import com.example.vesccontrolcentre.ai.ElevenLabsManager
import com.example.vesccontrolcentre.telemetry.VescTelemetry
import kotlinx.coroutines.flow.catch
import java.io.File
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.tts.TextToSpeech
import java.util.Locale
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlin.random.Random
import android.location.Geocoder
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import java.text.SimpleDateFormat
import java.util.Date
import com.example.vesccontrolcentre.health.HealthConnectManager
import com.example.vesccontrolcentre.model.getFaultString
import com.example.vesccontrolcentre.settings.UserSettingsManager
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import org.json.JSONObject
import java.util.Calendar
import kotlin.math.min

class VescService : Service(), SensorEventListener, SharedPreferences.OnSharedPreferenceChangeListener {

    companion object {
        private const val TAG = "VescService"

        const val CHANNEL_ID = "vesc_control_centre_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START_TELEMETRY = "com.example.vesccontrolcentre.ACTION_START_TELEMETRY"
        const val ACTION_STOP_TELEMETRY = "com.example.vesccontrolcentre.ACTION_STOP_TELEMETRY"
        const val ACTION_APPLY_PROFILE = "com.example.vesccontrolcentre.ACTION_APPLY_PROFILE"
        const val ACTION_TRIGGER_SYNC_MARKER = "com.example.vesccontrolcentre.ACTION_TRIGGER_SYNC_MARKER"
        const val ACTION_DROP_SYNC_MARKER = "com.example.vesccontrolcentre.ACTION_DROP_SYNC_MARKER"
        const val ACTION_WAKE_WORD_TRIGGERED = "com.example.vesccontrolcentre.WAKE_WORD_TRIGGERED"
        const val EXTRA_WAKE_WORD_NAME = "WAKE_WORD_NAME"

        fun triggerSyncMarker(context: Context) {
            val intent = Intent(context, VescService::class.java).apply {
                action = ACTION_TRIGGER_SYNC_MARKER
            }
            if (isServiceRunning) {
                context.startService(intent)
            }
        }

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

        fun startTelemetry(context: Context, deviceAddress: String, polePairs: Int = 12, wheelDiameter: Float = 10.0f) {
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
    
    private var rideStartTimeMs = 0L
    private var proactiveAssistantJob: Job? = null

    private var gpxLogger: GpxLogger? = null
    private var csvLogger: CsvLogger? = null
    private var engineSoundManager: EngineSoundManager? = null
    private var currentEngineSoundResId: Int = 0

    private var locationManager: LocationManager? = null
    private var locationListener: LocationListener? = null
    private var currentLat = 0.0
    private var currentLon = 0.0
    private var currentAlt = 0.0
    private var currentGpsSpeed = 0f
    private var currentGpsBearing = 0f

    private var sensorManager: SensorManager? = null
    private var accelX = 0f
    private var accelY = 0f
    private var accelZ = 0f
    private var gyroX = 0f
    private var gyroY = 0f
    private var gyroZ = 0f

    private var tts: TextToSpeech? = null

    private val geminiAnalyst by lazy { 
        GeminiAnalyst(
            appContext = this,
            onCommandReceived = { jsonCommand ->
                Log.d(TAG, "Executing hardware action: $jsonCommand")
                processJsonIntent(jsonCommand)
            },
            onSpeechResponse = { responseText ->
                Log.d(TAG, "Friday speech response: $responseText")
                speak(responseText)
            }
        ) 
    }
    private var currentRiderBpm: Int = 0
    private var onnxEngine: OnnxWakeWordEngine? = null

    private var wasConnected = false
    private var hasPlayedStartupGreeting = false
    
    private val targetMac = "C8:DC:63:2A:F6:6E"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        isServiceRunning = true

        tts = TextToSpeech(applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.setLanguage(Locale.US)
                Log.d(TAG, "Built-in TextToSpeech initialized successfully.")
            }
        }

        val prefs = getSharedPreferences("vesc_prefs", MODE_PRIVATE)
        _activeProfileState.value = prefs.getString("active_profile", null)
        prefs.registerOnSharedPreferenceChangeListener(this)
        
        val initialPolePairs = prefs.getInt("pole_pairs", 7)
        val initialWheelDiameter = prefs.getFloat("wheel_diameter", 10.0f)
        val manager = VescBleManager(this, initialPolePairs, initialWheelDiameter)
        vescBleManager = manager
        setupBleObservers(manager)

        try {
            Wearable.getMessageClient(this).addListener { messageEvent ->
                when (messageEvent.path) {
                    "/vesc/sync_marker" -> {
                        Log.d(TAG, "Sync marker received from Wear OS watch!")
                        handleDropSyncMarker()
                    }
                    "/vesc/heart_rate" -> {
                        val bpm = try {
                            messageEvent.data.firstOrNull()?.toInt()?.and(0xFF) ?: 0
                        } catch (_: Exception) { 0 }
                        if (bpm > 0) {
                            currentRiderBpm = bpm
                            Log.d(TAG, "Live Rider Heart Rate: $bpm BPM")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error attaching Wearable MessageClient listener: ${e.message}")
        }

        try {
            onnxEngine = OnnxWakeWordEngine(this) {
                Log.d(TAG, "ONNX Wake Word Triggered!")

                val intent = Intent(ACTION_WAKE_WORD_TRIGGERED).apply {
                    putExtra(EXTRA_WAKE_WORD_NAME, "hey_friday")
                }
                sendBroadcast(intent)

                onWakeWordTriggered()
            }
            updateMicListeningState()
        } catch (e: Exception) {
            Log.e(TAG, "Error starting wake word engine: ${e.message}", e)
        }
    }

    private val elevenLabsManager by lazy { ElevenLabsManager(this) }

    fun speak(text: String, modelId: String = "eleven_flash_v2_5") {
        if (text.isBlank()) return
        
        val userSettings = UserSettingsManager(this)
        val usePremium = userSettings.usePremiumVoice.value
        val elevenLabsApiKey = userSettings.elevenLabsApiKey.value
        val voiceId = userSettings.elevenLabsVoiceId.value
        
        serviceScope.launch {
            if (usePremium && elevenLabsApiKey.isNotBlank()) {
                try {
                    elevenLabsManager.speakText(text, voiceId, elevenLabsApiKey, modelId)
                    Log.d(TAG, "ElevenLabs TTS Requested: $text")
                    return@launch
                } catch (e: Exception) {
                    Log.e(TAG, "ElevenLabs TTS failed, falling back to local TTS: ${e.message}")
                }
            }
            // Fallback to local TTS
            Log.d(TAG, "Local TTS Requested: $text")
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts_req_${System.currentTimeMillis()}")
        }
    }

    private suspend fun getStreetName(lat: Double, lon: Double): String = suspendCancellableCoroutine { continuation ->
        if (lat == 0.0 && lon == 0.0) {
            if (continuation.isActive) continuation.resume("Unknown Location")
            return@suspendCancellableCoroutine
        }
        try {
            val geocoder = Geocoder(this@VescService, Locale.getDefault())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                geocoder.getFromLocation(lat, lon, 1) { addresses ->
                    val street = addresses.firstOrNull()?.thoroughfare ?: "Unknown Road"
                    if (continuation.isActive) continuation.resume(street)
                }
            } else {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(lat, lon, 1)
                val street = addresses?.firstOrNull()?.thoroughfare ?: "Unknown Road"
                if (continuation.isActive) continuation.resume(street)
            }
        } catch (e: Exception) {
            if (continuation.isActive) continuation.resume("Unknown Road")
        }
    }

    private fun startProactiveAssistant() {
        rideStartTimeMs = System.currentTimeMillis()
        
        proactiveAssistantJob = serviceScope.launch {
            while(isActive) {
                // Pick a random delay between 2 and 10 minutes
                val nextDelayMinutes = Random.nextInt(2, 11) 
                val nextDelayMs = nextDelayMinutes * 60 * 1000L
                
                Log.d(TAG, "Friday is resting. Next spontaneous update in $nextDelayMinutes minutes.")
                
                // Wait for the randomized time
                delay(nextDelayMs)
                
                val data = vescBleManager?.telemetryData?.value
                val currentSpeed = data?.mph ?: 0f
                val currentBattery = data?.voltage ?: 0f
                val activeProfile = (activeProfileState.value ?: "NORMAL").uppercase()
                val currentTimeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

                val batteryPercent = if (currentBattery > 0f) {
                    val seriesCells = maxOf(1, Math.round(currentBattery / 3.7f))
                    val cellVoltage = currentBattery / seriesCells
                    ((cellVoltage - 3.2f) / (4.2f - 3.2f) * 100f).toInt().coerceIn(0, 100)
                } else {
                    0
                }

                val compassDirection = when (currentGpsBearing) {
                    in 0f..22.5f, in 337.5f..360f -> "North"
                    in 22.5f..67.5f -> "North East"
                    in 67.5f..112.5f -> "East"
                    in 112.5f..157.5f -> "South East"
                    in 157.5f..202.5f -> "South"
                    in 202.5f..247.5f -> "South West"
                    in 247.5f..292.5f -> "West"
                    in 292.5f..337.5f -> "North West"
                    else -> "Unknown"
                }

                val streetName = getStreetName(currentLat, currentLon)

                val ridingStyleInference = if (currentSpeed > 20 && (data?.adcThrottle ?: 0f) > 0.8f) {
                    "Aggressive acceleration, carving"
                } else if (currentSpeed < 10) {
                    "Leisurely cruising"
                } else {
                    "Steady riding"
                }

                val jsonPayload = JSONObject().apply {
                    put("scooter_state", activeProfile)
                    put("speed_mph", currentSpeed)
                    put("battery_percent", batteryPercent)
                    put("rider_heart_rate", currentRiderBpm)
                    put("riding_style_inference", ridingStyleInference)
                    put("environment", JSONObject().apply {
                        put("time", currentTimeStr)
                        put("weather", "Unknown") // Placeholder if no weather API exists
                        put("location", JSONObject().apply {
                            put("bearing", compassDirection)
                            put("street_name", streetName)
                        })
                    })
                }.toString(2)
                
                // Pass the data to Gemini
                val aiMessage = geminiAnalyst.generateProactiveUpdate(
                    jsonPayload
                )
                
                if (aiMessage.isNotBlank()) {
                    speak(aiMessage, modelId = "eleven_v3")
                }
            }
        }
    }

    private fun updateMicListeningState() {
        if (!isServiceRunning) {
            try {
                onnxEngine?.stopListening()
            } catch (_: Exception) {}
            return
        }

        try {
            onnxEngine?.startListening()
        } catch (e: Exception) {
            Log.e(TAG, "Error starting ONNX wake word engine: ${e.message}")
        }
    }

    // 2. Handle the Wake Word Trigger
    @SuppressLint("MissingPermission")
    fun onWakeWordTriggered(transcript: String = "") {
        try {
            val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
            Handler(Looper.getMainLooper()).postDelayed({
                toneGen.release()
            }, 200)
        } catch (e: Exception) {
            Log.e(TAG, "Error playing beep tone: ${e.message}")
        }

        serviceScope.launch(Dispatchers.IO) {
            delay(150) // Wait for the beep to finish
            
            val sampleRate = 16000
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            
            if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
                Log.e(TAG, "AudioRecord configuration not supported.")
                return@launch
            }

            val audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed.")
                return@launch
            }

            try {
                audioRecord.startRecording()
                Log.d(TAG, "Started recording 3s raw audio for Gemini...")
                
                val expectedBytes = sampleRate * 2 * 3 // 3 seconds
                val pcmData = ByteArray(expectedBytes)
                var bytesRead = 0
                
                while (bytesRead < expectedBytes) {
                    val chunk = ByteArray(min(bufferSize, expectedBytes - bytesRead))
                    val read = audioRecord.read(chunk, 0, chunk.size)
                    if (read > 0) {
                        System.arraycopy(chunk, 0, pcmData, bytesRead, read)
                        bytesRead += read
                    } else {
                        break
                    }
                }
                
                Log.d(TAG, "Finished recording. Captured $bytesRead bytes. Processing with Gemini...")

                val speedMph = vescBleManager?.telemetryData?.value?.mph ?: 0f
                val voltage = vescBleManager?.telemetryData?.value?.voltage ?: 0f

                geminiAnalyst.processVoiceCommand(
                    pcmBytes = pcmData,
                    speedMph = speedMph,
                    batteryVoltage = voltage,
                    lat = currentLat,
                    lon = currentLon
                )

            } catch (e: Exception) {
                Log.e(TAG, "Error recording or processing voice command: ${e.message}", e)
            } finally {
                try {
                    audioRecord.stop()
                    audioRecord.release()
                } catch (_: Exception) {}
            }
        }
    }

    private fun processJsonIntent(jsonIntent: String) {
        Log.d(TAG, "Executing JSON intent from Gemini: $jsonIntent")
        val prefs = getSharedPreferences("vesc_prefs", MODE_PRIVATE)
        val upper = jsonIntent.uppercase()

        serviceScope.launch {
            when {
                upper.contains("SWITCH_PROFILE") || upper.contains("PROFILE") || upper.contains("TARGET") -> {
                    when {
                        upper.contains("CRAWL") || upper.contains("ECO") || upper.contains("SLOW") || upper.contains("LOW") -> handleApplyProfile(ProfileType.CRAWL, isFromGemini = true)
                        upper.contains("LONG_RANGE") || upper.contains("RANGE") || upper.contains("EFFICIEN") -> handleApplyProfile(ProfileType.LONG_RANGE, isFromGemini = true)
                        upper.contains("MAX_POWER") || upper.contains("MAX") || upper.contains("POWER") || upper.contains("SPORT") || upper.contains("FAST") || upper.contains("BOOST") || upper.contains("HIGH") -> handleApplyProfile(ProfileType.MAX_POWER, isFromGemini = true)
                        upper.contains("NORMAL") || upper.contains("BALANCED") || upper.contains("MEDIUM") || upper.contains("DEFAULT") -> handleApplyProfile(ProfileType.NORMAL, isFromGemini = true)
                    }
                }

                upper.contains("RUN_DIAGNOSTICS") || upper.contains("DIAGNOSTIC") -> {
                    runTelemetryDiagnostic()
                }

                jsonIntent.contains("ENGINE_SOUND_OFF") -> {
                    prefs.edit { putBoolean("engine_sound_enabled", false) }
                    SoundToggleWidgetProvider.updateAllWidgets(this@VescService)
                }

                jsonIntent.contains("ENGINE_SOUND_ON") -> {
                    prefs.edit { putBoolean("engine_sound_enabled", true) }
                    SoundToggleWidgetProvider.updateAllWidgets(this@VescService)
                }

                jsonIntent.contains("CHANGE_SOUND") -> {
                    when {
                        jsonIntent.contains("V8_WARMUP") -> {
                            prefs.edit {
                                putInt("engine_sound_res_id", R.raw.snd_557214_lhermanns_enginewarmup_1_loop)
                                putBoolean("engine_sound_enabled", true)
                            }
                        }
                        jsonIntent.contains("V8") -> {
                            prefs.edit {
                                putInt("engine_sound_res_id", R.raw.snd_636066_lumamorph_eight_cylinder_engine_idling)
                                putBoolean("engine_sound_enabled", true)
                            }
                        }
                        jsonIntent.contains("HOVER") -> {
                            prefs.edit {
                                putInt("engine_sound_res_id", R.raw.snd_348857_mickboere_hover_vehicle_idle_loop)
                                putBoolean("engine_sound_enabled", true)
                            }
                        }
                        jsonIntent.contains("SCIFI") -> {
                            prefs.edit {
                                putInt("engine_sound_res_id", R.raw.snd_407540_sojan_sci_fi_engine_loop)
                                putBoolean("engine_sound_enabled", true)
                            }
                        }
                        jsonIntent.contains("SYNTH") -> {
                            prefs.edit {
                                putInt("engine_sound_res_id", R.raw.snd_482664_joao_janz_synth_car_engine_loop_1_1)
                                putBoolean("engine_sound_enabled", true)
                            }
                        }
                        jsonIntent.contains("ALIEN") -> {
                            prefs.edit {
                                putInt("engine_sound_res_id", R.raw.snd_558975_fivebrosstopmosyt_alien_engine_loop_1)
                                putBoolean("engine_sound_enabled", true)
                            }
                        }
                        jsonIntent.contains("FAN") -> {
                            prefs.edit {
                                putInt("engine_sound_res_id", R.raw.snd_618185_theplax_extractor_fan)
                                putBoolean("engine_sound_enabled", true)
                            }
                        }
                        jsonIntent.contains("DIESEL") -> {
                            prefs.edit {
                                putInt("engine_sound_res_id", R.raw.snd_679693_grauxonen_t4_19td_2000_engine_loop)
                                putBoolean("engine_sound_enabled", true)
                            }
                        }
                        jsonIntent.contains("SPACEPOD") -> {
                            prefs.edit {
                                putInt("engine_sound_res_id", R.raw.snd_773036_sealionstudios_spacepodthursters)
                                putBoolean("engine_sound_enabled", true)
                            }
                        }
                        jsonIntent.contains("F1_LOW") -> {
                            prefs.edit {
                                putInt("engine_sound_res_id", R.raw.snd_f1_low)
                                putBoolean("engine_sound_enabled", true)
                            }
                        }
                        jsonIntent.contains("F1") -> {
                            prefs.edit {
                                putInt("engine_sound_res_id", R.raw.snd_f1)
                                putBoolean("engine_sound_enabled", true)
                            }
                        }
                        jsonIntent.contains("DUALTRONX") || jsonIntent.contains("DUALTRON_X") -> {
                            prefs.edit {
                                putInt("engine_sound_res_id", R.raw.snd_dualtron_x)
                                putBoolean("engine_sound_enabled", true)
                            }
                        }
                        jsonIntent.contains("DUALTRON") || jsonIntent.contains("THUNDER") -> {
                            prefs.edit {
                                putInt("engine_sound_res_id", R.raw.snd_dualtron_thunder)
                                putBoolean("engine_sound_enabled", true)
                            }
                        }
                        jsonIntent.contains("HYPERX") || jsonIntent.contains("HYPER_X") -> {
                            prefs.edit {
                                putInt("engine_sound_res_id", R.raw.snd_hyper_x)
                                putBoolean("engine_sound_enabled", true)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun runTelemetryDiagnostic() {
        val data = _telemetryState.value
        val faultStr = if (data.faultCode > 0) getFaultString(data.faultCode) else "None"
        Log.d(TAG, "Telemetry Diagnostic: Amps: ${data.motorCurrent}, Duty: ${data.dutyCycle}, Fault: $faultStr")
        _telemetryState.value = _telemetryState.value.copy(aiMessage = "Diagnostics running...")
    }

    fun handleDropSyncMarker() {
        Log.d(TAG, "Sync marker dropped!")
        
        try {
            val toneGen = ToneGenerator(AudioManager.STREAM_ALARM, 100)
            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
            
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
                } catch (_: Exception) {}
            }, 200)

            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
                } catch (_: Exception) {}
            }, 400)

            // Release ToneGenerator and speak voice confirmation AFTER all 3 beeps finish
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    toneGen.release()
                } catch (_: Exception) {}
                speak("Sync marker dropped")
            }, 600)

        } catch (e: Exception) {
            Log.e(TAG, "Error playing sync marker beep sequence: ${e.message}")
            speak("Sync marker dropped")
        }

        val currentData = _telemetryState.value
        csvLogger?.logData(
            mph = currentData.mph,
            voltage = currentData.voltage,
            motorAmps = currentData.motorCurrent,
            batteryAmps = currentData.batteryCurrent,
            dutyCycle = currentData.dutyCycle,
            tempMosfet = currentData.tempMosfet,
            tempMotor = currentData.tempMotor,
            wattHoursUsed = currentData.wattHoursUsed,
            ampHoursCharged = currentData.ampHoursCharged,
            tachAbs = currentData.tachometerAbs,
            faultCode = currentData.faultCode,
            accelX = accelX,
            accelY = accelY,
            accelZ = accelZ,
            gyroX = gyroX,
            gyroY = gyroY,
            gyroZ = gyroZ,
            adcThrottle = currentData.adcThrottle,
            adcBrake = currentData.adcBrake,
            activeRideDurationMs = currentData.activeRideDurationMs,
            isSyncMarker = true,
            riderBpm = currentRiderBpm,
            timestampMs = System.currentTimeMillis()
        )

        gpxLogger?.addWaypoint("Sync Drop")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.d(TAG, "onStartCommand action=$action")

        when (action) {
            ACTION_STOP_TELEMETRY -> {
                finalizeRideLogsAndStop()
                return START_NOT_STICKY
            }

            ACTION_DROP_SYNC_MARKER, ACTION_TRIGGER_SYNC_MARKER -> {
                handleDropSyncMarker()
                return START_STICKY
            }

            ACTION_APPLY_PROFILE -> {
                val profileKey = intent.getStringExtra(EXTRA_PROFILE_KEY) ?: ProfileType.NORMAL.key
                val profileType = ProfileType.fromKey(profileKey) ?: ProfileType.NORMAL
                handleApplyProfile(profileType)
                return START_STICKY
            }

            ACTION_START_TELEMETRY, null -> {
                val savedPrefs = getSharedPreferences("vesc_prefs", MODE_PRIVATE)
                val deviceAddress = intent?.getStringExtra(EXTRA_DEVICE_ADDRESS)
                    ?: savedPrefs.getString("mac_address", "") ?: ""
                
                // Correctly check EXTRA_POLE_PAIRS and fallback to your saved value or 12
                val polePairs = if (intent?.hasExtra(EXTRA_POLE_PAIRS) == true) {
                    intent.getIntExtra(EXTRA_POLE_PAIRS, savedPrefs.getInt("pole_pairs", 12))
                } else {
                    savedPrefs.getInt("pole_pairs", 12)
                }

                // Correctly check EXTRA_WHEEL_DIAMETER and fallback to your saved value or 10.0f
                val wheelDiameter = if (intent?.hasExtra(EXTRA_WHEEL_DIAMETER) == true) {
                    intent.getFloatExtra(EXTRA_WHEEL_DIAMETER, savedPrefs.getFloat("wheel_diameter", 10.0f))
                } else {
                    savedPrefs.getFloat("wheel_diameter", 10.0f)
                }

                if (deviceAddress.isBlank()) {
                    Log.e(TAG, "Target BLE MAC address is missing!")
                    Toast.makeText(this, "Target VESC MAC address missing!", Toast.LENGTH_SHORT).show()
                    if (!isServiceRunning) stopSelf()
                    return START_NOT_STICKY
                }

                initAndConnect(deviceAddress, polePairs, wheelDiameter)
                startProactiveAssistant()
                return START_STICKY
            }

            else -> return START_NOT_STICKY
        }
    }

    private fun setupBleObservers(manager: VescBleManager) {
        if (observeJob != null) return

        observeJob = serviceScope.launch {
            manager.telemetryData.collect { data ->
                _telemetryState.value = data

                if (data.isConnected) {
                    wasConnected = true
                    updateNotification(data)
                    
                    if (data.voltage > 10.0f && !hasPlayedStartupGreeting && data.faultCode == 0) {
                        hasPlayedStartupGreeting = true
                        serviceScope.launch {
                            val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                            val timeOfDay = when (currentHour) {
                                in 5..11 -> "Morning"
                                in 12..16 -> "Afternoon"
                                in 17..19 -> "Late Evening"
                                else -> "Night"
                            }
                            val greeting = geminiAnalyst.generateStartupGreeting(
                                batteryVoltage = data.voltage,
                                timeOfDay = timeOfDay,
                                lat = currentLat,
                                lon = currentLon
                            )
                            speak(greeting)
                        }
                    }

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
                        riderBpm = currentRiderBpm,
                        timestampMs = System.currentTimeMillis()
                    )

                    BaseTelemetryWidget.sendTelemetryBroadcast(this@VescService, data)
                    sendTelemetryToWear(data)
                } else if (wasConnected) {
                    Log.d(TAG, "Scooter disconnected after active connection. Finalizing logs...")
                    hasPlayedStartupGreeting = false
                    sendTelemetryToWear(TelemetryData(isConnected = false, statusText = "Disconnected"))
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
    }

    private fun sendTelemetryToWear(data: TelemetryData) {
        try {
            val batteryPercent = if (data.voltage > 0f) {
                val seriesCells = maxOf(1, Math.round(data.voltage / 3.7f))
                val cellVoltage = data.voltage / seriesCells
                ((cellVoltage - 3.2f) / (4.2f - 3.2f) * 100f).toInt().coerceIn(0, 100)
            } else {
                0
            }
            val activeProfile = (activeProfileState.value ?: "NORMAL").uppercase()

            val jsonPayload = JSONObject().apply {
                put("speed", data.mph)
                put("battery", batteryPercent)
                put("profile", activeProfile)
            }.toString().toByteArray(Charsets.UTF_8)

            Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
                for (node in nodes) {
                    Wearable.getMessageClient(this)
                        .sendMessage(node.id, "/vesc/telemetry", jsonPayload)
                }
            }

            val putDataMapReq = PutDataMapRequest.create("/telemetry")
            val dataMap = putDataMapReq.dataMap
            dataMap.putFloat("mph", data.mph)
            dataMap.putFloat("voltage", data.voltage)
            dataMap.putLong("activeRideDurationMs", data.activeRideDurationMs)
            dataMap.putFloat("estimatedRemainingMiles", data.estimatedRemainingMiles)
            dataMap.putBoolean("isConnected", data.isConnected)
            dataMap.putLong("timestamp", System.currentTimeMillis())

            val putDataReq = putDataMapReq.asPutDataRequest().setUrgent()
            Wearable.getDataClient(this).putDataItem(putDataReq)
        } catch (e: Exception) {
            Log.e(TAG, "Error broadcasting telemetry to Wear OS: ${e.message}")
        }
    }

    private fun initAndConnect(deviceAddress: String, polePairs: Int, wheelDiameter: Float) {
        isServiceRunning = true
        wasConnected = false
        updateMicListeningState()

        val prefs = getSharedPreferences("vesc_prefs", MODE_PRIVATE)
        val logGpx = prefs.getBoolean("log_gpx_enabled", true)
        val logCsv = prefs.getBoolean("log_csv_enabled", true)

        if (logGpx) {
            gpxLogger = GpxLogger(this)
        }
        // Always start GPS updates for AI location-aware telemetry context
        startGpsUpdates()
        
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
            setupBleObservers(manager)
        } else {
            vescBleManager?.updateConfig(polePairs, wheelDiameter)
        }
        vescBleManager?.connect(deviceAddress)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        val prefs = sharedPreferences ?: getSharedPreferences("vesc_prefs", MODE_PRIVATE)
        if (key == "engine_sound_enabled" && isServiceRunning) {
            val enabled = prefs.getBoolean("engine_sound_enabled", false)
            if (!enabled) {
                engineSoundManager?.stopEngineSound()
                engineSoundManager = null
                currentEngineSoundResId = 0
            }
        } else if (key == "continuous_mic_enabled") {
            updateMicListeningState()
        } else if (key == "pole_pairs" || key == "wheel_diameter") {
            val polePairs = prefs.getInt("pole_pairs", 7)
            val wheelDiameter = prefs.getFloat("wheel_diameter", 10.0f)
            vescBleManager?.updateConfig(polePairs, wheelDiameter)
            Log.d(TAG, "Dynamic config update: polePairs=$polePairs, wheelDiameter=$wheelDiameter")
        }
    }

    private fun handleApplyProfile(profileType: ProfileType, isFromGemini: Boolean = false) {
        val prefs = getSharedPreferences("vesc_prefs", MODE_PRIVATE)
        val deviceAddress = prefs.getString("mac_address", "") ?: ""
        val polePairs = prefs.getInt("pole_pairs", 7)
        val wheelDiameter = prefs.getFloat("wheel_diameter", 10.0f)

        prefs.edit { putString("active_profile", profileType.key) }
        _activeProfileState.value = profileType.key

        BaseProfileWidgetProvider.updateAllWidgets(this)

        val defaultAnnouncement = when (profileType) {
            ProfileType.CRAWL -> "Crawl profile active"
            ProfileType.NORMAL -> "Normal profile active"
            ProfileType.LONG_RANGE -> "Long Range profile active"
            ProfileType.MAX_POWER -> "Max profile active"
        }
        
        Log.d(TAG, defaultAnnouncement)
        
        // ONLY speak if it wasn't triggered by Gemini
        if (!isFromGemini) {
            val speedMph = vescBleManager?.telemetryData?.value?.mph ?: 0f
            val voltage = vescBleManager?.telemetryData?.value?.voltage ?: 0f
            
            serviceScope.launch {
                val aiAnnouncement = geminiAnalyst.generateProfileSwitchCommentary(
                    targetProfile = profileType.name,
                    speedMph = speedMph,
                    batteryVoltage = voltage
                )
                Log.d(TAG, aiAnnouncement)
                speak(aiAnnouncement)
            }
        }

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
                        currentGpsSpeed = speed
                        currentGpsBearing = bearing
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
        hasPlayedStartupGreeting = false

        proactiveAssistantJob?.cancel()
        onnxEngine?.release()
        onnxEngine = null

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
        
        // Disconnection handled internally by OkHttp WebSockets

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
        updateMicListeningState()

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
        Log.d(TAG, "onDestroy called. Stopping engine sound, AI tasks, and closing loggers...")
        getSharedPreferences("vesc_prefs", MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(this)
        
        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
        } catch (_: Exception) {}

        elevenLabsManager.stop()
        hasPlayedStartupGreeting = false

        onnxEngine?.release()
        onnxEngine = null

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
        proactiveAssistantJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }
}