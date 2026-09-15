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
            val prefs = context.getSharedPreferences("vesc_prefs", Context.MODE_PRIVATE)
            val storageOption = prefs.getString("log_storage_option", "PUBLIC_DOCUMENTS") ?: "PUBLIC_DOCUMENTS"
            val timeStr = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
            val fileName = "ride_log_$timeStr.gpx"

            var outputStream: OutputStream? = null

            if (storageOption == "CUSTOM_SAF") {
                val safUriStr = prefs.getString("log_custom_saf_uri", null)
                if (!safUriStr.isNullOrEmpty()) {
                    try {
                        val treeUri = Uri.parse(safUriStr)
                        val pickedDir = DocumentFile.fromTreeUri(context, treeUri)
                        if (pickedDir != null && pickedDir.canWrite()) {
                            val newFile = pickedDir.createFile("application/gpx+xml", fileName)
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
                    Log.d(TAG, "Created GPX log at Public Documents: ${file.absolutePath}")
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
                Log.d(TAG, "Created GPX log at App External Files: ${file.absolutePath}")
            }

            writer = OutputStreamWriter(outputStream, Charsets.UTF_8)
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
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing GPX logger: ${e.message}", e)
        }
    }

    @Synchronized
    fun logTrackPoint(
        lat: Double,
        lon: Double,
        alt: Double,
        speedMetersPerSec: Float,
        bearing: Float,
        accuracy: Float,
        satellites: Int,
        timestampMs: Long = System.currentTimeMillis()
    ) {
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
            if (speedMetersPerSec > 0f) {
                sb.append("        <speed>").append(String.format(Locale.US, "%.2f", speedMetersPerSec)).append("</speed>\n")
            }
            if (bearing > 0f) {
                sb.append("        <course>").append(String.format(Locale.US, "%.1f", bearing)).append("</course>\n")
            }
            if (accuracy > 0f) {
                sb.append("        <hdop>").append(String.format(Locale.US, "%.1f", accuracy)).append("</hdop>\n")
            }
            if (satellites > 0) {
                sb.append("        <sat>").append(satellites).append("</sat>\n")
            }
            sb.append("      </trkpt>\n")

            writer?.write(sb.toString())
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
