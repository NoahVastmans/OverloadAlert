package kth.nova.overloadalert.ui.screens.plan

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kth.nova.overloadalert.domain.repository.PlanRepository
import kth.nova.overloadalert.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel responsible for managing the UI state of the training plan screen.
 *
 * This ViewModel observes changes from the [PlanRepository] to update the current training plan
 * and monitors the [PreferencesRepository] to track Google account connection status. It exposes a
 * [StateFlow] of [PlanUiState] to the UI and provides methods to trigger calendar synchronization
 * and unlock premium features.
 *
 * @property planRepository The repository used to fetch and sync training plan data.
 * @property preferencesRepository The repository used to observe and update user preferences.
 */
class PlanViewModel(
    private val planRepository: PlanRepository,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlanUiState())
    val uiState: StateFlow<PlanUiState> = _uiState.asStateFlow()

    init {
        planRepository.latestPlan
            .onEach { plan ->
                _uiState.update {
                    it.copy(
                        isLoading = plan == null,
                        trainingPlan = plan
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

    fun syncCalendar() {
        Log.d("PlanViewModel", "Manual sync requested.")
        viewModelScope.launch {
            planRepository.syncCalendar()
        }
    }

    fun unlockPremium() {
        viewModelScope.launch {
            val currentPrefs = preferencesRepository.preferencesFlow.first()
            preferencesRepository.savePreferences(currentPrefs.copy(isPremium = true))
        }
    }

    companion object {
        fun provideFactory(
            planRepository: PlanRepository,
            preferencesRepository: PreferencesRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return PlanViewModel(planRepository, preferencesRepository) as T
            }
        }
    }
}