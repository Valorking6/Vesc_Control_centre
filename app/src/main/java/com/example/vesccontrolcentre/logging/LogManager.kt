package com.example.vesccontrolcentre.logging

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LogFileItem(
    val file: File?,
    val uri: Uri?,
    val name: String,
    val isGpx: Boolean,
    val sizeBytes: Long,
    val formattedSize: String,
    val lastModifiedMs: Long,
    val formattedDate: String,
    val locationTag: String
)

object LogManager {

    private const val TAG = "LogManager"

    fun getLogFiles(context: Context): List<LogFileItem> {
        val list = mutableListOf<LogFileItem>()
        val seenPathSet = mutableSetOf<String>()
        val dateFormat = SimpleDateFormat("MMM dd, yyyy  HH:mm:ss", Locale.US)

        // 1. Scan Public Documents (/Documents/VESC_Logs/)
        try {
            val publicDocsDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "VESC_Logs")
            if (publicDocsDir.exists()) {
                val files = publicDocsDir.listFiles { _, name ->
                    name.endsWith(".gpx", ignoreCase = true) || name.endsWith(".csv", ignoreCase = true)
                } ?: emptyArray()

                for (file in files) {
                    if (seenPathSet.add(file.absolutePath)) {
                        list.add(
                            LogFileItem(
                                file = file,
                                uri = null,
                                name = file.name,
                                isGpx = file.name.endsWith(".gpx", ignoreCase = true),
                                sizeBytes = file.length(),
                                formattedSize = formatFileSize(file.length()),
                                lastModifiedMs = file.lastModified(),
                                formattedDate = dateFormat.format(Date(file.lastModified())),
                                locationTag = "Public Documents (/VESC_Logs/)"
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning public documents: ${e.message}")
        }

        // 2. Scan App Private External Files Directory
        try {
            val privateDocsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            if (privateDocsDir != null && privateDocsDir.exists()) {
                val files = privateDocsDir.listFiles { _, name ->
                    name.endsWith(".gpx", ignoreCase = true) || name.endsWith(".csv", ignoreCase = true)
                } ?: emptyArray()

                for (file in files) {
                    if (seenPathSet.add(file.absolutePath)) {
                        list.add(
                            LogFileItem(
                                file = file,
                                uri = null,
                                name = file.name,
                                isGpx = file.name.endsWith(".gpx", ignoreCase = true),
                                sizeBytes = file.length(),
                                formattedSize = formatFileSize(file.length()),
                                lastModifiedMs = file.lastModified(),
                                formattedDate = dateFormat.format(Date(file.lastModified())),
                                locationTag = "App Private Folder"
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning private files: ${e.message}")
        }

        // 3. Scan Custom SAF Directory if configured
        try {
            val prefs = context.getSharedPreferences("vesc_prefs", Context.MODE_PRIVATE)
            val safUriStr = prefs.getString("log_custom_saf_uri", null)
            if (!safUriStr.isNullOrEmpty()) {
                val treeUri = Uri.parse(safUriStr)
                val pickedDir = DocumentFile.fromTreeUri(context, treeUri)
                if (pickedDir != null && pickedDir.exists()) {
                    val docFiles = pickedDir.listFiles()
                    for (doc in docFiles) {
                        val name = doc.name ?: continue
                        if (name.endsWith(".gpx", ignoreCase = true) || name.endsWith(".csv", ignoreCase = true)) {
                            if (seenPathSet.add(doc.uri.toString())) {
                                list.add(
                                    LogFileItem(
                                        file = null,
                                        uri = doc.uri,
                                        name = name,
                                        isGpx = name.endsWith(".gpx", ignoreCase = true),
                                        sizeBytes = doc.length(),
                                        formattedSize = formatFileSize(doc.length()),
                                        lastModifiedMs = doc.lastModified(),
                                        formattedDate = dateFormat.format(Date(doc.lastModified())),
                                        locationTag = "Custom Storage Folder"
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning custom SAF folder: ${e.message}")
        }

        return list.sortedByDescending { it.lastModifiedMs }
    }

    fun deleteLogItem(context: Context, item: LogFileItem): Boolean {
        return try {
            if (item.file != null && item.file.exists()) {
                item.file.delete()
            } else if (item.uri != null) {
                DocumentFile.fromSingleUri(context, item.uri)?.delete() ?: false
            } else false
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting log item ${item.name}: ${e.message}", e)
            false
        }
    }

    fun deleteAllLogs(context: Context): Int {
        var count = 0
        val files = getLogFiles(context)
        for (item in files) {
            if (deleteLogItem(context, item)) {
                count++
            }
        }
        return count
    }

    fun shareLogItem(context: Context, item: LogFileItem) {
        try {
            val contentUri: Uri = if (item.file != null) {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    item.file
                )
            } else item.uri ?: return

            val mimeType = if (item.isGpx) "application/gpx+xml" else "text/csv"

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Share Ride Log (${item.name})")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            Log.e(TAG, "Error sharing log file ${item.name}: ${e.message}", e)
        }
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 -> String.format(Locale.US, "%.2f MB", bytes / (1024f * 1024f))
            bytes >= 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024f)
            else -> "$bytes B"
        }
    }
}
