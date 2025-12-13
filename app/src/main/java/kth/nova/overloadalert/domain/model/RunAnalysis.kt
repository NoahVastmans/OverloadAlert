package kth.nova.overloadalert.domain.model

/**
 * Holds the results of the running load analysis.
 */
data class RunAnalysis(
    // Core calculated metrics
    val longestRunLast30Days: Float,
    val acuteLoad: Float,
    val chronicLoad: Float,

    // Risk Assessments
    val riskAssessment: RiskAssessment?,
    val acwrAssessment: AcwrAssessment?,

    // New Prescriptive Metrics
    val recommendedTodaysRun: Float = 0f,
    val maxWeeklyLoad: Float = 0f
)