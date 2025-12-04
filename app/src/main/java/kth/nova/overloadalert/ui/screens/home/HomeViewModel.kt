package kth.nova.overloadalert.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kth.nova.overloadalert.data.RunningRepository
import kth.nova.overloadalert.domain.usecases.AnalyzeRunDataUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel(
    private val runningRepository: RunningRepository,
    private val analyzeRunDataUseCase: AnalyzeRunDataUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadRunData()
    }

    private fun loadRunData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                // This will fail until we implement authentication
                val runs = runningRepository.getRunsForLast30Days()
                val analysis = analyzeRunDataUseCase(runs)
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

    companion object {
        fun provideFactory(
            runningRepository: RunningRepository,
            analyzeRunDataUseCase: AnalyzeRunDataUseCase
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return HomeViewModel(runningRepository, analyzeRunDataUseCase) as T
            }
        }
    }
}