package kth.nova.overloadalert.domain.plan

/**
 * Represents a full, seven-day training plan.
 */
data class WeeklyTrainingPlan(
    val days: List<DailyPlan> = emptyList()
)