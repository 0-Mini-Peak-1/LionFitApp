package com.lionfit.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sleep_records_table")
data class SleepRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val dateLogged: Long,         // The day the record belongs to
    val bedTimeInMillis: Long,    // When they went to sleep
    val wakeTimeInMillis: Long,   // When they woke up
    val totalHoursSlept: Double,  // Calculated from bed/wake times
    val notes: String? = null     // Optional field for how they felt
)