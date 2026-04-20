package com.lionfit.app.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lionfit.app.data.model.SleepRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface SleepDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSleepRecord(sleepRecord: SleepRecord)

    @Delete
    suspend fun deleteSleepRecord(sleepRecord: SleepRecord)

    // ฟังก์ชันเดิม (Dashboard ใช้) - ไม่แก้ไข signature เพื่อไม่ให้ dashboard error
    @Query("SELECT * FROM sleep_records ORDER BY dateLogged DESC")
    fun getAllSleepRecords(): Flow<List<SleepRecord>>

    // ฟังก์ชันใหม่: กรองตาม userId (ใช้ใน SleepFragment)
    @Query("SELECT * FROM sleep_records WHERE userId = :userId ORDER BY dateLogged DESC")
    fun getSleepRecordsByUser(userId: String): Flow<List<SleepRecord>>

    /**
     * วันนี้นอนไปกี่ชั่วโมง (กรองตาม userId)
     */
    @Query("SELECT SUM(totalHoursSlept) FROM sleep_records WHERE userId = :userId AND dateLogged = :todayDate")
    fun getTodayTotalHours(userId: String, todayDate: Long): Flow<Double?>

    /**
     * อาทิตย์นี้นอนเฉลี่ยกี่ชั่วโมง (กรองตาม userId)
     */
    @Query("SELECT AVG(totalHoursSlept) FROM sleep_records WHERE userId = :userId AND dateLogged >= :startDate AND dateLogged <= :endDate")
    fun getWeeklyAverageHours(userId: String, startDate: Long, endDate: Long): Flow<Double?>

    // ดึงข้อมูลการนอนล่าสุดของ User
    @Query("SELECT * FROM sleep_records WHERE userId = :userId ORDER BY wakeTimeInMillis DESC LIMIT 1")
    fun getLatestSleepRecord(userId: String): Flow<SleepRecord?>
}