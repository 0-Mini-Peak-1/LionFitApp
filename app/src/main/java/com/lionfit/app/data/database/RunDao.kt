package com.lionfit.app.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.lionfit.app.data.model.RunSession
import kotlinx.coroutines.flow.Flow

@Dao
interface RunDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRun(runSession: RunSession)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllRuns(runs: List<RunSession>)

    @Update
    suspend fun updateRun(runSession: RunSession)

    @Delete
    suspend fun deleteRun(runSession: RunSession)

    // Flow automatically updates the UI when new runs are added
    @Query("SELECT * FROM run_sessions_table ORDER BY timestamp DESC")
    fun getAllRunsSortedByDate(): Flow<List<RunSession>>
}