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

class GpxLogger(context: Context) {

    companion object {
        private const val TAG = "GpxLogger"
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
            val file = File(docsDir, "ride_log_$timeStr.gpx")

            val fos = FileOutputStream(file)
            writer = OutputStreamWriter(fos, Charsets.UTF_8)

            writer?.write(
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                        "<gpx version=\"1.1\" creator=\"VESC Control Centre\"\n" +
                        "     xmlns=\"http://www.topografix.com/GPX/1/1\"\n" +
                        "     xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                        "     xsi:schemaLocation=\"http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd\">\n" +
                        "  <trk>\n" +
                        "    <name>VESC Ride</name>\n" +
                        "    <trkseg>\n"
            )
            writer?.flush()
            Log.d(TAG, "GPX Log initialized at ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing GPX logger: ${e.message}", e)
        }
    }

    @Synchronized
    fun logTrackPoint(lat: Double, lon: Double, alt: Double, timestampMs: Long = System.currentTimeMillis()) {
        if (isClosed || writer == null) return
        if (lat == 0.0 && lon == 0.0) return

        try {
            val timeIso = isoFormat.format(Date(timestampMs))
            val sb = StringBuilder()
            sb.append("      <trkpt lat=\"").append(String.format(Locale.US, "%.7f", lat))
                .append("\" lon=\"").append(String.format(Locale.US, "%.7f", lon)).append("\">\n")
            if (alt != 0.0) {
                sb.append("        <ele>").append(String.format(Locale.US, "%.2f", alt)).append("</ele>\n")
            }
            sb.append("        <time>").append(timeIso).append("</time>\n")
            sb.append("      </trkpt>\n")

            writer?.write(sb.toString())
            writer?.flush()
        } catch (e: Exception) {
            Log.e(TAG, "Error logging GPX track point: ${e.message}", e)
        }
    }

    @Synchronized
    fun closeLog() {
        if (isClosed) return
        isClosed = true
        try {
            writer?.write("    </trkseg>\n  </trk>\n</gpx>\n")
            writer?.flush()
            writer?.close()
            Log.d(TAG, "GPX Log successfully closed.")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing GPX logger: ${e.message}", e)
        } finally {
            writer = null
        }
    }
}
