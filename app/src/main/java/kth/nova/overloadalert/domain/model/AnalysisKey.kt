package kth.nova.overloadalert.domain.model

/**
 * A unique key representing a specific set of runs used for analysis.
 *
 * This key is derived from the hash of the run IDs and acts as a fingerprint for the input state.
 * It is primarily used to determine if a cached analysis result is still valid or if a
 * recalculation (full or partial) is required due to changes in the underlying run data.
 *
 * @property runIdsHash The calculated hash code of the run identifiers included in this analysis.
 */
data class AnalysisKey(
    val runIdsHash: Int
)