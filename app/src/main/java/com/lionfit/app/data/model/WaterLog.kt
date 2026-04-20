package com.lionfit.app.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Entity(tableName = "water_logs_table")
@Serializable
data class WaterLog(
    @PrimaryKey
    @SerialName("id")
    @ColumnInfo(name = "id")
    val id: String = java.util.UUID.randomUUID().toString(),

    @SerialName("user_id")
    @ColumnInfo(name = "user_id")
    val userId: String,

    @SerialName("amount_ml")
    @ColumnInfo(name = "amount_ml")
    val amountMl: Int,

    @SerialName("date_logged")
    @ColumnInfo(name = "date_logged")
    val dateLogged: Long = System.currentTimeMillis()
)
