package kth.nova.overloadalert.domain.model

/**
 * Represents the risk level based on the Acute-to-Chronic Workload Ratio (ACWR).
 */
enum class AcwrRiskLevel {
    HIGH_OVERTRAINING,
    MODERATE_OVERTRAINING,
    OPTIMAL,
    UNDERTRAINING
}

/**
 * Holds the result of an ACWR assessment, including the level, ratio, and a descriptive message.
 */
data class AcwrAssessment(
    val riskLevel: AcwrRiskLevel,
    val ratio: Float,
    val message: String
)