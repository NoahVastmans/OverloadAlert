package kth.nova.overloadalert.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kth.nova.overloadalert.data.RunningRepository
import kth.nova.overloadalert.data.TokenManager
import kth.nova.overloadalert.domain.repository.AnalysisRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel(
    analysisRepository: AnalysisRepository, // The new single source of truth
    private val runningRepository: RunningRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        analysisRepository.latestAnalysis
            .onEach { analysisData ->
                _uiState.update {
                    it.copy(
                        isLoading = analysisData == null,
                        runAnalysis = analysisData?.runAnalysis
                    )
                }
            }
            .launchIn(viewModelScope)

        tokenManager.lastSyncTimestamp
            .onEach { lastSyncTime ->
                _uiState.update { it.copy(lastSyncTime = lastSyncTime) }
            }
            .launchIn(viewModelScope)
    }

    fun refreshData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = runningRepository.syncRuns()
            if (result.isFailure) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        syncErrorMessage = "Sync failed: ${result.exceptionOrNull()?.message}"
                    )
                }
            }
            // On success, the AnalysisRepository's flow will automatically trigger an update.
        }
    }

    fun onSyncErrorShown() {
        _uiState.update { it.copy(syncErrorMessage = null) }
    }

    companion object {
        fun provideFactory(
            analysisRepository: AnalysisRepository,
            runningRepository: RunningRepository,
            tokenManager: TokenManager
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return HomeViewModel(analysisRepository, runningRepository, tokenManager) as T
            }
        }
    }
}