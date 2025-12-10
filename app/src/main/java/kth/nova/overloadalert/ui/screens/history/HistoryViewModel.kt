package kth.nova.overloadalert.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kth.nova.overloadalert.data.RunningRepository
import kth.nova.overloadalert.domain.usecases.AnalyzeRunData
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class HistoryViewModel(
    runningRepository: RunningRepository,
    analyzeRunData: AnalyzeRunData
) : ViewModel() {

    val uiState: StateFlow<HistoryUiState> = runningRepository.getAllRuns()
        .map { allRuns ->
            val analyzedRuns = analyzeRunData.analyzeFullHistory(allRuns)
            HistoryUiState(isLoading = false, analyzedRuns = analyzedRuns)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = HistoryUiState(isLoading = true)
        )

    companion object {
        fun provideFactory(
            runningRepository: RunningRepository,
            analyzeRunData: AnalyzeRunData
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return HistoryViewModel(runningRepository, analyzeRunData) as T
            }
        }
    }
}