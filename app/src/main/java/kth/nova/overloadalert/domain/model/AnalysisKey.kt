package kth.nova.overloadalert.domain.model

/**
 * A key representing the state of the runs used for analysis.
 * Used to determine if a full or partial recalculation is needed.
 */
data class AnalysisKey(
    val runIdsHash: Int
)