package kth.nova.overloadalert.ui.screens.preferences

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kth.nova.overloadalert.data.remote.GoogleAuthRepository
import kth.nova.overloadalert.domain.repository.PreferencesRepository
import kth.nova.overloadalert.domain.plan.UserPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PreferencesViewModel(
    private val preferencesRepository: PreferencesRepository,
    private val googleAuthRepository: GoogleAuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PreferencesUiState())
    val uiState: StateFlow<PreferencesUiState> = _uiState.asStateFlow()

    init {
        preferencesRepository.preferencesFlow
            .onEach { prefs ->
                _uiState.update { 
                    it.copy(
                        isLoading = false, 
                        preferences = prefs,
                        initialPreferences = if (it.initialPreferences == UserPreferences()) prefs else it.initialPreferences,
                        isPlanValid = preferencesRepository.isPlanValid(prefs)
                    ) 
                }
            }
            .launchIn(viewModelScope)

        preferencesRepository.isGoogleConnected
            .onEach { isConnected ->
                _uiState.update { it.copy(isGoogleConnected = isConnected) }
            }
            .launchIn(viewModelScope)
    }

    fun savePreferences(preferences: UserPreferences, onSaveComplete: () -> Unit) {
        viewModelScope.launch {
            val wasPremium = _uiState.value.initialPreferences.isPremium
            if (wasPremium && !preferences.isPremium) {
                googleAuthRepository.signOut()
            }
            preferencesRepository.savePreferences(preferences)
            onSaveComplete()
        }
    }

    fun onPreferencesChanged(newPreferences: UserPreferences) {
        val isValid = preferencesRepository.isPlanValid(newPreferences)
        _uiState.update { 
            it.copy(
                preferences = newPreferences,
                isPlanValid = isValid
            ) 
        }
    }
    
    fun getGoogleSignInIntent(): Intent {
        return googleAuthRepository.getSignInIntent()
    }
    
    fun handleSignInResult(intent: Intent) {
        viewModelScope.launch {
            googleAuthRepository.handleSignInResult(intent)
        }
    }
    
    fun signOut() {
        viewModelScope.launch {
            googleAuthRepository.signOut()
        }
    }

    companion object {
        fun provideFactory(
            preferencesRepository: PreferencesRepository,
            googleAuthRepository: GoogleAuthRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return PreferencesViewModel(preferencesRepository, googleAuthRepository) as T
            }
        }
    }
}