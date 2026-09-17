package com.example.vesccontrolcentre.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import com.example.vesccontrolcentre.presentation.theme.VescControlCentreTheme
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MainActivity : ComponentActivity(), DataClient.OnDataChangedListener, SensorEventListener {

    private val _mph = MutableStateFlow(0f)
    private val _voltage = MutableStateFlow(0f)
    private val _activeRideDurationMs = MutableStateFlow(0L)
    private val _estimatedRemainingMiles = MutableStateFlow(0f)
    private val _isConnected = MutableStateFlow(false)
    private val _showSyncVisual = MutableStateFlow(false)
    private val _riderBpm = MutableStateFlow(0)
    private val _hasBodySensorPermission = MutableStateFlow(false)

    private var sensorManager: SensorManager? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        _hasBodySensorPermission.value = isGranted
        if (isGranted) {
            setupHeartRateSensor()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Wearable.getDataClient(this).addListener(this)

        checkBodySensorPermissionSilent()

        setContent {
            val mph by _mph.collectAsState()
            val voltage by _voltage.collectAsState()
            val activeRideDurationMs by _activeRideDurationMs.collectAsState()
            val estimatedRemainingMiles by _estimatedRemainingMiles.collectAsState()
            val isConnected by _isConnected.collectAsState()
            val riderBpm by _riderBpm.collectAsState()
            val hasBodySensorPermission by _hasBodySensorPermission.collectAsState()

            VescControlCentreTheme {
                AppScaffold {
                    ScreenScaffold(
                        timeText = { TimeText() }
                    ) { _ ->
                        WearTelemetryScreen(
                            mph = mph,
                            voltage = voltage,
                            activeRideDurationMs = activeRideDurationMs,
                            estimatedRemainingMiles = estimatedRemainingMiles,
                            isConnected = isConnected,
                            riderBpm = riderBpm,
                            hasBodySensorPermission = hasBodySensorPermission,
                            onRequestPermission = {
                                permissionLauncher.launch(Manifest.permission.BODY_SENSORS)
                            },
                            showSyncVisualFlow = _showSyncVisual
                        )
                    }
                }
            }
        }
    }

    private fun checkBodySensorPermissionSilent() {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) == PackageManager.PERMISSION_GRANTED
        _hasBodySensorPermission.value = granted
        if (granted) {
            setupHeartRateSensor()
        }
    }

    private fun setupHeartRateSensor() {
        if (sensorManager == null) {
            sensorManager = getSystemService(SENSOR_SERVICE) as? SensorManager
            val hrSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_HEART_RATE)
            hrSensor?.let {
                sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
                Log.d("WearApp", "Heart Rate sensor registered successfully.")
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_HEART_RATE) {
            val bpm = event.values.firstOrNull()?.toInt() ?: 0
            if (bpm > 0) {
                _riderBpm.value = bpm
                sendHeartRateToPhone(bpm)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun sendHeartRateToPhone(bpm: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val nodeClient = Wearable.getNodeClient(this@MainActivity)
                val nodes = Tasks.await(nodeClient.connectedNodes)
                val messageClient = Wearable.getMessageClient(this@MainActivity)
                val payload = byteArrayOf(bpm.toByte())

                for (node in nodes) {
                    Tasks.await(messageClient.sendMessage(node.id, "/vesc/heart_rate", payload))
                }
            } catch (e: Exception) {
                Log.e("WearApp", "Failed to send heart rate to phone", e)
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return if (event?.repeatCount == 0 && keyCode == KeyEvent.KEYCODE_STEM_1) {
            triggerSyncMarker()
            true
        } else {
            super.onKeyDown(keyCode, event)
        }
    }

    private fun triggerSyncMarker() {
        lifecycleScope.launch {
            _showSyncVisual.value = true
            sendMarkerToPhone()
            delay(1000)
            _showSyncVisual.value = false
        }
    }

    private suspend fun sendMarkerToPhone() {
        withContext(Dispatchers.IO) {
            try {
                val nodeClient = Wearable.getNodeClient(this@MainActivity)
                val nodes = Tasks.await(nodeClient.connectedNodes)
                val messageClient = Wearable.getMessageClient(this@MainActivity)

                for (node in nodes) {
                    Tasks.await(messageClient.sendMessage(node.id, "/vesc/sync_marker", ByteArray(0)))
                }
            } catch (e: Exception) {
                Log.e("WearApp", "Failed to send sync marker to phone", e)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Wearable.getDataClient(this).addListener(this)
        checkBodySensorPermissionSilent()
    }

    override fun onPause() {
        super.onPause()
        Wearable.getDataClient(this).removeListener(this)
        try {
            sensorManager?.unregisterListener(this)
            sensorManager = null
        } catch (_: Exception) {}
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path == "/telemetry") {
                val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                _mph.value = dataMap.getFloat("mph", 0f)
                _voltage.value = dataMap.getFloat("voltage", 0f)
                _activeRideDurationMs.value = dataMap.getLong("activeRideDurationMs", 0L)
                _estimatedRemainingMiles.value = dataMap.getFloat("estimatedRemainingMiles", 0f)
                _isConnected.value = true
            }
        }
    }
}

@Composable
fun WearTelemetryScreen(
    mph: Float,
    voltage: Float,
    activeRideDurationMs: Long,
    estimatedRemainingMiles: Float,
    isConnected: Boolean,
    riderBpm: Int = 0,
    hasBodySensorPermission: Boolean = true,
    onRequestPermission: () -> Unit = {},
    showSyncVisualFlow: StateFlow<Boolean> = MutableStateFlow(false)
) {
    val showVisual by showSyncVisualFlow.collectAsState()
    val sec = (activeRideDurationMs / 1000) % 60
    val min = (activeRideDurationMs / (1000 * 60)) % 60
    val hrs = (activeRideDurationMs / (1000 * 60 * 60))
    val timeStr = if (hrs > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hrs, min, sec)
    } else {
        String.format(Locale.US, "%02d:%02d", min, sec)
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
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            // Status Header Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            if (isConnected) Color(0xFF00E676) else Color(0xFFFF5252),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (isConnected) "CONNECTED" else "OFFLINE",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 8.sp
                    )
                }

                if (riderBpm > 0) {
                    Box(
                        modifier = Modifier
                            .background(
                                Color(0xFFFF1744),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "❤️ $riderBpm",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 8.sp
                        )
                    }
                } else if (!hasBodySensorPermission) {
                    Box(
                        modifier = Modifier
                            .background(
                                Color(0xFF37474F),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { onRequestPermission() }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "❤️ Enable",
                            color = Color(0xFFFFD54F),
                            fontWeight = FontWeight.Bold,
                            fontSize = 8.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Main Live Speed Readout
            Text(
                text = String.format(Locale.US, "%.1f", mph),
                fontSize = 34.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF00E5FF)
            )
            Text(
                text = "MPH",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF00E5FF)
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Secondary Metrics Row: Voltage, Active Time, and Range
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "VOLTS",
                        fontSize = 7.sp,
                        color = Color.Gray,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = String.format(Locale.US, "%.1fV", voltage),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00E676)
                    )
                }

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(16.dp)
                        .background(Color.DarkGray)
                )

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "EST. RANGE",
                        fontSize = 7.sp,
                        color = Color.Gray,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (estimatedRemainingMiles > 0f) String.format(Locale.US, "%.1f mi", estimatedRemainingMiles) else "-- mi",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFD54F)
                    )
                }

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(16.dp)
                        .background(Color.DarkGray)
                )

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "TIME",
                        fontSize = 7.sp,
                        color = Color.Gray,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = timeStr,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        // Sync Marker Overlay
        AnimatedVisibility(
            visible = showVisual,
            enter = fadeIn(animationSpec = tween(150)),
            exit = fadeOut(animationSpec = tween(300))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF00E676).copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "✓",
                    color = Color(0xFF00E676),
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@WearPreviewDevices
@Composable
fun WearTelemetryScreenPreview() {
    MaterialTheme {
        WearTelemetryScreen(
            mph = 18.5f,
            voltage = 52.4f,
            activeRideDurationMs = 754000L,
            estimatedRemainingMiles = 14.2f,
            isConnected = true,
            riderBpm = 142
        )
    }
}
