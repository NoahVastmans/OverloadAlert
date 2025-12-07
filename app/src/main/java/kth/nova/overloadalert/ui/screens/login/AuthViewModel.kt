package kth.nova.overloadalert.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kth.nova.overloadalert.data.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AuthViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _isAuthenticated = MutableStateFlow(authRepository.getIsAuthenticated())
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
                // In a production app, you might want to log this to a crash reporting service.
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
            authRepository: AuthRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AuthViewModel(authRepository) as T
            }
        }
    }
}