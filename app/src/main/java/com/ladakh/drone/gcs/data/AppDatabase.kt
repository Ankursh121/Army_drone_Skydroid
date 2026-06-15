package com.ladakh.drone.gcs.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [EventLog::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun eventLogDao(): EventLogDao
}
