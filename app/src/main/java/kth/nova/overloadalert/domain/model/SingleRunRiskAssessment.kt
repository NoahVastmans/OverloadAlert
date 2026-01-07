package kth.nova.overloadalert.domain.model

/**
 * Represents the calculated risk level for a single run based on the distance relative to the
 * user's longest run in the last 30 days.
 *
 * @property NONE No significant risk detected (run distance is below 1.1x the 30-day max).
 * @property MODERATE Some risk factors present (run distance is between 1.1x and 1.3x the 30-day max).
 * @property HIGH Significant risk of overload (run distance is between 1.3x and 2.0x the 30-day max).
 * @property VERY_HIGH Critical risk level; immediate intervention recommended (run distance exceeds 2.0x the 30-day max).
 */
enum class RiskLevel {
    NONE,
    MODERATE,
    HIGH,
    VERY_HIGH
}

/**
 * Holds the result of a risk assessment for a single run, including the determined risk level and a descriptive message.
 *
 * @property riskLevel The calculated [RiskLevel] (e.g., NONE, MODERATE, HIGH, VERY_HIGH).
 * @property message A human-readable description explaining the reason for the assigned risk level.
 */
data class SingleRunRiskAssessment(
    val riskLevel: RiskLevel,
    val message: String
)