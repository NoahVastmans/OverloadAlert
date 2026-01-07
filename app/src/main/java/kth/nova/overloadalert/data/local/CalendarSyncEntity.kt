package kth.nova.overloadalert.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a local database entity for tracking synchronization status between the app's workout plan
 * and an external calendar (e.g., Google Calendar).
 *
 * This entity links a specific date (representing a daily workout) to a corresponding external calendar event,
 * allowing the system to track when the event was last synced and if the user has manually modified the time.
 *
 * @property date The primary key representing the date of the workout plan in ISO-8601 format (YYYY-MM-DD).
 * @property googleEventId The unique identifier of the corresponding event created in the Google Calendar.
 * @property lastSyncedAt The timestamp (in milliseconds) of the last successful synchronization.
 * @property userModifiedTime A flag indicating whether the user has manually changed the time of this event,
 *                            which may prevent future auto-sync overwrites.
 */
@Entity(tableName = "calendar_sync")
data class CalendarSyncEntity(
    @PrimaryKey val date: String, // ISO-8601 Date String (YYYY-MM-DD) which acts as the ID for our plan's daily workout
    val googleEventId: String,
    val lastSyncedAt: Long,
    val userModifiedTime: Boolean = false
)