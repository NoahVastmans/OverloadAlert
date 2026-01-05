package kth.nova.overloadalert.ui.screens.home

import androidx.compose.ui.graphics.Color

/**
 * A UI-ready model representing all the state needed for the HomeScreen.
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
