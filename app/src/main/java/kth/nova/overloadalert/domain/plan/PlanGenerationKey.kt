package kth.nova.overloadalert.domain.plan

import java.time.LocalDate

/**
 * A key representing the inputs required for plan generation.
 * Used with distinctUntilChanged to prevent unnecessary regeneration.
 */
data class PlanGenerationKey(
    val planningDate: LocalDate,
    val userPreferences: UserPreferences,
    val historicalRunsHash: Int
)