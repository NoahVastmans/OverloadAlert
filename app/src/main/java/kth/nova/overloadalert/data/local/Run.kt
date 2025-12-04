package kth.nova.overloadalert.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.OffsetDateTime

@Entity(tableName = "runs")
data class Run(
    @PrimaryKey val id: Long,
    val name: String,
    val distance: Float,
    val movingTime: Int,
    val startDate: OffsetDateTime
)