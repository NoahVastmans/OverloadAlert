package kth.nova.overloadalert.domain.plan

import com.squareup.moshi.JsonClass
import java.time.DayOfWeek

/**
 * Represents the user's stated preferences for their training plan.
 */
@JsonClass(generateAdapter = true)
data class UserPreferences(
    val preferredLongRunDays: Set<DayOfWeek> = emptySet(),
    val maxRunsPerWeek: Int = 7,
    val forbiddenRunDays: Set<DayOfWeek> = emptySet(),
    val progressionRate: ProgressionRate = ProgressionRate.SLOW, // Default to a safe, slow progression
    val isPremium: Boolean = false // Added for paywall feature
)