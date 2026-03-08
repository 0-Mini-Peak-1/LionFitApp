package com.lionfit.app.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lionfit.app.data.model.DietLog
import kotlinx.coroutines.flow.Flow

@Dao
interface DietDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDietLog(dietLog: DietLog)

    // Useful for calculating total calories consumed on a specific day
    @Query("SELECT SUM(calories) FROM diet_logs_table WHERE dateLogged = :targetDate")
    fun getTotalCaloriesForDate(targetDate: Long): Flow<Int>
}