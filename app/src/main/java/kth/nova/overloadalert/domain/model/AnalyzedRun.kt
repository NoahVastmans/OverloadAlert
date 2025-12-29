package kth.nova.overloadalert.domain.model

import kth.nova.overloadalert.data.local.Run

/**
 * Represents a single run from history, paired with its fully analyzed risk profile.
 */
data class AnalyzedRun(
    val run: Run,
    val risk: CombinedRisk // Changed from SingleRunRiskAssessment to CombinedRisk
)