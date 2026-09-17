package com.example.vesccontrolcentre.health

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseRoute
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Length
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneOffset

class HealthConnectManager(private val context: Context) {
    
    companion object {
        private const val TAG = "HealthConnectManager"
        
        val PERMISSIONS = setOf(
            HealthPermission.getWritePermission(ExerciseSessionRecord::class),
            HealthPermission.getWritePermission(DistanceRecord::class),
            "android.permission.health.WRITE_EXERCISE_ROUTE"
        )
    }

    val healthConnectClient by lazy {
        try {
            HealthConnectClient.getOrCreate(context)
        } catch (e: Exception) {
            Log.e(TAG, "Health Connect is not available: ${e.message}")
            null
        }
    }

    suspend fun hasAllPermissions(): Boolean {
        val client = healthConnectClient ?: return false
        return try {
            val granted = client.permissionController.getGrantedPermissions()
            granted.containsAll(PERMISSIONS)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check permissions: ${e.message}")
            false
        }
    }

    data class GpsPoint(
        val latitude: Double,
        val longitude: Double,
        val altitude: Double,
        val timestampMs: Long
    )

    suspend fun writeExerciseSession(
        startTimeMs: Long,
        endTimeMs: Long,
        totalDistanceMeters: Float,
        gpsTrack: List<GpsPoint>
    ): Boolean {
        val client = healthConnectClient ?: return false
        
        return withContext(Dispatchers.IO) {
            try {
                // Ensure end time is strictly greater than start time to prevent errors
                val start = Instant.ofEpochMilli(startTimeMs)
                val end = if (endTimeMs <= startTimeMs) Instant.ofEpochMilli(startTimeMs + 1000) else Instant.ofEpochMilli(endTimeMs)

                val records = mutableListOf<androidx.health.connect.client.records.Record>()

                val exerciseRoute = if (gpsTrack.isNotEmpty()) {
                    val locations = gpsTrack.map {
                        ExerciseRoute.Location(
                            time = Instant.ofEpochMilli(it.timestampMs),
                            latitude = it.latitude,
                            longitude = it.longitude,
                            altitude = if (it.altitude != 0.0) Length.meters(it.altitude) else null
                        )
                    }
                    ExerciseRoute(locations)
                } else {
                    null
                }

                // Create the ExerciseSessionRecord
                val sessionRecord = ExerciseSessionRecord(
                    startTime = start,
                    startZoneOffset = ZoneOffset.systemDefault().rules.getOffset(start),
                    endTime = end,
                    endZoneOffset = ZoneOffset.systemDefault().rules.getOffset(end),
                    exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_BIKING,
                    title = "Electric Scooter Ride",
                    exerciseRoute = exerciseRoute
                )
                records.add(sessionRecord)

                // Optional: Write the total distance if available and > 0
                if (totalDistanceMeters > 0f) {
                    val distanceRecord = DistanceRecord(
                        startTime = start,
                        startZoneOffset = ZoneOffset.systemDefault().rules.getOffset(start),
                        endTime = end,
                        endZoneOffset = ZoneOffset.systemDefault().rules.getOffset(end),
                        distance = Length.meters(totalDistanceMeters.toDouble())
                    )
                    records.add(distanceRecord)
                }

                client.insertRecords(records)
                Log.d(TAG, "Successfully inserted exercise session and distance into Health Connect")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error writing exercise session to Health Connect: ${e.message}", e)
                false
            }
        }
    }
}
