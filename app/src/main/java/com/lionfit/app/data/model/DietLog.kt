package com.lionfit.app.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Entity(tableName = "diet_logs_table")
@Serializable
data class DietLog(
    @PrimaryKey
    @SerialName("id")
    @ColumnInfo(name = "id")
    val id: String = UUID.randomUUID().toString(),

    @SerialName("user_id")
    @ColumnInfo(name = "user_id")
    val userId: String,

    @SerialName("food_name")
    @ColumnInfo(name = "food_name")
    val foodName: String,

    @SerialName("calories")
    @ColumnInfo(name = "calories")
    val calories: Int,

    @SerialName("date_logged")
    @ColumnInfo(name = "date_logged")
    val dateLogged: Long,

    @SerialName("meal_type")
    @ColumnInfo(name = "meal_type")
    val mealType: String
)