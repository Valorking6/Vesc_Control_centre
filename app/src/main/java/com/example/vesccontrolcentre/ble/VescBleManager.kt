package com.example.vesccontrolcentre.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.vesccontrolcentre.model.ProfileConfig
import com.example.vesccontrolcentre.model.TelemetryData
import com.example.vesccontrolcentre.model.getFaultString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

@SuppressLint("MissingPermission")
class VescBleManager(
    private val context: Context,
    private var polePairs: Int = 7,
    private var wheelDiameterInches: Float = 10.0f
) : BluetoothGattCallback() {

    companion object {
        private const val TAG = "VescBleManager"

        val NORDIC_UART_SERVICE_UUID: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
        val RX_CHARACTERISTIC_UUID: UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
        val TX_CHARACTERISTIC_UUID: UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")
        val CLIENT_CHARACTERISTIC_CONFIG_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        private const val COMM_GET_VALUES: Byte = 0x04
        private const val COMM_GET_DECODED_ADC: Byte = 0x35
        private const val COMM_LISP_REPL_CMD: Byte = 138.toByte()
        private const val POLL_INTERVAL_MS = 250L
    }

    private val _telemetryData = MutableStateFlow(TelemetryData())
    val telemetryData: StateFlow<TelemetryData> = _telemetryData.asStateFlow()

    private val _profileEvents = MutableSharedFlow<String>()
    val profileEvents: SharedFlow<String> = _profileEvents.asSharedFlow()

    private var latestAdcThrottle = 0f
    private var latestAdcBrake = 0f
    
    private var activeRideDurationMs = 0L
    private var lastSuccessfulPollMs = 0L

    private var bluetoothGatt: BluetoothGatt? = null
    private var rxCharacteristic: BluetoothGattCharacteristic? = null
    private var txCharacteristic: BluetoothGattCharacteristic? = null

    private val scope = CoroutineScope(Dispatchers.IO)
    private var pollingJob: Job? = null

    private val rxBuffer = ByteArrayOutputStream()
    private val bleMutex = Mutex()
    private var isConnected = false
    private var isPollingPaused = false

    fun updateConfig(polePairs: Int, wheelDiameterInches: Float) {
        this.polePairs = polePairs
        this.wheelDiameterInches = wheelDiameterInches
    }

    fun connect(deviceAddress: String, autoConnect: Boolean = true) {
        if (deviceAddress.isBlank()) {
            _telemetryData.value = TelemetryData(statusText = "No MAC Address Set")
            return
        }

        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val bluetoothAdapter = bluetoothManager?.adapter
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.e(TAG, "Bluetooth not enabled")
            _telemetryData.value = TelemetryData(statusText = "Bluetooth Disabled")
            return
        }

        try {
            val device = bluetoothAdapter.getRemoteDevice(deviceAddress)
            _telemetryData.value = TelemetryData(statusText = "Connecting...")
            Log.d(TAG, "Connecting to device $deviceAddress...")
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
            bluetoothGatt = device.connectGatt(context, autoConnect, this, BluetoothDevice.TRANSPORT_LE)
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting: ${e.message}", e)
            _telemetryData.value = TelemetryData(statusText = "Connection Failed")
        }
    }

    fun disconnect() {
        stopPolling()
        try {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting: ${e.message}", e)
        } finally {
            bluetoothGatt = null
            rxCharacteristic = null
            txCharacteristic = null
            isConnected = false
            _telemetryData.value = TelemetryData(isConnected = false, statusText = "Disconnected")
        }
    }

    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        Log.d(TAG, "onConnectionStateChange: status=$status, newState=$newState")
        if (newState == BluetoothProfile.STATE_CONNECTED) {
            Log.d(TAG, "Connected to GATT server. Requesting MTU 512...")
            _telemetryData.value = TelemetryData(statusText = "Requesting MTU...")
            gatt.requestMtu(512)
        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            Log.d(TAG, "Disconnected from GATT server.")
            disconnect()
        }
    }

    override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
        Log.d(TAG, "onMtuChanged: mtu=$mtu, status=$status. Discovering services...")
        _telemetryData.value = TelemetryData(statusText = "Discovering Services...")
        gatt.discoverServices()
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        Log.d(TAG, "onServicesDiscovered: status=$status")
        if (status == BluetoothGatt.GATT_SUCCESS) {
            val service = gatt.getService(NORDIC_UART_SERVICE_UUID)
            if (service != null) {
                rxCharacteristic = service.getCharacteristic(RX_CHARACTERISTIC_UUID)
                txCharacteristic = service.getCharacteristic(TX_CHARACTERISTIC_UUID)

                txCharacteristic?.let { txChar ->
                    gatt.setCharacteristicNotification(txChar, true)
                    val descriptor = txChar.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)
                    if (descriptor != null) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                        } else {
                            @Suppress("DEPRECATION")
                            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            @Suppress("DEPRECATION")
                            gatt.writeDescriptor(descriptor)
                        }
                    }
                }

                isConnected = true
                _telemetryData.value = TelemetryData(isConnected = true, statusText = "Connected")
                
                val intent = Intent("com.example.vesccontrolcentre.VESC_CONNECTED")
                context.sendBroadcast(intent)
                
                startPolling()
            } else {
                Log.e(TAG, "Nordic UART Service not found")
                _telemetryData.value = TelemetryData(statusText = "NUS Service Not Found")
            }
        } else {
            Log.e(TAG, "Service discovery failed: $status")
            _telemetryData.value = TelemetryData(statusText = "Service Discovery Failed")
        }
    }

    private fun startPolling() {
        stopPolling()
        activeRideDurationMs = 0L
        lastSuccessfulPollMs = 0L
        pollingJob = scope.launch {
            Log.d(TAG, "Starting polling loop...")
            while (true) {
                if (isConnected && !isPollingPaused) {
                    sendCommGetValues()
                    delay(50) // Small delay between requests to not overwhelm the BLE stack
                    sendCommGetDecodedAdc()
                }
                delay(POLL_INTERVAL_MS - 50)
            }
        }
    }

    private fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    private suspend fun sendCommGetValues() {
        bleMutex.withLock {
            val gatt = bluetoothGatt ?: return
            val rxChar = rxCharacteristic ?: return

            val payload = byteArrayOf(COMM_GET_VALUES)
            val packet = framePacket(payload)

            writePacketToGatt(gatt, rxChar, packet, chunkDelayMs = 0)
        }
    }

    private suspend fun sendCommGetDecodedAdc() {
        bleMutex.withLock {
            val gatt = bluetoothGatt ?: return
            val rxChar = rxCharacteristic ?: return

            val payload = byteArrayOf(COMM_GET_DECODED_ADC)
            val packet = framePacket(payload)

            writePacketToGatt(gatt, rxChar, packet, chunkDelayMs = 0)
        }
    }

    fun applyProfile(profileConfig: ProfileConfig) {
        scope.launch {
            try {
                isPollingPaused = true
                delay(100)

                val prefs = context.getSharedPreferences("vesc_prefs", Context.MODE_PRIVATE)
                val isDualVesc = prefs.getBoolean("enable_dual_vesc", true)
                val secondaryCanId = prefs.getInt("secondary_can_id", 53)

                val lispCmd = if (isDualVesc && secondaryCanId in 0..255) {
                    "(progn " +
                            "(conf-set 'l-current-max ${profileConfig.lCurrentMax}) " +
                            "(conf-set 'l-in-current-max ${profileConfig.lInCurrentMax}) " +
                            "(conf-set 'l-watt-max ${profileConfig.lWattMax}) " +
                            "(can-cmd $secondaryCanId \"(progn (conf-set 'l-current-max ${profileConfig.lCurrentMax}) (conf-set 'l-in-current-max ${profileConfig.lInCurrentMax}) (conf-set 'l-watt-max ${profileConfig.lWattMax}))\"))\u0000"
                } else {
                    "(progn " +
                            "(conf-set 'l-current-max ${profileConfig.lCurrentMax}) " +
                            "(conf-set 'l-in-current-max ${profileConfig.lInCurrentMax}) " +
                            "(conf-set 'l-watt-max ${profileConfig.lWattMax}))\u0000"
                }

                val cmdBytes = lispCmd.toByteArray(Charsets.US_ASCII)
                val payload = ByteArray(cmdBytes.size + 1)
                payload[0] = COMM_LISP_REPL_CMD
                System.arraycopy(cmdBytes, 0, payload, 1, cmdBytes.size)

                val packet = framePacket(payload)

                bleMutex.withLock {
                    val gatt = bluetoothGatt
                    val rxChar = rxCharacteristic
                    if (gatt == null || rxChar == null || !isConnected) {
                        _profileEvents.emit("Failed: VESC not connected")
                        return@launch
                    }

                    writePacketInChunks(gatt, rxChar, packet)
                }

                delay(150)
                val dualTag = if (isDualVesc) " [Dual CAN ID: $secondaryCanId]" else ""
                _profileEvents.emit("Applied ${profileConfig.type.defaultTitle} (${profileConfig.lCurrentMax}A / ${profileConfig.lWattMax.toInt()}W)$dualTag")
            } catch (e: Exception) {
                Log.e(TAG, "Error applying profile: ${e.message}", e)
                _profileEvents.emit("Error applying profile: ${e.message}")
            } finally {
                isPollingPaused = false
            }
        }
    }

    private suspend fun writePacketInChunks(
        gatt: BluetoothGatt,
        rxChar: BluetoothGattCharacteristic,
        packetToWrite: ByteArray
    ) {
        val chunkSize = 20
        var offset = 0
        while (offset < packetToWrite.size) {
            val length = Math.min(chunkSize, packetToWrite.size - offset)
            val chunk = ByteArray(length)
            System.arraycopy(packetToWrite, offset, chunk, 0, length)

            writePacketToGatt(gatt, rxChar, chunk, chunkDelayMs = 15)
            offset += length
        }
    }

    private suspend fun writePacketToGatt(
        gatt: BluetoothGatt,
        rxChar: BluetoothGattCharacteristic,
        data: ByteArray,
        chunkDelayMs: Long
    ) {
        if (!isConnected || bluetoothGatt == null) {
            Log.w(TAG, "Cannot write to GATT: Disconnected or null")
            return
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(rxChar, data, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
            } else {
                @Suppress("DEPRECATION")
                rxChar.value = data
                @Suppress("DEPRECATION")
                rxChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(rxChar)
            }
            if (chunkDelayMs > 0) {
                delay(chunkDelayMs)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception writing to GATT: ${e.message}")
        }
    }

    @Deprecated("Deprecated in Java / Android API", ReplaceWith("onCharacteristicChanged"))
    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic
    ) {
        @Suppress("DEPRECATION")
        val data = characteristic.value ?: return
        processIncomingData(data)
    }

    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ) {
        processIncomingData(value)
    }

    @Synchronized
    private fun processIncomingData(bytes: ByteArray) {
        rxBuffer.write(bytes)
        val bufferedBytes = rxBuffer.toByteArray()

        var parseIndex = 0
        while (bufferedBytes.size - parseIndex >= 6) {
            val startByte = bufferedBytes[parseIndex]
            if (startByte == 0x02.toByte()) {
                val payloadLen = bufferedBytes[parseIndex + 1].toInt() and 0xFF
                val totalPacketLen = 1 + 1 + payloadLen + 2 + 1
                if (bufferedBytes.size - parseIndex >= totalPacketLen) {
                    val stopByte = bufferedBytes[parseIndex + totalPacketLen - 1]
                    if (stopByte == 0x03.toByte()) {
                        val payload = bufferedBytes.copyOfRange(parseIndex + 2, parseIndex + 2 + payloadLen)
                        parseVescPayload(payload)
                        parseIndex += totalPacketLen
                        continue
                    }
                } else {
                    break
                }
            } else if (startByte == 0x03.toByte()) {
                if (bufferedBytes.size - parseIndex >= 7) {
                    val lenMsb = bufferedBytes[parseIndex + 1].toInt() and 0xFF
                    val lenLsb = bufferedBytes[parseIndex + 2].toInt() and 0xFF
                    val payloadLen = (lenMsb shl 8) or lenLsb
                    val totalPacketLen = 1 + 2 + payloadLen + 2 + 1
                    if (bufferedBytes.size - parseIndex >= totalPacketLen) {
                        val stopByte = bufferedBytes[parseIndex + totalPacketLen - 1]
                        if (stopByte == 0x03.toByte()) {
                            val payload = bufferedBytes.copyOfRange(parseIndex + 3, parseIndex + 3 + payloadLen)
                            parseVescPayload(payload)
                            parseIndex += totalPacketLen
                            continue
                        }
                    } else {
                        break
                    }
                } else {
                    break
                }
            }
            parseIndex++
        }

        rxBuffer.reset()
        if (parseIndex < bufferedBytes.size) {
            rxBuffer.write(bufferedBytes, parseIndex, bufferedBytes.size - parseIndex)
        }
    }

    private fun parseVescPayload(payload: ByteArray) {
        if (payload.isEmpty()) return

        when (payload[0]) {
            COMM_GET_VALUES -> {
                if (payload.size >= 29) {
                    val buffer = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN)

                    val tempMosfet = buffer.getShort(1).toFloat() / 10.0f
                    val tempMotor = buffer.getShort(3).toFloat() / 10.0f
                    val motorCurrent = buffer.getInt(5).toFloat() / 100.0f
                    val batteryCurrent = buffer.getInt(9).toFloat() / 100.0f
                    val dutyCycle = (buffer.getShort(21).toFloat() / 1000.0f) * 100.0f
                    val rawErpm = buffer.getInt(23).toFloat()
                    val rawVoltage = buffer.getShort(27).toFloat() / 10.0f
                    val ampHoursCharged = if (payload.size >= 37) buffer.getInt(33).toFloat() / 10000.0f else 0f
                    val wattHoursUsed = if (payload.size >= 41) buffer.getInt(37).toFloat() / 10000.0f else 0f

                    // Tachometer Absolute
                    val tachAbs = if (payload.size >= 53) buffer.getInt(49).toLong() else 0L

                    val faultCode = if (payload.size >= 54) payload[53].toInt() and 0xFF else 0
                    val faultText = getFaultString(faultCode)

                    val calculatedMph = calculateMph(
                        erpm = rawErpm,
                        polePairs = this.polePairs,
                        wheelDiameterInches = this.wheelDiameterInches
                    )

                    val currentTimeMs = System.currentTimeMillis()
                    if (lastSuccessfulPollMs > 0) {
                        val delta = currentTimeMs - lastSuccessfulPollMs
                        if (calculatedMph > 0.5f) {
                            activeRideDurationMs += delta
                        }
                    }
                    lastSuccessfulPollMs = currentTimeMs

                    _telemetryData.value = TelemetryData(
                        mph = calculatedMph,
                        voltage = rawVoltage,
                        erpm = rawErpm,
                        motorCurrent = motorCurrent,
                        batteryCurrent = batteryCurrent,
                        dutyCycle = dutyCycle,
                        tempMosfet = tempMosfet,
                        tempMotor = tempMotor,
                        wattHoursUsed = wattHoursUsed,
                        ampHoursCharged = ampHoursCharged,
                        tachometerAbs = tachAbs,
                        faultCode = faultCode,
                        faultText = faultText,
                        adcThrottle = latestAdcThrottle,
                        adcBrake = latestAdcBrake,
                        activeRideDurationMs = activeRideDurationMs,
                        isConnected = true,
                        statusText = "Connected"
                    )
                }
            }
            COMM_GET_DECODED_ADC -> {
                if (payload.size >= 9) {
                    val buffer = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN)
                    latestAdcThrottle = buffer.getFloat(1)
                    latestAdcBrake = buffer.getFloat(5)
                }
            }
        }
    }

    private fun framePacket(payload: ByteArray): ByteArray {
        val crc = crc16(payload)
        val crcMsb = ((crc shr 8) and 0xFF).toByte()
        val crcLsb = (crc and 0xFF).toByte()

        return if (payload.size <= 256) {
            byteArrayOf(0x02, payload.size.toByte()) + payload + byteArrayOf(crcMsb, crcLsb, 0x03)
        } else {
            val lenMsb = ((payload.size shr 8) and 0xFF).toByte()
            val lenLsb = (payload.size and 0xFF).toByte()
            byteArrayOf(0x03, lenMsb, lenLsb) + payload + byteArrayOf(crcMsb, crcLsb, 0x03)
        }
    }

    private fun crc16(buf: ByteArray): Int {
        var crc = 0
        for (byte in buf) {
            val b = byte.toInt() and 0xFF
            crc = crc xor (b shl 8)
            for (i in 0 until 8) {
                if ((crc and 0x8000) != 0) {
                    crc = ((crc shl 1) xor 0x1021) and 0xFFFF
                } else {
                    crc = (crc shl 1) and 0xFFFF
                }
            }
        }
        return crc
    }
}

fun calculateMph(erpm: Float, polePairs: Int = 7, wheelDiameterInches: Float = 10.0f): Float {
    val mechanicalRpm = erpm / polePairs
    val circumferenceInches = Math.PI * wheelDiameterInches
    val inchesPerMinute = mechanicalRpm * circumferenceInches
    return (inchesPerMinute * 60 / 63360).toFloat()
}
