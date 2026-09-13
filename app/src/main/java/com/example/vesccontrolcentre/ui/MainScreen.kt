package com.example.vesccontrolcentre.ui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import com.example.vesccontrolcentre.R
import com.example.vesccontrolcentre.model.ProfileConfig
import com.example.vesccontrolcentre.model.ProfileType
import com.example.vesccontrolcentre.model.TelemetryData
import com.example.vesccontrolcentre.service.VescService
import com.example.vesccontrolcentre.widget.BaseTelemetryWidget
import java.util.Locale

data class BleDeviceItem(
    val name: String,
    val address: String,
    val rssi: Int
)

data class MetricToggleItem(
    val prefKey: String,
    val title: String,
    val variableName: String,
    val description: String,
    val defaultValue: Boolean = true
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onRequestPermissions: () -> Unit,
    hasPermissions: Boolean
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("vesc_prefs", Context.MODE_PRIVATE) }

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("Telemetry", "Profiles", "Settings & Widgets")

    var macAddress by remember { mutableStateOf(prefs.getString("mac_address", "") ?: "") }
    var polePairsText by remember { mutableStateOf(prefs.getInt("pole_pairs", 7).toString()) }
    var wheelDiameterText by remember { mutableStateOf(prefs.getFloat("wheel_diameter", 10.0f).toString()) }

    var isScanning by remember { mutableStateOf(false) }
    val discoveredDevices = remember { mutableStateListOf<BleDeviceItem>() }

    val telemetryData by VescService.telemetryState.collectAsState()
    val activeProfileKey by VescService.activeProfileState.collectAsState()

    fun saveHardwareConfig() {
        val polePairs = polePairsText.toIntOrNull() ?: 7
        val wheelDiameter = wheelDiameterText.toFloatOrNull() ?: 10.0f
        prefs.edit {
            putString("mac_address", macAddress)
            putInt("pole_pairs", polePairs)
            putFloat("wheel_diameter", wheelDiameter)
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (!hasPermissions) {
            onRequestPermissions()
            return
        }
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val scanner = bluetoothManager?.adapter?.bluetoothLeScanner
        if (scanner == null) {
            Toast.makeText(context, "Bluetooth disabled or unavailable", Toast.LENGTH_SHORT).show()
            return
        }

        discoveredDevices.clear()
        isScanning = true

        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device ?: return
                val address = device.address ?: return
                val name = device.name ?: "Unknown VESC / BLE"
                val rssi = result.rssi

                val index = discoveredDevices.indexOfFirst { it.address == address }
                if (index >= 0) {
                    discoveredDevices[index] = BleDeviceItem(name, address, rssi)
                } else {
                    discoveredDevices.add(BleDeviceItem(name, address, rssi))
                }
            }

            override fun onScanFailed(errorCode: Int) {
                isScanning = false
                Toast.makeText(context, "Scan failed: $errorCode", Toast.LENGTH_SHORT).show()
            }
        }

        try {
            scanner.startScan(scanCallback)
        } catch (e: Exception) {
            isScanning = false
            Toast.makeText(context, "Error starting scan: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_telemetry),
                                contentDescription = "Logo",
                                tint = Color(0xFF00E5FF)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("VESC Control Centre", fontWeight = FontWeight.Bold)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )

                PrimaryTabRow(selectedTabIndex = selectedTabIndex) {
                    tabTitles.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = { Text(title, fontWeight = FontWeight.Bold) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (!hasPermissions) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF331414)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Permissions Required", fontWeight = FontWeight.Bold, color = Color(0xFFFF5252))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Bluetooth, Location and Notification permissions are required to scan BLE devices, log GPX coordinates, and run background telemetry.",
                            fontSize = 13.sp,
                            color = Color.LightGray
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onRequestPermissions,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))
                        ) {
                            Text("Grant Permissions")
                        }
                    }
                }
            }

            when (selectedTabIndex) {
                0 -> TelemetryTab(
                    telemetryData = telemetryData,
                    activeProfileKey = activeProfileKey,
                    isServiceRunning = VescService.isServiceRunning,
                    onStartService = {
                        if (macAddress.isBlank()) {
                            Toast.makeText(context, "Please set target BLE MAC Address first!", Toast.LENGTH_SHORT).show()
                            selectedTabIndex = 2
                            return@TelemetryTab
                        }
                        saveHardwareConfig()
                        val polePairs = polePairsText.toIntOrNull() ?: 7
                        val wheelDiameter = wheelDiameterText.toFloatOrNull() ?: 10.0f
                        VescService.startTelemetry(context, macAddress, polePairs, wheelDiameter)
                    },
                    onStopService = {
                        VescService.stopTelemetry(context)
                    }
                )

                1 -> ProfilesTab(
                    activeProfileKey = activeProfileKey,
                    onApplyProfile = { profileType ->
                        if (macAddress.isBlank()) {
                            Toast.makeText(context, "Please set target BLE MAC Address first!", Toast.LENGTH_SHORT).show()
                            selectedTabIndex = 2
                            return@ProfilesTab
                        }
                        saveHardwareConfig()
                        VescService.applyProfile(context, profileType)
                    }
                )

                2 -> SettingsAndScanTab(
                    macAddress = macAddress,
                    onMacAddressChange = {
                        macAddress = it
                        saveHardwareConfig()
                    },
                    polePairsText = polePairsText,
                    onPolePairsChange = {
                        polePairsText = it
                        saveHardwareConfig()
                    },
                    wheelDiameterText = wheelDiameterText,
                    onWheelDiameterChange = {
                        wheelDiameterText = it
                        saveHardwareConfig()
                    },
                    isScanning = isScanning,
                    discoveredDevices = discoveredDevices,
                    onStartScan = { startScan() },
                    onSelectDevice = { device ->
                        macAddress = device.address
                        saveHardwareConfig()
                        Toast.makeText(context, "Selected VESC: ${device.name} (${device.address})", Toast.LENGTH_SHORT).show()
                    },
                    telemetryData = telemetryData
                )
            }
        }
    }
}

