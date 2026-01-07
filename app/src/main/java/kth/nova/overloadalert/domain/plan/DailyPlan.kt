package kth.nova.overloadalert.domain.plan

import com.squareup.moshi.JsonClass
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Represents the training plan for a single day.
 *
 * @property date The specific date for this daily plan.
 * @property dayOfWeek The day of the week (e.g., Monday, Tuesday).
 * @property runType The type of run scheduled for the day (e.g., Long Run, Rest).
 * @property plannedDistance The planned distance for the run in meters. Defaults to 0.
 * @property isRestWeek A flag indicating if this day is part of a rest week.
 */
@JsonClass(generateAdapter = true)
data class DailyPlan(
    val date: LocalDate,
    val dayOfWeek: DayOfWeek,
    val runType: RunType,
    val plannedDistance: Float = 0f, // in meters
    val isRestWeek: Boolean = false
)