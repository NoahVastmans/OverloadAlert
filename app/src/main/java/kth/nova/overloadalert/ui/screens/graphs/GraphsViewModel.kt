package kth.nova.overloadalert.ui.screens.graphs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kth.nova.overloadalert.domain.repository.AnalysisRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update

class GraphsViewModel(
    analysisRepository: AnalysisRepository // The new single source of truth
) : ViewModel() {

    private val _uiState = MutableStateFlow(GraphsUiState())
    val uiState: StateFlow<GraphsUiState> = _uiState.asStateFlow()

    init {
        analysisRepository.latestAnalysis
            .onEach { analysisData ->
                _uiState.update {
                    it.copy(
                        isLoading = analysisData == null,
                        graphData = analysisData?.graphData
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    companion object {
        fun provideFactory(
            analysisRepository: AnalysisRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return GraphsViewModel(analysisRepository) as T
            }
        }
    }
}