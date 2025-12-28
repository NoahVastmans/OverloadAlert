package kth.nova.overloadalert.ui.screens.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kth.nova.overloadalert.domain.repository.PlanRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update

class PlanViewModel(
    planRepository: PlanRepository // The new single source of truth for the plan
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlanUiState())
    val uiState: StateFlow<PlanUiState> = _uiState.asStateFlow()

    init {
        // Simply collect the pre-calculated plan from the repository.
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
    }

    companion object {
        fun provideFactory(
            planRepository: PlanRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return PlanViewModel(planRepository) as T
            }
        }
    }
}