@Composable
fun TelemetryTab(
    telemetryData: TelemetryData,
    activeProfileKey: String?,
    isServiceRunning: Boolean,
    onStartService: () -> Unit,
    onStopService: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("vesc_prefs", Context.MODE_PRIVATE) }

    var speedUnit by remember { mutableStateOf(prefs.getString("speed_unit", "MPH") ?: "MPH") }
    var speedometerMode by remember { mutableStateOf(prefs.getString("speedometer_mode", "DIGITAL") ?: "DIGITAL") }

    var logGpxEnabled by remember { mutableStateOf(prefs.getBoolean("log_gpx_enabled", true)) }
    var logCsvEnabled by remember { mutableStateOf(prefs.getBoolean("log_csv_enabled", true)) }

    val isKmh = speedUnit.equals("KMH", ignoreCase = true)
    val displaySpeedVal = if (isKmh) telemetryData.mph * 1.60934f else telemetryData.mph
    val speed3DigitStr = String.format(Locale.US, "%05.1f", displaySpeedVal)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF121824)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("LIVE TELEMETRY DASHBOARD", fontWeight = FontWeight.Bold, color = Color(0xFF8A99AD), fontSize = 12.sp)
                        Box(
                            modifier = Modifier
                                .background(
                                    if (telemetryData.isConnected) Color(0xFF00E676) else Color(0xFFFF5252),
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (telemetryData.isConnected) "CONNECTED" else "OFFLINE",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Speedometer Display Mode & Unit Toggle Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Display Mode: Digital vs Dial
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = speedometerMode == "DIGITAL",
                                onClick = {
                                    speedometerMode = "DIGITAL"
                                    prefs.edit { putString("speedometer_mode", "DIGITAL") }
                                },
                                label = { Text("Digital", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFF00E5FF), selectedLabelColor = Color.Black)
                            )

                            FilterChip(
                                selected = speedometerMode == "DIAL",
                                onClick = {
                                    speedometerMode = "DIAL"
                                    prefs.edit { putString("speedometer_mode", "DIAL") }
                                },
                                label = { Text("Dial Gauge", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFF00E5FF), selectedLabelColor = Color.Black)
                            )
                        }

                        // Speed Unit: MPH vs KM/H
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = !isKmh,
                                onClick = {
                                    speedUnit = "MPH"
                                    prefs.edit { putString("speed_unit", "MPH") }
                                    BaseTelemetryWidget.sendTelemetryBroadcast(context, telemetryData)
                                },
                                label = { Text("MPH", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFF00E676), selectedLabelColor = Color.Black)
                            )

                            FilterChip(
                                selected = isKmh,
                                onClick = {
                                    speedUnit = "KMH"
                                    prefs.edit { putString("speed_unit", "KMH") }
                                    BaseTelemetryWidget.sendTelemetryBroadcast(context, telemetryData)
                                },
                                label = { Text("KM/H", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFF00E676), selectedLabelColor = Color.Black)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (speedometerMode == "DIAL") {
                        SpeedometerDial(
                            speedMph = telemetryData.mph,
                            unit = if (isKmh) "KM/H" else "MPH",
                            maxSpeed = 60f,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("SPEED", fontSize = 11.sp, color = Color.Gray)
                                Text(
                                    text = speed3DigitStr,
                                    fontSize = 38.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF00E5FF)
                                )
                                Text(if (isKmh) "KM/H" else "MPH", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E5FF))
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("VOLTAGE", fontSize = 11.sp, color = Color.Gray)
                                Text(
                                    text = String.format(Locale.US, "%.1f", telemetryData.voltage),
                                    fontSize = 38.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF00E676)
                                )
                                Text("VOLTS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E676))
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("DUTY CYCLE", fontSize = 11.sp, color = Color.Gray)
                                Text(
                                    text = String.format(Locale.US, "%.1f", telemetryData.dutyCycle),
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE040FB)
                                )
                                Text("%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE040FB))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("MOTOR CURRENT", fontSize = 10.sp, color = Color.Gray)
                            Text(
                                text = String.format(Locale.US, "%.1f A", telemetryData.motorCurrent),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFA726)
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("BATTERY CURRENT", fontSize = 10.sp, color = Color.Gray)
                            Text(
                                text = String.format(Locale.US, "%.1f A", telemetryData.batteryCurrent),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFD54F)
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("MOSFET TEMP", fontSize = 10.sp, color = Color.Gray)
                            Text(
                                text = String.format(Locale.US, "%.1f °C", telemetryData.tempMosfet),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFF5252)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = onStartService,
                            enabled = !isServiceRunning,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Start Service", color = Color.Black, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = onStopService,
                            enabled = isServiceRunning,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Stop Service", color = Color(0xFFFF5252))
                        }
                    }
                }
            }
        }

        // Dual Ride Logging Options Card
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Ride Logging Options", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Automatically record ride logs to Documents. Files are finalized and saved when scooter disconnects.",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val next = !logGpxEnabled
                                logGpxEnabled = next
                                prefs.edit { putBoolean("log_gpx_enabled", next) }
                            }
                            .padding(vertical = 4.dp)
                    ) {
                        Checkbox(
                            checked = logGpxEnabled,
                            onCheckedChange = { checked ->
                                logGpxEnabled = checked
                                prefs.edit { putBoolean("log_gpx_enabled", checked) }
                            },
                            colors = CheckboxDefaults.colors(checkedColor = Color(0xFF00E5FF))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Log Ride to Strava (GPX)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val next = !logCsvEnabled
                                logCsvEnabled = next
                                prefs.edit { putBoolean("log_csv_enabled", next) }
                            }
                            .padding(vertical = 4.dp)
                    ) {
                        Checkbox(
                            checked = logCsvEnabled,
                            onCheckedChange = { checked ->
                                logCsvEnabled = checked
                                prefs.edit { putBoolean("log_csv_enabled", checked) }
                            },
                            colors = CheckboxDefaults.colors(checkedColor = Color(0xFF00E5FF))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Log Raw Telemetry (CSV)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Active VESC Profile", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = activeProfileKey ?: "None / Default",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp,
                        color = Color(0xFF00E5FF)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "You can switch between Crawl, Normal, Long Range, and Max Power anytime from the 'Profiles' tab or from Home Screen Widgets.",
                        fontSize = 13.sp,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

@Composable
fun ProfilesTab(
    activeProfileKey: String?,
    onApplyProfile: (ProfileType) -> Unit
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(ProfileType.entries) { type ->
            val initialConfig = remember(type) { ProfileConfig.load(context, type) }

            var currentMaxText by remember(type) { mutableStateOf(initialConfig.lCurrentMax.toString()) }
            var inCurrentMaxText by remember(type) { mutableStateOf(initialConfig.lInCurrentMax.toString()) }
            var wattMaxText by remember(type) { mutableStateOf(initialConfig.lWattMax.toString()) }

            val isActive = (activeProfileKey == type.key)

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isActive) Color(0xFF1E3A5F) else MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(type.defaultTitle, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text(type.defaultSubtitle, fontSize = 12.sp, color = Color.Gray)
                        }

                        if (isActive) {
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFF00E676), shape = RoundedCornerShape(4.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("⚡ ACTIVE", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = currentMaxText,
                            onValueChange = { currentMaxText = it },
                            label = { Text("Motor Current (A)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = inCurrentMaxText,
                            onValueChange = { inCurrentMaxText = it },
                            label = { Text("Batt Current (A)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = wattMaxText,
                            onValueChange = { wattMaxText = it },
                            label = { Text("Max Power (W)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = {
                                val cMax = currentMaxText.toFloatOrNull() ?: type.defaultCurrentMax
                                val inMax = inCurrentMaxText.toFloatOrNull() ?: type.defaultInCurrentMax
                                val wMax = wattMaxText.toFloatOrNull() ?: type.defaultWattMax

                                val config = ProfileConfig(type, cMax, inMax, wMax)
                                config.save(context)
                                Toast.makeText(context, "Saved ${type.defaultTitle} profile settings!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Save Settings")
                        }

                        Button(
                            onClick = {
                                val cMax = currentMaxText.toFloatOrNull() ?: type.defaultCurrentMax
                                val inMax = inCurrentMaxText.toFloatOrNull() ?: type.defaultInCurrentMax
                                val wMax = wattMaxText.toFloatOrNull() ?: type.defaultWattMax

                                val config = ProfileConfig(type, cMax, inMax, wMax)
                                config.save(context)

                                onApplyProfile(type)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Apply Profile Now", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsAndScanTab(
    macAddress: String,
    onMacAddressChange: (String) -> Unit,
    polePairsText: String,
    onPolePairsChange: (String) -> Unit,
    wheelDiameterText: String,
    onWheelDiameterChange: (String) -> Unit,
    isScanning: Boolean,
    discoveredDevices: List<BleDeviceItem>,
    onStartScan: () -> Unit,
    onSelectDevice: (BleDeviceItem) -> Unit,
    telemetryData: TelemetryData
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("vesc_prefs", Context.MODE_PRIVATE) }

    var enableDualVesc by remember { mutableStateOf(prefs.getBoolean("enable_dual_vesc", true)) }
    var secondaryCanIdText by remember { mutableStateOf(prefs.getInt("secondary_can_id", 53).toString()) }

    var logGpxEnabled by remember { mutableStateOf(prefs.getBoolean("log_gpx_enabled", true)) }
    var logCsvEnabled by remember { mutableStateOf(prefs.getBoolean("log_csv_enabled", true)) }

    val metricsList = remember {
        listOf(
            MetricToggleItem("metric_show_mph", "Speed", "mph / kmh", "Live speed readout", true),
            MetricToggleItem("metric_show_voltage", "Battery Voltage (V)", "v_in", "Total pack battery voltage", true),
            MetricToggleItem("metric_show_motor_current", "Motor Current (A)", "current_motor", "Actual motor torque output", true),
            MetricToggleItem("metric_show_battery_current", "Battery Current (A)", "current_in", "Total amp draw on battery pack", true),
            MetricToggleItem("metric_show_duty_cycle", "Duty Cycle (%)", "duty_cycle", "Percentage of max available power used", true),
            MetricToggleItem("metric_show_temp_mosfet", "Controller Temp (°C)", "temp_mos_max", "MOSFET heat levels to prevent thermal damage", true),
            MetricToggleItem("metric_show_temp_motor", "Motor Temp (°C)", "temp_motor", "Motor stator temperature", true),
            MetricToggleItem("metric_show_erpm", "ERPM / RPM", "erpm", "Electrical RPM of the motor", false),
            MetricToggleItem("metric_show_watt_hours", "Energy Consumed (Wh)", "watt_hours_used", "Accurate fuel gauge ignoring voltage sag", false),
            MetricToggleItem("metric_show_amp_hours_charged", "Regen Braking (Ah)", "amp_hours_charged", "Recaptured energy during braking", false),
            MetricToggleItem("metric_show_fault_codes", "Fault Codes & Warnings", "fault_code", "Live diagnostic error codes", true)
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hardware Config
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("VESC Hardware Configuration", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = macAddress,
                        onValueChange = onMacAddressChange,
                        label = { Text("Target VESC BLE MAC Address") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("e.g. 12:34:56:78:9A:BC") }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = polePairsText,
                            onValueChange = onPolePairsChange,
                            label = { Text("Motor Pole Pairs") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = wheelDiameterText,
                            onValueChange = onWheelDiameterChange,
                            label = { Text("Wheel Dia (inches)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                }
            }
        }

        // Ride Logging Options Card
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Ride Logging Options", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Automatically save ride logs to Documents. Files close with XML headers when scooter turns off.",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val next = !logGpxEnabled
                                logGpxEnabled = next
                                prefs.edit { putBoolean("log_gpx_enabled", next) }
                            }
                            .padding(vertical = 4.dp)
                    ) {
                        Checkbox(
                            checked = logGpxEnabled,
                            onCheckedChange = { checked ->
                                logGpxEnabled = checked
                                prefs.edit { putBoolean("log_gpx_enabled", checked) }
                            },
                            colors = CheckboxDefaults.colors(checkedColor = Color(0xFF00E5FF))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Log Ride to Strava (GPX)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val next = !logCsvEnabled
                                logCsvEnabled = next
                                prefs.edit { putBoolean("log_csv_enabled", next) }
                            }
                            .padding(vertical = 4.dp)
                    ) {
                        Checkbox(
                            checked = logCsvEnabled,
                            onCheckedChange = { checked ->
                                logCsvEnabled = checked
                                prefs.edit { putBoolean("log_csv_enabled", checked) }
                            },
                            colors = CheckboxDefaults.colors(checkedColor = Color(0xFF00E5FF))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Log Raw Telemetry (CSV)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                }
            }
        }

        // Dual VESC / CAN Bus Setup Card
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Dual VESC / CAN Bus Setup", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "For dual motor ESC setups connected via CAN bus, profile changes are forwarded to the second motor controller.",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Forward Profiles Over CAN Bus", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Applies profiles to secondary ESC", fontSize = 11.sp, color = Color.Gray)
                        }

                        Switch(
                            checked = enableDualVesc,
                            onCheckedChange = { checked ->
                                enableDualVesc = checked
                                prefs.edit { putBoolean("enable_dual_vesc", checked) }
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF00E5FF))
                        )
                    }

                    if (enableDualVesc) {
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = secondaryCanIdText,
                            onValueChange = { input ->
                                secondaryCanIdText = input
                                val id = input.toIntOrNull() ?: 53
                                prefs.edit { putInt("secondary_can_id", id) }
                            },
                            label = { Text("Secondary VESC CAN ID") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("Default: 53 (Use 255 for CAN Broadcast)") }
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            "Tip: Set CAN ID to 255 to broadcast profile commands to ALL connected controllers on the CAN bus, or enter the specific CAN ID (e.g. 53, 1, 2).",
                            fontSize = 11.sp,
                            color = Color(0xFF00E5FF)
                        )
                    }
                }
            }
        }

        // Widget Metrics Toggles
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Widget Telemetry Metrics Options", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Choose which VESC variables and metrics appear on your Telemetry Home Screen Widgets.",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    metricsList.forEach { item ->
                        var isChecked by remember { mutableStateOf(prefs.getBoolean(item.prefKey, item.defaultValue)) }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${item.title} (${item.variableName})",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = item.description,
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }

                            Switch(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    isChecked = checked
                                    prefs.edit { putBoolean(item.prefKey, checked) }
                                    BaseTelemetryWidget.sendTelemetryBroadcast(context, telemetryData)
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF00E5FF))
                            )
                        }
                    }
                }
            }
        }

        // BLE Scanner
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("BLE Scanner", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Button(
                            onClick = onStartScan,
                            enabled = !isScanning
                        ) {
                            if (isScanning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.height(16.dp).width(16.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Scanning...")
                            } else {
                                Text("Scan VESC")
                            }
                        }
                    }

                    if (discoveredDevices.isEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "No BLE devices discovered yet. Tap 'Scan VESC' to search for your VESC Bluetooth module.",
                            fontSize = 13.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
        }

        items(discoveredDevices) { device ->
            val isSelected = device.address.equals(macAddress, ignoreCase = true)
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) Color(0xFF1E3A5F) else MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectDevice(device) }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(device.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(device.address, fontSize = 13.sp, color = Color.Gray)
                    }
                    Text("${device.rssi} dBm", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Widget Size Options", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "• Telemetry Compact Widget (2x1): Fast check on 2 selected stats\n" +
                                "• Telemetry Standard Widget (2x2): Speed gauge + 4 customizable metrics\n" +
                                "• Telemetry Dashboard Widget (4x2): Full live dashboard showing up to 8 metrics & fault diagnostic alerts\n" +
                                "• 4 Profile Widgets (2x1): Crawl, Normal, Long Range, Max Power\n\n" +
                                "Long-press your home screen, tap Widgets -> VESC Control Centre to select your size!",
                        fontSize = 13.sp,
                        color = Color.LightGray
                    )
                }
            }
        }
    }
}
