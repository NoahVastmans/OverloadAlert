package kth.nova.overloadalert.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kth.nova.overloadalert.data.RunningRepository
import kth.nova.overloadalert.data.remote.StravaTokenManager
import kth.nova.overloadalert.domain.repository.AnalysisRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel responsible for managing the UI state of the Home screen.
 *
 * This ViewModel orchestrates the data flow between the domain layer (repositories) and the UI.
 * It combines the latest analysis data and sync timestamps to produce a unified [HomeUiState].
 *
 * Key responsibilities include:
 * - Observing the latest analysis results and mapping them to UI models (Risk and Recommendation cards).
 * - Monitoring the last synchronization time and formatting it for display (e.g., "Last synced: 5 min ago"), automatically updating every minute.
 * - Handling manual data refresh requests via [refreshData] and managing associated loading/error states.
 * - Providing a factory for dependency injection via the companion object.
 *
 * @property runningRepository Repository for triggering run synchronization.
 * @property tokenManager Manager for handling authentication tokens and sync timestamps.
 * @property mapper Mapper used to transform domain models into UI-specific models.
 * @param analysisRepository Repository providing the latest analysis data.
 */
class HomeViewModel(
    analysisRepository: AnalysisRepository,
    private val runningRepository: RunningRepository,
    private val stravaTokenManager: StravaTokenManager,
    private val mapper: HomeUiMapper
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        // Combine analysis data and last sync time into a single UI state
        combine(
            analysisRepository.latestAnalysis,
            stravaTokenManager.lastSyncTimestamp
        ) { analysisData, lastSyncTime ->
            if (analysisData == null) {
                HomeUiState(isLoading = false) // Show "No data" message
            } else {
                HomeUiState(
                    isLoading = false,
                    riskCard = analysisData.runAnalysis?.combinedRisk?.let { mapper.mapRiskCard(it) },
                    recommendationCard = analysisData.runAnalysis?.let { mapper.mapRecommendationCard(it) },
                    lastSyncLabel = formatSyncTime(lastSyncTime)
                )
            }
        }.onEach { newState ->
            _uiState.update { newState }
        }.launchIn(viewModelScope)

        // Separate loop to update the time-sensitive sync label every minute
        viewModelScope.launch {
            while (true) {
                delay(60000L)
                val lastSyncTime = stravaTokenManager.lastSyncTimestamp.value
                _uiState.update { it.copy(lastSyncLabel = formatSyncTime(lastSyncTime)) }
            }
        }
    }

    fun refreshData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, syncErrorMessage = null) }
            val result = runningRepository.syncRuns()
            val errorMessage = result.exceptionOrNull()?.message

            // The analysis flow will update the main content automatically.
            // We only need to handle the loading and error states here.
            _uiState.update { it.copy(isLoading = false, syncErrorMessage = errorMessage) }
        }
    }

    fun onSyncErrorShown() {
        _uiState.update { it.copy(syncErrorMessage = null) }
    }

    private fun formatSyncTime(lastSyncTime: Long?): String? {
        if (lastSyncTime == null || lastSyncTime <= 0) return null
        val minutesAgo = (System.currentTimeMillis() - lastSyncTime) / 60000
        return when {
            minutesAgo < 1 -> "Last synced: just now"
            minutesAgo == 1L -> "Last synced: 1 min ago"
            else -> "Last synced: $minutesAgo min ago"
        }
    }

    companion object {
        fun provideFactory(
            analysisRepository: AnalysisRepository,
            runningRepository: RunningRepository,
            stravaTokenManager: StravaTokenManager,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return HomeViewModel(
                    analysisRepository,
                    runningRepository,
                    stravaTokenManager,
                    HomeUiMapper()
                ) as T
            }
        }
    }
}