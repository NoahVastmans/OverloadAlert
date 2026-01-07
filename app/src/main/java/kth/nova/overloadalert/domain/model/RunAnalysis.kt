package kth.nova.overloadalert.domain.model

/**
 * Represents the top-level analysis results of a user's running data.
 *
 * This model encapsulates key metrics used to determine overload risk and training recommendations,
 * typically displayed on the home screen or dashboard.
 *
 * @property acuteLoad The short-term training load (e.g., last 7 days).
 * @property chronicLoad The long-term training load (e.g., last 28 days).
 * @property recommendedTodaysRun The calculated optimal distance or load for a run today.
 * @property maxWeeklyLoad The maximum total load suggested for the current week to avoid injury.
 * @property combinedRisk The aggregate risk level calculated from various risk factors.
 * @property safeLongRun The maximum recommended distance or load for a single long run. Defaults to 0f.
 * @property minRecommendedTodaysRun The minimum recommended distance or load for a run today to maintain fitness. Defaults to 0f.
 * @property acwrAssessment The detailed assessment based on the Acute:Chronic Workload Ratio (ACWR). Can be null if data is insufficient.
 * @property singleRunRiskAssessment The assessment of risk specifically related to individual run intensity/volume. Can be null.
 */
data class RunAnalysis(
    val acuteLoad: Float,
    val chronicLoad: Float,
    val recommendedTodaysRun: Float,
    val maxWeeklyLoad: Float,
    val combinedRisk: CombinedRisk,
    val safeLongRun: Float = 0f,
    val minRecommendedTodaysRun: Float = 0f,
    val acwrAssessment: AcwrAssessment? = null,
    val singleRunRiskAssessment: SingleRunRiskAssessment? = null
)