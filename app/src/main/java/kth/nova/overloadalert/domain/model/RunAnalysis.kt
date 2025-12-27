package kth.nova.overloadalert.domain.model

/**
 * Represents the top-level analysis results for the home screen.
 */
data class RunAnalysis(
    val acuteLoad: Float,
    val chronicLoad: Float,
    val recommendedTodaysRun: Float,
    val maxWeeklyLoad: Float,
    val combinedRisk: CombinedRisk,
    val maxSafeLongRun: Float = 0f, // The recommended upper limit for a single long run
    val minRecommendedTodaysRun: Float = 0f // The recommended lower limit for a single run
)