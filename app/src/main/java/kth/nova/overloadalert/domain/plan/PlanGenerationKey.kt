package kth.nova.overloadalert.domain.plan

import java.time.LocalDate

/**
 * A composite key encapsulating the parameters that trigger a recalculation of the training plan.
 *
 * This key is primarily used to check for state changes (e.g., via `distinctUntilChanged`).
 * A new plan is only generated if one of these components—the target date, user settings,
 * or the integrity of historical run data—changes.
 *
 * @property planningDate The specific date for which the plan is being generated.
 * @property userPreferences Configuration settings provided by the user (e.g., goals, availability).
 * @property historicalRunsHash A hash code representing the state of past activities; changes here imply new data was imported or modified.
 */
data class PlanGenerationKey(
    val planningDate: LocalDate,
    val userPreferences: UserPreferences,
    val historicalRunsHash: Int
)