package kth.nova.overloadalert.ui.screens.graphs

import kth.nova.overloadalert.domain.model.GraphData

/**
 * Represents the state of the Graphs screen.
 */
data class GraphsUiState(
    val isLoading: Boolean = true,
    val graphData: GraphData? = null
)