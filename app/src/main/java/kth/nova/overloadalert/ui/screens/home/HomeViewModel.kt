package kth.nova.overloadalert.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kth.nova.overloadalert.data.RunningRepository
import kth.nova.overloadalert.data.TokenManager
import kth.nova.overloadalert.domain.usecases.AnalyzeRunData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel(
    private val runningRepository: RunningRepository,
    analyzeRunData: AnalyzeRunData,
    tokenManager: TokenManager
) : ViewModel() {

    // Private mutable state for one-off events like snackbars
    private val _eventState = MutableStateFlow(HomeUiState())

    // Public immutable state combined from multiple flows
    val uiState: StateFlow<HomeUiState> = combine(
        runningRepository.getRunsForAnalysis(),
        tokenManager.lastSyncTimestamp,
        _eventState
    ) { runs, lastSync, eventState ->
        val analysis = analyzeRunData(runs)
        HomeUiState(
            isLoading = false, // Data is now flowing, so we are not in the initial loading state
            runAnalysis = analysis,
            lastSyncTime = lastSync,
            syncErrorMessage = eventState.syncErrorMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState(isLoading = true) // The very first state is loading
    )

    init {
        // Auto-refresh data if the cache is stale on startup
        val lastSync = tokenManager.lastSyncTimestamp.value
        val isCacheStale = (System.currentTimeMillis() - lastSync) > 3600 * 1000 // 1 hour
        if (isCacheStale) {
            refreshData()
        }
    }

    fun refreshData() {
        viewModelScope.launch {
            val syncResult = runningRepository.syncRuns()
            if (syncResult.isFailure) {
                _eventState.update { it.copy(syncErrorMessage = "Sync failed. No internet connection?") }
            }
        }
    }
    
    fun onSyncErrorShown() {
        _eventState.update { it.copy(syncErrorMessage = null) }
    }

    fun clearAllData() {
        viewModelScope.launch {
            runningRepository.clearAllRuns()
        }
    }

    companion object {
        fun provideFactory(
            runningRepository: RunningRepository,
            analyzeRunData: AnalyzeRunData,
            tokenManager: TokenManager
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return HomeViewModel(runningRepository, analyzeRunData, tokenManager) as T
            }
        }
    }
}