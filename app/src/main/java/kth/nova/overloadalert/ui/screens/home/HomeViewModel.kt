package kth.nova.overloadalert.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kth.nova.overloadalert.data.RunningRepository
import kth.nova.overloadalert.data.TokenManager
import kth.nova.overloadalert.domain.usecases.AnalyzeRunData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel(
    private val runningRepository: RunningRepository,
    analyzeRunData: AnalyzeRunData,
    tokenManager: TokenManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState(isLoading = true))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        runningRepository.getRunsForAnalysis()
            .onEach { runs ->
                val analysis = analyzeRunData(runs)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        runAnalysis = analysis,
                        lastSyncTime = tokenManager.getLastSyncTimestamp()
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    fun refreshData() {
        viewModelScope.launch {
            val syncResult = runningRepository.syncRuns()
            if (syncResult.isFailure) {
                _uiState.update { it.copy(syncErrorMessage = "Sync failed. No internet connection?") }
            }
        }
    }
    
    fun onSyncErrorShown() {
        _uiState.update { it.copy(syncErrorMessage = null) }
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