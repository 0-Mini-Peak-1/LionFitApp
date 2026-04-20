package com.lionfit.app.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lionfit.app.data.model.WaterLog
import kotlinx.coroutines.flow.Flow

@Dao
interface WaterDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWaterLog(waterLog: WaterLog)

    @Query("SELECT * FROM water_logs_table ORDER BY date_logged DESC")
    fun getAllWaterLogs(): Flow<List<WaterLog>>

    @Query("SELECT SUM(amount_ml) FROM water_logs_table WHERE date_logged >= :startOfDay AND date_logged <= :endOfDay")
    suspend fun getTotalWaterByDay(startOfDay: Long, endOfDay: Long): Int?

    @Query("DELETE FROM water_logs_table")
    suspend fun clearAll()
}
