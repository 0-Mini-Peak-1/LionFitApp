package com.lionfit.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class RoutePoint(
    val lat: Double,
    val lng: Double
)

@Serializable
@Entity(tableName = "run_sessions_table")
data class RunSession(
    // Change to a String UUID instead of an auto-generating Int
    @PrimaryKey
    @SerialName("id")
    val id: String = UUID.randomUUID().toString(),

    // Add the user_id so Supabase knows who owns this data, RLS thing
    @SerialName("user_id")
    val userId: String,

    @SerialName("title")
    val title: String = "Morning Activity",

    @SerialName("description")
    val description: String = "",

    @SerialName("activity_type")
    val activityType: String = "Run",

    @SerialName("timestamp")
    val timestamp: Long,

    @SerialName("duration_in_millis")
    val durationInMillis: Long,

    @SerialName("distance_in_km")
    val distanceInKm: Double,

    @SerialName("average_pace")
    val averagePace: Double,

    @SerialName("calories_burned")
    val caloriesBurned: Int,

    @SerialName("path_coordinates")
    val pathCoordinates: List<List<RoutePoint>>? = null,

    @SerialName("map_snapshot_url")
    val mapSnapshotUrl: String? = null
)