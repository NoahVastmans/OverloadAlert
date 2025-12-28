package kth.nova.overloadalert.domain.plan

import java.time.DayOfWeek

/**
 * Represents the user's stated preferences for their training plan.
 */
data class UserPreferences(
    val preferredLongRunDays: Set<DayOfWeek> = emptySet(),
    val maxRunsPerWeek: Int = 7,
    val forbiddenRunDays: Set<DayOfWeek> = emptySet(),
    val progressionRate: ProgressionRate = ProgressionRate.SLOW, // Default to a safe, slow progression
    val riskOverride: RiskOverride? = null
)