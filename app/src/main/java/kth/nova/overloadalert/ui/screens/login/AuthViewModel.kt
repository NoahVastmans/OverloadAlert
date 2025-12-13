package kth.nova.overloadalert.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kth.nova.overloadalert.data.AuthRepository
import kth.nova.overloadalert.data.TokenManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AuthViewModel(
    private val authRepository: AuthRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    // The user is authenticated if a refresh token exists.
    private val _isAuthenticated = MutableStateFlow(tokenManager.getRefreshToken() != null)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    init {
        _uiState.update { it.copy(authUrl = authRepository.getAuthUrl()) }
    }

    fun exchangeCodeForToken(code: String) {
        viewModelScope.launch {
            try {
                authRepository.exchangeCodeForToken(code)
                _isAuthenticated.value = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun logout() {
        authRepository.logout()
        _isAuthenticated.value = false
    }

    companion object {
        fun provideFactory(
            authRepository: AuthRepository,
            tokenManager: TokenManager
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AuthViewModel(authRepository, tokenManager) as T
            }
        }
    }
}