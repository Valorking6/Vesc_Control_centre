package com.example.vesccontrolcentre.logging

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileOutputStream
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
            val docsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            if (docsDir != null && !docsDir.exists()) {
                docsDir.mkdirs()
            }
            val timeStr = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
            val file = File(docsDir, "telemetry_log_$timeStr.csv")

            val fos = FileOutputStream(file)
            writer = OutputStreamWriter(fos, Charsets.UTF_8)

            writer?.write("Timestamp,Time_ISO,Speed_MPH,Voltage_V,Motor_Amps,Battery_Amps,Duty_Cycle,Temp_FET_C,Temp_Motor_C,Wh_Used,Ah_Charged\n")
            writer?.flush()
            Log.d(TAG, "CSV Log initialized at ${file.absolutePath}")
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
        timestampMs: Long = System.currentTimeMillis(),
        dutyCycle: Float = 0f,
        tempMosfet: Float = 0f,
        tempMotor: Float = 0f,
        wattHoursUsed: Float = 0f,
        ampHoursCharged: Float = 0f
    ) {
        if (isClosed || writer == null) return
        try {
            val timeIso = isoFormat.format(Date(timestampMs))
            val line = String.format(
                Locale.US,
                "%d,%s,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f\n",
                timestampMs, timeIso, mph, voltage, motorAmps, batteryAmps,
                dutyCycle, tempMosfet, tempMotor, wattHoursUsed, ampHoursCharged
            )
            writer?.write(line)
            writer?.flush()
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
