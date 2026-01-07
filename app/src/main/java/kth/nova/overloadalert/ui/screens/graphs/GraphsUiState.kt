package kth.nova.overloadalert.ui.screens.graphs

import kth.nova.overloadalert.domain.model.GraphData

/**
 * Represents the UI state for the Graphs screen.
 *
 * @property isLoading Indicates whether the graph data is currently being fetched or processed. Defaults to `true`.
 * @property graphData The data required to populate the graphs, or `null` if the data is not yet available.
 */
data class GraphsUiState(
    val isLoading: Boolean = true,
    val graphData: GraphData? = null
)