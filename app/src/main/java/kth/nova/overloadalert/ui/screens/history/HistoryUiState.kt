package kth.nova.overloadalert.ui.screens.history

import kth.nova.overloadalert.domain.model.AnalyzedRun

/**
 * Represents the UI state for the History screen.
 *
 * @property isLoading Indicates whether the historical data is currently being fetched.
 * @property analyzedRuns A list of [AnalyzedRun] objects representing past runs to be displayed.
 * @property errorMessage A localized error message to be shown if the data fetching fails, or null if no error occurred.
 */
data class HistoryUiState(
    val isLoading: Boolean = true,
    val analyzedRuns: List<AnalyzedRun> = emptyList(),
    val errorMessage: String? = null
)