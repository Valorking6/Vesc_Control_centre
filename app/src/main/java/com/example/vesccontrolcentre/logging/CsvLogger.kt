package com.example.vesccontrolcentre.logging

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class CsvLogger(context: Context) {

    companion object {
        private const val TAG = "CsvLogger"
    }

    private var writer: OutputStreamWriter? = null
    private var isClosed = false
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    init {
        try {
            val prefs = context.getSharedPreferences("vesc_prefs", Context.MODE_PRIVATE)
            val storageOption = prefs.getString("log_storage_option", "PUBLIC_DOCUMENTS") ?: "PUBLIC_DOCUMENTS"
            val timeStr = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
            val fileName = "telemetry_log_$timeStr.csv"

            var outputStream: OutputStream? = null

            if (storageOption == "CUSTOM_SAF") {
                val safUriStr = prefs.getString("log_custom_saf_uri", null)
                if (!safUriStr.isNullOrEmpty()) {
                    try {
                        val treeUri = Uri.parse(safUriStr)
                        val pickedDir = DocumentFile.fromTreeUri(context, treeUri)
                        if (pickedDir != null && pickedDir.canWrite()) {
                            val newFile = pickedDir.createFile("text/csv", fileName)
                            if (newFile != null) {
                                outputStream = context.contentResolver.openOutputStream(newFile.uri)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed SAF file creation, falling back: ${e.message}")
                    }
                }
            }

            if (outputStream == null && storageOption == "PUBLIC_DOCUMENTS") {
                try {
                    val publicDocsDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "VESC_Logs")
                    if (!publicDocsDir.exists()) {
                        publicDocsDir.mkdirs()
                    }
                    val file = File(publicDocsDir, fileName)
                    outputStream = FileOutputStream(file)
                    Log.d(TAG, "Created CSV log at Public Documents: ${file.absolutePath}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed public documents directory, falling back: ${e.message}")
                }
            }

            if (outputStream == null) {
                val docsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                if (docsDir != null && !docsDir.exists()) {
                    docsDir.mkdirs()
                }
                val file = File(docsDir, fileName)
                outputStream = FileOutputStream(file)
                Log.d(TAG, "Created CSV log at App External Files: ${file.absolutePath}")
            }

            writer = OutputStreamWriter(outputStream, Charsets.UTF_8)
            writer?.write("Timestamp_ms,Time_ISO,Speed_MPH,Voltage_V,Motor_Amps,Battery_Amps,Duty_Cycle,Temp_FET_C,Temp_Motor_C,Wh_Used,Ah_Charged,Tach_Abs,Fault_Code,Accel_X,Accel_Y,Accel_Z,Gyro_X,Gyro_Y,Gyro_Z,ADC_Throttle,ADC_Brake,Active_Time_Seconds\n")
            writer?.flush()
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing CSV logger: ${e.message}", e)
        }
    }

    @Synchronized
    fun logData(
        mph: Float,
        voltage: Float,
        motorAmps: Float,
        batteryAmps: Float,
        dutyCycle: Float,
        tempMosfet: Float,
        tempMotor: Float,
        wattHoursUsed: Float,
        ampHoursCharged: Float,
        tachAbs: Long,
        faultCode: Int,
        accelX: Float,
        accelY: Float,
        accelZ: Float,
        gyroX: Float,
        gyroY: Float,
        gyroZ: Float,
        adcThrottle: Float,
        adcBrake: Float,
        activeRideDurationMs: Long,
        timestampMs: Long = System.currentTimeMillis()
    ) {
        if (isClosed || writer == null) return
        try {
            val timeIso = isoFormat.format(Date(timestampMs))
            val activeSeconds = activeRideDurationMs / 1000f
            val line = String.format(
                Locale.US,
                "%d,%s,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%d,%d,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.2f\n",
                timestampMs, timeIso, mph, voltage, motorAmps, batteryAmps,
                dutyCycle, tempMosfet, tempMotor, wattHoursUsed, ampHoursCharged,
                tachAbs, faultCode, accelX, accelY, accelZ, gyroX, gyroY, gyroZ, adcThrottle, adcBrake, activeSeconds
            )
            writer?.write(line)
        } catch (e: Exception) {
            Log.e(TAG, "Error logging CSV telemetry: ${e.message}", e)
        }
    }

    @Synchronized
    fun closeLog() {
        if (isClosed) return
        isClosed = true
        try {
            writer?.flush()
            writer?.close()
            Log.d(TAG, "CSV Log successfully closed.")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing CSV logger: ${e.message}", e)
        } finally {
            writer = null
        }
    }
}
