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
    val safeLongRun: Float = 0f,
    val minRecommendedTodaysRun: Float = 0f,
    // Expose raw assessments for policy decisions
    val acwrAssessment: AcwrAssessment? = null,
    val singleRunRiskAssessment: SingleRunRiskAssessment? = null
)