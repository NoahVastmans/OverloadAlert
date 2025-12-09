package kth.nova.overloadalert.ui.screens.history

import kth.nova.overloadalert.domain.model.AnalyzedRun

/**
 * Represents the state of the History screen.
 */
data class HistoryUiState(
    val isLoading: Boolean = true,
    val analyzedRuns: List<AnalyzedRun> = emptyList(),
    val errorMessage: String? = null
)