package kth.nova.overloadalert.domain.plan

import com.squareup.moshi.JsonClass
import java.time.DayOfWeek

/**
 * Represents the user's configuration and preferences for generating a training plan.
 *
 * This data class encapsulates constraints and settings such as preferred or forbidden
 * running days, the maximum volume of runs per week, and the desired rate of training
 * progression. It also tracks subscription status for feature gating.
 *
 * @property preferredLongRunDays A set of days of the week when the user prefers to do their long runs.
 * @property maxRunsPerWeek The maximum number of running sessions the user is willing to undertake in a week. Defaults to 7.
 * @property forbiddenRunDays A set of days of the week when the user cannot or does not want to run.
 * @property progressionRate The speed at which training difficulty increases (e.g., SLOW, MODERATE, FAST). Defaults to [ProgressionRate.SLOW] for safety.
 * @property isPremium Indicates whether the user has a premium subscription, unlocking paywalled features.
 */
@JsonClass(generateAdapter = true)
data class UserPreferences(
    val preferredLongRunDays: Set<DayOfWeek> = emptySet(),
    val maxRunsPerWeek: Int = 7,
    val forbiddenRunDays: Set<DayOfWeek> = emptySet(),
    val progressionRate: ProgressionRate = ProgressionRate.SLOW, // Default to a safe, slow progression
    val isPremium: Boolean = false // Added for paywall feature
)