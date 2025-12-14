package kth.nova.overloadalert.ui.screens.graphs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kth.nova.overloadalert.data.RunningRepository
import kth.nova.overloadalert.domain.usecases.GenerateGraphData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update

class GraphsViewModel(
    runningRepository: RunningRepository,
    generateGraphData: GenerateGraphData
) : ViewModel() {

    private val _uiState = MutableStateFlow(GraphsUiState())
    val uiState: StateFlow<GraphsUiState> = _uiState.asStateFlow()

    init {
        runningRepository.getAllRuns()
            .onEach { runs ->
                val graphData = generateGraphData(runs)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        graphData = graphData
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    companion object {
        fun provideFactory(
            runningRepository: RunningRepository,
            generateGraphData: GenerateGraphData
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return GraphsViewModel(runningRepository, generateGraphData) as T
            }
        }
    }
}