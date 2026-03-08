package com.lionfit.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "run_sessions_table")
data class RunSession(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val timestamp: Long,          // Date and time the run started
    val durationInMillis: Long,   // Total time ran
    val distanceInKm: Double,     // Total distance
    val averagePace: Double,      // Minutes per km
    val caloriesBurned: Int

    // Note for later: To store the actual traces of the path (a list of map coordinates),
    // you will need to use a Room "TypeConverter" to save the list as a JSON string.
)