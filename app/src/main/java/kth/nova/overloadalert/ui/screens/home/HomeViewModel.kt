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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel(
    private val runningRepository: RunningRepository,
    analyzeRunData: AnalyzeRunData,
    tokenManager: TokenManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = runningRepository.getRunsForAnalysis()
        .map {
            val analysis = analyzeRunData(it)
            HomeUiState(
                isLoading = false,
                runAnalysis = analysis,
                lastSyncTime = tokenManager.getLastSyncTimestamp()
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = HomeUiState(isLoading = true)
        )

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