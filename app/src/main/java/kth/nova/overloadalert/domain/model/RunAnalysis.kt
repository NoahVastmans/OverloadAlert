package kth.nova.overloadalert.domain.model

/**
 * Holds the results of the running load analysis.
 */
data class RunAnalysis(
    // Core calculated metrics for prescriptive advice
    val acuteLoad: Float,
    val chronicLoad: Float,

    // New Prescriptive Metrics
    val recommendedTodaysRun: Float,
    val maxWeeklyLoad: Float,

    // The final, combined risk assessment for the user
    val combinedRisk: CombinedRisk
)