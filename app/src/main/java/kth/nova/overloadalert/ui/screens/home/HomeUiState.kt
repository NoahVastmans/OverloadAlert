package kth.nova.overloadalert.ui.screens.home

import kth.nova.overloadalert.domain.model.RunAnalysis

/**
 * Represents the state of the Home screen.
 */
data class HomeUiState(
    val isLoading: Boolean = false,
    val runAnalysis: RunAnalysis? = null,
    val lastSyncTime: Long = 0L,
    val syncErrorMessage: String? = null // Add this back for error handling
)