package kth.nova.overloadalert.ui.screens.home

import androidx.compose.ui.graphics.Color

/**
 * Represents the various states for the Home screen.
 *
 * @param isLoading True if the screen is currently loading data, false otherwise.
 * @param lastSyncLabel A user-friendly string indicating the last data sync time (e.g., "Updated just now").
 * @param syncErrorMessage An error message to display if data synchronization fails.
 * @param riskCard The state for the main risk card, or null if not available.
 * @param recommendationCard The state for the training recommendations card, or null if not available.
 */
data class HomeUiState(
    val isLoading: Boolean = true,
    val lastSyncLabel: String? = null,
    val syncErrorMessage: String? = null,
    val riskCard: RiskCardUi? = null,
    val recommendationCard: RecommendationCardUi? = null
)

/**
 * UI model for the main risk card.
 */
data class RiskCardUi(
    val title: String,
    val description: String,
    val guidancePrefix: String, // e.g., "Guidance:"
    val guidance: String,
    val color: Color
)

/**
 * UI model for the recommendations card.
 */
data class RecommendationCardUi(
    val maxRunToday: String,
    val minRunToday: String,
    val maxWeeklyVolume: String
)
