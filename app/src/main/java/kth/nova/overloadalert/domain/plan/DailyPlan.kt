package kth.nova.overloadalert.domain.plan

import com.squareup.moshi.JsonClass
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Represents the training plan for a single day.
 */
@JsonClass(generateAdapter = true)
data class DailyPlan(
    val date: LocalDate,
    val dayOfWeek: DayOfWeek,
    val runType: RunType,
    val plannedDistance: Float = 0f, // in meters
    val isRestWeek: Boolean = false
)