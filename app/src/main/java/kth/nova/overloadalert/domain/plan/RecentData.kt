package kth.nova.overloadalert.domain.plan

/**
 * Represents safety and compliance metrics from the user's recent training.
 */
data class RecentData(
    val maxSafeLongRun: Float,
    val maxWeeklyVolume: Float,
    val minDailyVolume: Float,
    val restWeekRequired: Boolean
)