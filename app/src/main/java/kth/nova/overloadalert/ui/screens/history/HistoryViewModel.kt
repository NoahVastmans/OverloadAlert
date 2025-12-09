package kth.nova.overloadalert.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kth.nova.overloadalert.data.RunningRepository
import kth.nova.overloadalert.domain.usecases.AnalyzeRunData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HistoryViewModel(
    private val runningRepository: RunningRepository,
    private val analyzeRunData: AnalyzeRunData
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        loadAllRuns()
    }

    private fun loadAllRuns() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val allRuns = runningRepository.getAllRuns()
                val analyzedRuns = analyzeRunData.analyzeFullHistory(allRuns)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        analyzedRuns = analyzedRuns
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Failed to load run history."
                    )
                }
            }
        }
    }

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