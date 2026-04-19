package com.lionfit.app.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lionfit.app.data.model.SleepRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface SleepDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSleepRecord(sleepRecord: SleepRecord)

    @Query("SELECT * FROM sleep_records_table ORDER BY dateLogged DESC")
    fun getAllSleepRecords(): Flow<List<SleepRecord>>

    // ดึงชั่วโมงการนอนรวมของวันที่ระบุ
    @Query("SELECT SUM(totalHoursSlept) FROM sleep_records_table WHERE dateLogged = :date")
    fun getTotalSleepHoursForDay(date: Long): Flow<Double?>

    // ดึงค่าเฉลี่ยชั่วโมงการนอนในช่วงวันที่ระบุ (สำหรับ 1 สัปดาห์)
    @Query("SELECT AVG(totalHoursSlept) FROM sleep_records_table WHERE dateLogged >= :startDate AND dateLogged <= :endDate")
    fun getAverageSleepHoursForRange(startDate: Long, endDate: Long): Flow<Double?>
}