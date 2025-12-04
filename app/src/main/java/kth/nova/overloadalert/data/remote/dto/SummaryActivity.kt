package kth.nova.overloadalert.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SummaryActivity(
    val id: Long,
    val name: String,
    val distance: Float,
    @SerialName("moving_time")
    val movingTime: Int,
    @SerialName("start_date")
    val startDate: String
)