package kth.nova.overloadalert.domain.repository

import kth.nova.overloadalert.domain.plan.UserPreferences
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface defining the operations for managing user preferences.
 * This belongs in the Domain layer.
 */
interface PreferencesRepository {
    val preferencesFlow: StateFlow<UserPreferences>
    val isGoogleConnected: StateFlow<Boolean>
    fun savePreferences(preferences: UserPreferences)
    fun isPlanValid(preferences: UserPreferences): Boolean
}
