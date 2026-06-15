package com.ladakh.drone.gcs.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "event_logs")
data class EventLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "timestamp") val timestamp: Long,
    @ColumnInfo(name = "object_type") val objectType: String,
    @ColumnInfo(name = "confidence") val confidence: Double,
    @ColumnInfo(name = "track_id") val trackId: Int
)

@Dao
interface EventLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(event: EventLog)

    @Query("SELECT * FROM event_logs ORDER BY timestamp DESC")
    suspend fun getAllLogs(): List<EventLog>

    @Query("DELETE FROM event_logs")
    suspend fun clearAllLogs()
}
