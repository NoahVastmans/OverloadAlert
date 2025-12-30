package kth.nova.overloadalert.domain.plan

import com.squareup.moshi.JsonClass
import java.time.DayOfWeek

/**
 * Represents the training plan for a single day.
 */
@JsonClass(generateAdapter = true)
data class DailyPlan(
    val dayOfWeek: DayOfWeek,
    val runType: RunType,
    val plannedDistance: Float = 0f, // in meters
    val isRestWeek: Boolean = false
)