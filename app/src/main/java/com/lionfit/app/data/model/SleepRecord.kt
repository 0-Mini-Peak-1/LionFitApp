package com.lionfit.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
@Entity(tableName = "sleep_records")
data class SleepRecord(
    @PrimaryKey
    @SerialName("id")
    val id: String = UUID.randomUUID().toString(),

    @SerialName("user_id")
    val userId: String,

    @SerialName("date_logged")
    val dateLogged: Long,

    @SerialName("bed_time_in_millis")
    val bedTimeInMillis: Long,

    @SerialName("wake_time_in_millis")
    val wakeTimeInMillis: Long,

    @SerialName("total_hours_slept")
    val totalHoursSlept: Double
)
