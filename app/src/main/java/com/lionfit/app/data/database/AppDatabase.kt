package com.lionfit.app.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.lionfit.app.data.model.DietLog
import com.lionfit.app.data.model.RunSession
import com.lionfit.app.data.model.SleepRecord

// 1. List all your Entities here. If you add new tables later, you must add them to this array.
@Database(
    entities = [RunSession::class, SleepRecord::class, DietLog::class],
    version = 1,
    exportSchema = false
)
// 2. Tell Room to use the TypeConverter we made for the running map coordinates
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    // 3. Connect the DAOs
    abstract fun runDao(): RunDao
    abstract fun sleepDao(): SleepDao
    abstract fun dietDao(): DietDao

    // 4. Create the Singleton instance
    companion object {
        // @Volatile ensures that changes made by one thread are immediately visible to others
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            // If the INSTANCE is not null, return it. If it is, create the database.
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "lionfit_database" // The name of your database file
                )
                    // Wipes and rebuilds the database if you change the schema (e.g., add a new column)
                    // This is very handy during the development phase!
                    .fallbackToDestructiveMigration()
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}