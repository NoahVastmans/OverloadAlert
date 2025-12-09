package kth.nova.overloadalert.domain.model

/**
 * Represents the calculated risk level for a given activity or period.
 */
enum class RiskLevel {
    NONE,
    MODERATE,
    HIGH,
    VERY_HIGH
}

/**
 * Holds the result of a risk assessment, including the level and a descriptive message.
 */
data class RiskAssessment(
    val riskLevel: RiskLevel,
    val message: String
)