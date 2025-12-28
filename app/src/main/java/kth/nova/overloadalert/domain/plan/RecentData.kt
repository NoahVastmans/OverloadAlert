package kth.nova.overloadalert.domain.plan

/**
 * Represents safety and compliance metrics from the user's recent training.
 */
data class RecentData(
    val maxSafeLongRun: Float,
    val baseWeeklyVolume: Float,
    val minDailyVolume: Float,
    val restWeekRequired: Boolean
)