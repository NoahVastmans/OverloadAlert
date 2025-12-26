package kth.nova.overloadalert.domain.plan

import java.time.DayOfWeek

/**
 * Represents the training plan for a single day.
 */
data class DailyPlan(
    val dayOfWeek: DayOfWeek,
    val runType: RunType,
    val plannedDistance: Float = 0f // in meters
)