package kth.nova.overloadalert.domain.model

import kth.nova.overloadalert.data.local.Run

/**
 * Represents a historical training run paired with its comprehensive risk analysis.
 *
 * This container class links a raw [Run] data entity with its computed [CombinedRisk] profile,
 * encapsulating both the factual data of the workout and the derived overload assessment.
 *
 * @property run The raw historical run data.
 * @property risk The fully analyzed risk profile associated with this specific run.
 */
data class AnalyzedRun(
    val run: Run,
    val risk: CombinedRisk // Changed from SingleRunRiskAssessment to CombinedRisk
)