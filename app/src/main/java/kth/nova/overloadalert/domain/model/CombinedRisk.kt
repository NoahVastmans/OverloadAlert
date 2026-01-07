package kth.nova.overloadalert.domain.model

import androidx.compose.ui.graphics.Color

/**
 * Represents the aggregated risk assessment for a specific monitoring context.
 *
 * This data class encapsulates the UI-ready representation of a risk level, combining
 * a descriptive title, an explanatory message, and a corresponding visual indicator (color).
 * It is typically used to display alerts or status updates to the user regarding system overload conditions.
 *
 * @property title The short, high-level summary of the risk state (e.g., "High Risk").
 * @property message A detailed description or instruction related to the current risk level.
 * @property color The [Color] associated with this risk level, used for UI styling (e.g., Red for high risk).
 */
data class CombinedRisk(
    val title: String,
    val message: String,
    val color: Color
)
