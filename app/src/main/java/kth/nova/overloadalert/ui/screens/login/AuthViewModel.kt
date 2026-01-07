package kth.nova.overloadalert.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kth.nova.overloadalert.data.remote.StravaAuthRepository
import kth.nova.overloadalert.data.remote.StravaTokenManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel responsible for managing authentication state and interactions for the Login screen.
 *
 * This ViewModel handles the logic for:
 * - Generating the initial OAuth authentication URL.
 * - Exchanging an authorization code for access and refresh tokens.
 * - Managing the user's logged-in status based on token existence.
 * - Logging out the user by clearing stored credentials.
 *
 * It exposes the UI state via [uiState] and the authentication status via [isAuthenticated].
 *
 * @property stravaAuthRepository The repository handling the network and logic operations for authentication.
 * @property stravaTokenManager The manager responsible for storing and retrieving auth tokens.
 */
class AuthViewModel(
    private val stravaAuthRepository: StravaAuthRepository,
    private val stravaTokenManager: StravaTokenManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    // The user is authenticated if a refresh token exists.
    private val _isAuthenticated = MutableStateFlow(stravaTokenManager.getRefreshToken() != null)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    init {
        _uiState.update { it.copy(authUrl = stravaAuthRepository.getAuthUrl()) }
    }

    fun exchangeCodeForToken(code: String) {
        viewModelScope.launch {
            try {
                stravaAuthRepository.exchangeCodeForToken(code)
                _isAuthenticated.value = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun logout() {
        stravaAuthRepository.logout()
        _isAuthenticated.value = false
    }

    companion object {
        fun provideFactory(
            stravaAuthRepository: StravaAuthRepository,
            stravaTokenManager: StravaTokenManager
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AuthViewModel(stravaAuthRepository, stravaTokenManager) as T
            }
        }
    }
}