package kth.nova.overloadalert.domain.model

import androidx.compose.ui.graphics.Color

/**
 * Represents the final, combined risk assessment presented to the user,
 * including a title, a detailed message, and a corresponding color.
 */
data class CombinedRisk(
    val title: String,
    val message: String,
    val color: Color
)