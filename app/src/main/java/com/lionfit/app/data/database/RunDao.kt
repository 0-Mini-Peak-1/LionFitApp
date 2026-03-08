package com.lionfit.app.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lionfit.app.data.model.RunSession
import kotlinx.coroutines.flow.Flow

@Dao
interface RunDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRun(runSession: RunSession)

    // Flow automatically updates the UI when new runs are added
    @Query("SELECT * FROM run_sessions_table ORDER BY timestamp DESC")
    fun getAllRunsSortedByDate(): Flow<List<RunSession>>
}