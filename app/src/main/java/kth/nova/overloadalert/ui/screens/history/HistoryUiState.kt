package kth.nova.overloadalert.ui.screens.history

import kth.nova.overloadalert.data.local.Run

/**
 * Represents the state of the History screen.
 */
data class HistoryUiState(
    val isLoading: Boolean = true,
    val runs: List<Run> = emptyList(),
    val errorMessage: String? = null
)