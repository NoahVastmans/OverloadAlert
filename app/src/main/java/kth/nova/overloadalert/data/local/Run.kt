package kth.nova.overloadalert.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a single recorded run stored in the local database.
 * This entity corresponds to the "runs" table.
 *
 * @property id The unique identifier for the run (typically originating from an external API like Strava).
 * @property distance The total distance of the run in meters.
 * @property startDateLocal The start date and time of the run in local time, formatted as an ISO 8601 string.
 * @property movingTime The time spent moving during the run, measured in seconds.
 */
@Entity(tableName = "runs")
data class Run(
    @PrimaryKey val id: Long,
    val distance: Float, // in meters
    val startDateLocal: String, // ISO 8601 format
    val movingTime: Int // in seconds
)