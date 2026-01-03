package kth.nova.overloadalert.domain.repository

import kth.nova.overloadalert.domain.plan.UserPreferences
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
