package com.lionfit.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "diet_logs_table")
data class DietLog(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val foodName: String,
    val calories: Int,
    val dateLogged: Long,         // To group foods by day
    val mealType: String          // e.g., "Breakfast", "Lunch", "Dinner", "Snack"
)