package com.example.vesccontrolcentre.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.material3.Text
import com.example.vesccontrolcentre.presentation.theme.VescControlCentreTheme
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Locale

class MainActivity : ComponentActivity(), SensorEventListener, MessageClient.OnMessageReceivedListener {

    companion object {
        private const val TAG = "WearMainActivity"
    }

    private var sensorManager: SensorManager? = null
    private var heartRateSensor: Sensor? = null
    private var cachedNodeId: String? = null
    private var sensorRegistered = false

    // Telemetry / UI state (Compose reads these directly, no ViewModel needed for this simple screen)
    private var speedMph by mutableFloatStateOf(0.0f)
    private var batteryPercent by mutableIntStateOf(0)
    private var activeProfile by mutableStateOf("NORMAL")
    private var currentHeartRate by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sensorManager = getSystemService(SENSOR_SERVICE) as? SensorManager
        heartRateSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_HEART_RATE)

        setContent {
            VescControlCentreTheme {
                WearDashboardScreen(
                    speedMph = speedMph,
                    batteryPercent = batteryPercent,
                    activeProfile = activeProfile,
                    heartRateBpm = currentHeartRate,
                    onPermissionGranted = { registerHeartRateSensor() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            Wearable.getMessageClient(this).addListener(this)
        } catch (e: Exception) {
            Log.e(TAG, "Error adding MessageClient listener", e)
        }
        if (hasBodySensorPermission()) {
            registerHeartRateSensor()
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            Wearable.getMessageClient(this).removeListener(this)
        } catch (e: Exception) {
            Log.e(TAG, "Error removing MessageClient listener", e)
        }
        sensorManager?.unregisterListener(this)
        sensorRegistered = false
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager?.unregisterListener(this)
        sensorRegistered = false
    }

    private fun hasBodySensorPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.BODY_SENSORS
        ) == PackageManager.PERMISSION_GRANTED

    fun registerHeartRateSensor() {
        if (sensorRegistered) return
        if (!hasBodySensorPermission()) {
            Log.w(TAG, "Cannot register heart rate sensor, BODY_SENSORS not granted")
            return
        }
        val sensor = heartRateSensor
        if (sensor == null) {
            Log.e(TAG, "Heart rate sensor not available on this device")
            return
        }
        sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        sensorRegistered = true
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path == "/vesc/telemetry") {
            try {
                val jsonString = String(messageEvent.data, Charsets.UTF_8)
                val json = JSONObject(jsonString)
                val speed = json.optDouble("speed", 0.0).toFloat()
                val battery = json.optInt("battery", 0)
                val profile = json.optString("profile", "NORMAL")
                lifecycleScope.launch(Dispatchers.Main) {
                    speedMph = speed
                    batteryPercent = battery
                    activeProfile = profile
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing telemetry message", e)
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_HEART_RATE && event.values.isNotEmpty()) {
            val bpm = event.values[0].toInt()
            if (bpm > 0) {
                currentHeartRate = bpm
                broadcastHeartRate(bpm)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }

    private suspend fun getPhoneNodeId(): String? = withContext(Dispatchers.IO) {
        try {
            val nodes = Wearable.getNodeClient(this@MainActivity).connectedNodes.await()
            nodes.firstOrNull()?.id
        } catch (e: Exception) {
            Log.e(TAG, "Failed to discover connected nodes: ${e.message}", e)
            null
        }
    }

    private fun broadcastHeartRate(bpm: Int) {
        val payload = byteArrayOf(bpm.coerceIn(0, 255).toByte())
        lifecycleScope.launch(Dispatchers.IO) {
            val nodeId = cachedNodeId ?: getPhoneNodeId()?.also { cachedNodeId = it }
            if (nodeId != null) {
                try {
                    Wearable.getMessageClient(this@MainActivity)
                        .sendMessage(nodeId, "/vesc/heart_rate", payload)
                        .await()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send heart rate to node $nodeId", e)
                    cachedNodeId = null
                }
            } else {
                Log.w(TAG, "No connected phone node found, cannot send heart rate")
            }
        }
    }
}

@Composable
fun WearDashboardScreen(
    speedMph: Float,
    batteryPercent: Int,
    activeProfile: String,
    heartRateBpm: Int,
    onPermissionGranted: () -> Unit
) {
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onPermissionGranted()
        } else {
            Log.w("WearMainActivity", "BODY_SENSORS permission denied")
        }
    }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BODY_SENSORS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.BODY_SENSORS)
        } else {
            onPermissionGranted()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(12.dp)
        ) {
            // 1. Current Speed (large centered text)
            Text(
                text = String.format(Locale.US, "%.1f MPH", speedMph),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 2. Battery Percentage & Active Profile tag
            Text(
                text = "$batteryPercent% | ${activeProfile.uppercase()}",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF00E676)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 3. Live Heart Rate at the bottom
            val hrText = if (heartRateBpm > 0) "\u2764\uFE0F $heartRateBpm BPM" else "\u2764\uFE0F --"
            Text(
                text = hrText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFFF5252)
            )
        }
    }
}