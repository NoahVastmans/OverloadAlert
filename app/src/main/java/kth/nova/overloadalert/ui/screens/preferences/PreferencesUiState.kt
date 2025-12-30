package kth.nova.overloadalert.ui.screens.preferences

import kth.nova.overloadalert.domain.plan.UserPreferences

/**
 * Represents the UI state for the user preferences screen.
 */
data class PreferencesUiState(
    val isLoading: Boolean = true,
    val preferences: UserPreferences = UserPreferences(),
    val isPlanValid: Boolean = true
)