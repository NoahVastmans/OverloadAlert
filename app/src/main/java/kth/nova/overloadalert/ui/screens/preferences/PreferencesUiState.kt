package kth.nova.overloadalert.ui.screens.preferences

import kth.nova.overloadalert.domain.plan.UserPreferences

/**
 * Represents the UI state for the user preferences screen.
 */
data class PreferencesUiState(
    val isLoading: Boolean = true,
    val preferences: UserPreferences = UserPreferences(),
    val initialPreferences: UserPreferences = UserPreferences(), // The state to compare against for changes
    val isPlanValid: Boolean = true,
    val isGoogleConnected: Boolean = false
)