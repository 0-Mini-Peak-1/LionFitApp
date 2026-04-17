package com.lionfit.app.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.lionfit.app.data.model.DietLog
import com.lionfit.app.data.model.RunSession
import com.lionfit.app.data.model.SleepRecord

// All entities are listed here
@Database(
    entities = [RunSession::class, SleepRecord::class, DietLog::class],
    version = 3,
    exportSchema = false
)
// Tell Room to use the TypeConverter made for the running map coordinates
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    // Connect the DAOs
    abstract fun runDao(): RunDao
    abstract fun sleepDao(): SleepDao
    abstract fun dietDao(): DietDao

    // Create the Singleton instance
    companion object {
        // @Volatile ensures that changes made by one thread are immediately visible to others
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "LionFitDB"
                )

                    .fallbackToDestructiveMigration()
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}