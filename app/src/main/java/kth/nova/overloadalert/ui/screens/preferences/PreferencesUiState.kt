package kth.nova.overloadalert.ui.screens.preferences

import kth.nova.overloadalert.domain.plan.UserPreferences

/**
 * Represents the UI state for the user preferences screen.
 *
 * @property isLoading Indicates whether the user preferences are currently being loaded.
 * @property preferences The current user preferences state reflecting any unsaved user changes.
 * @property initialPreferences The initial state of the preferences when the screen loaded, used to detect if changes have been made.
 * @property isPlanValid Indicates whether the current plan configuration in [preferences] is valid.
 * @property isGoogleConnected Indicates whether the user has successfully connected their Google account.
 */
data class PreferencesUiState(
    val isLoading: Boolean = true,
    val preferences: UserPreferences = UserPreferences(),
    val initialPreferences: UserPreferences = UserPreferences(), // The state to compare against for changes
    val isPlanValid: Boolean = true,
    val isGoogleConnected: Boolean = false
)