package kth.nova.overloadalert.ui.screens.home

import kth.nova.overloadalert.domain.model.RunAnalysis

/**
 * Represents the state of the Home screen.
 */
data class HomeUiState(
    val isLoading: Boolean = true,
    val runAnalysis: RunAnalysis? = null,
    val lastSyncTime: Long? = null, // Timestamp of the last successful sync
    val syncErrorMessage: String? = null // For temporary errors like no internet
)