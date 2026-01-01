package kth.nova.overloadalert.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "calendar_sync")
data class CalendarSyncEntity(
    @PrimaryKey val date: String, // ISO-8601 Date String (YYYY-MM-DD) which acts as the ID for our plan's daily workout
    val googleEventId: String,
    val lastSyncedAt: Long,
    val userModifiedTime: Boolean = false
)