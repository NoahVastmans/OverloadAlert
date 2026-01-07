package kth.nova.overloadalert.ui.screens.login

/**
 * Represents the UI state for the authentication screen.
 *
 * This data class holds the current state of the login UI, specifically maintaining
 * the authentication URL required for the login flow.
 *
 * @property authUrl The URL used for authentication. Defaults to an empty string if not yet determined.
 */
data class AuthUiState(
    val authUrl: String = ""
)