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

    @Query("SELECT * FROM diet_logs_table WHERE date_logged >= :start AND date_logged <= :end ORDER BY date_logged DESC")
    fun getDietLogsForRange(start: Long, end: Long): Flow<List<DietLog>>

    @Query("SELECT SUM(calories) FROM diet_logs_table WHERE date_logged >= :start AND date_logged <= :end")
    fun getTotalCaloriesForRange(start: Long, end: Long): Flow<Int?>

    @Query("SELECT DISTINCT strftime('%Y-%m-%d', date_logged / 1000, 'unixepoch', 'localtime') FROM diet_logs_table")
    fun getDatesWithLogs(): Flow<List<String>>

    @Query("DELETE FROM diet_logs_table WHERE id = :id")
    suspend fun deleteById(id: String)
}