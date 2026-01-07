package kth.nova.overloadalert.domain.model

/**
 * Represents the risk level associated with an athlete's training load, derived from the Acute-to-Chronic Workload Ratio (ACWR).
 *
 * This classification helps identify whether an athlete is in a state of undertraining, optimal training, or at risk of injury due to overtraining.
 *
 * - [HIGH_OVERTRAINING]: Indicates a dangerous spike in workload relative to chronic load (ratio > 2).
 * - [MODERATE_OVERTRAINING]: Indicates a higher than optimal workload (ratio between 1.3 and 2).
 * - [OPTIMAL]: Indicates the "sweet spot" for training progression and injury prevention (ratio between 0.8 and 1.3).
 * - [UNDERTRAINING]: Indicates a workload that may not be sufficient to maintain fitness (ratio < 0.8).
 */
enum class AcwrRiskLevel {
    HIGH_OVERTRAINING,
    MODERATE_OVERTRAINING,
    OPTIMAL,
    UNDERTRAINING
}

/**
 * Holds the result of an ACWR assessment, including the level, ratio, and a descriptive message.
 *
 * @property riskLevel The assessed risk level (e.g., OPTIMAL, UNDERTRAINING) based on the calculated ratio.
 * @property ratio The calculated Acute-to-Chronic Workload Ratio value.
 * @property message A descriptive message explaining the assessment result to the user.
 */
data class AcwrAssessment(
    val riskLevel: AcwrRiskLevel,
    val ratio: Float,
    val message: String
)