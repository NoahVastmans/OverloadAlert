package kth.nova.overloadalert.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "runs")
data class Run(
    @PrimaryKey val id: Long,
    val distance: Float, // in meters
    val startDateLocal: String, // ISO 8601 format
    val movingTime: Int // in seconds
)