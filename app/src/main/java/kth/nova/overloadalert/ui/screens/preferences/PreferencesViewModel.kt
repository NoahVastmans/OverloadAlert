package kth.nova.overloadalert.ui.screens.preferences

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kth.nova.overloadalert.data.remote.GoogleAuthRepository
import kth.nova.overloadalert.data.remote.GoogleTokenManager
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
    private val googleAuthRepository: GoogleAuthRepository,
    private val googleTokenManager: GoogleTokenManager
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
                        isPlanValid = preferencesRepository.isPlanValid(prefs)
                    ) 
                }
            }
            .launchIn(viewModelScope)
            
        googleTokenManager.isConnected
            .onEach { connected ->
                _uiState.update { it.copy(isGoogleConnected = connected) }
            }
            .launchIn(viewModelScope)
    }

    fun savePreferences(preferences: UserPreferences, onSaveComplete: () -> Unit) {
        viewModelScope.launch {
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
    
    // --- Google Sign-In Methods ---
    
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
            preferencesRepository: PreferencesRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                // We'll need to inject these via the factory, 
                // but for now let's assume we can get them from the AppComponent (passed as dependency)
                // Actually, the factory is usually created in the composition root.
                // We will need to update the factory creation in AppComponent or pass dependencies here.
                throw IllegalStateException("Use AppComponent to create this factory")
            }
        }
    }
}