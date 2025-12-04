package kth.nova.overloadalert.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kth.nova.overloadalert.domain.CalculateWorkloadRatio
import kth.nova.overloadalert.domain.GetRunRecommendations
import kth.nova.overloadalert.domain.SyncStravaData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val syncStravaData: SyncStravaData,
    private val getRunRecommendations: GetRunRecommendations,
    private val calculateWorkloadRatio: CalculateWorkloadRatio
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeScreenState())
    val uiState: StateFlow<HomeScreenState> = _uiState.asStateFlow()

    init {
        syncAndLoadData()
    }

    private fun syncAndLoadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                syncStravaData()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to sync data from Strava.", isLoading = false) }
                return@launch
            }

            val recommendationsFlow = getRunRecommendations()
            val workloadRatioFlow = calculateWorkloadRatio()

            combine(recommendationsFlow, workloadRatioFlow) { recommendations, workload ->
                HomeScreenState(
                    isLoading = false,
                    runRecommendation = recommendations,
                    workloadRatio = workload
                )
            }.catch { e ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to calculate metrics: ${e.message}"
                    )
                }
            }.collect { combinedState ->
                _uiState.value = combinedState
            }
        }
    }
}