package kth.nova.overloadalert.ui.screens.home

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

class HomeViewModel(
    private val runningRepository: RunningRepository,
    private val analyzeRunData: AnalyzeRunData
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadRunData()
    }

    private fun loadRunData(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val runs = runningRepository.getRunsForLast30Days(forceRefresh)
                val analysis = analyzeRunData(runs)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        runAnalysis = analysis,
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Failed to load run data. Please connect to Strava."
                    )
                }
            }
        }
    }

    fun refreshData() {
        loadRunData(forceRefresh = true)
    }

    companion object {
        fun provideFactory(
            runningRepository: RunningRepository,
            analyzeRunData: AnalyzeRunData
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return HomeViewModel(runningRepository, analyzeRunData) as T
            }
        }
    }
}