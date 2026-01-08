package kth.nova.overloadalert.ui.screens.home

import androidx.compose.ui.graphics.Color
import kth.nova.overloadalert.domain.model.AcwrAssessment
import kth.nova.overloadalert.domain.model.AcwrRiskLevel
import kth.nova.overloadalert.domain.model.RunAnalysis

/**
 * Responsible for mapping domain models into UI-specific states for the Home screen.
 *
 * This mapper transforms business logic objects (like [AcwrAssessment] and [RunAnalysis])
 * into display-ready data classes ([RiskCardUi], [RecommendationCardUi]) containing formatted strings,
 * descriptions, and actionable guidance tailored for the user interface.
 *
 * It encapsulates logic for:
 * - Generating user-friendly titles, descriptions, and guidance based on risk levels (Low, Optimal, Elevated, High).
 * - Formatting numerical distance data into readable string representations.
 */
class HomeUiMapper {

    fun mapRiskCard(assessment: AcwrAssessment): RiskCardUi {
        val (title, description, guidance) = mapAcwrToText(assessment.riskLevel)

        return RiskCardUi(
            title = title,
            description = description,
            guidancePrefix = "Guidance:",
            guidance = guidance,
            color = mapRiskToColor(assessment.riskLevel)
        )
    }

    fun mapRecommendationCard(analysis: RunAnalysis): RecommendationCardUi {
        return RecommendationCardUi(
            maxRunToday = String.format("%.2f km", analysis.recommendedTodaysRun / 1000f),
            minRunToday = String.format("%.2f km", analysis.minRecommendedTodaysRun / 1000f),
            maxWeeklyVolume = String.format("%.2f km", analysis.maxWeeklyLoad / 1000f)
        )
    }

    private fun mapRiskToColor(riskLevel: AcwrRiskLevel): Color {
        return when (riskLevel) {
            AcwrRiskLevel.UNDERTRAINING -> Color(0xFF4B71BB) // Blue
            AcwrRiskLevel.OPTIMAL -> Color(0xFF4CAF50)       // Green
            AcwrRiskLevel.MODERATE_OVERTRAINING -> Color(0xFFFFA726) // Orange
            AcwrRiskLevel.HIGH_OVERTRAINING -> Color(0xFFD93535)   // Red
        }
    }

    private fun mapAcwrToText(riskLevel: AcwrRiskLevel): Triple<String, String, String> {
        return when (riskLevel) {
            AcwrRiskLevel.UNDERTRAINING ->
                Triple(
                    "Low Load",
                    "Your recent training load is low, indicating detraining/recovery and reduced stimulation of muscles, tendons, and bones. While short-term injury risk may appear low, detrained tissues tolerate sudden increases poorly.",
                    "Rebuild volume gradually and avoid rapid increases in weekly distance or intensity."
                )

            AcwrRiskLevel.OPTIMAL ->
                Triple(
                    "Optimal Load",
                    "Your recent training load is well balanced, indicating good adaptation to training stress. This range is generally associated with the lowest injury risk when progression is controlled.",
                    "Maintain steady progression and avoid unnecessary spikes in volume or intensity."
                )

            AcwrRiskLevel.MODERATE_OVERTRAINING ->
                Triple(
                    "Elevated Load",
                    "Your recent training load is above the optimal range, suggesting accumulating fatigue. Injury risk increases when elevated load is maintained without sufficient recovery.",
                    "Reduce training load slightly and prioritize recovery before further progression."
                )

            AcwrRiskLevel.HIGH_OVERTRAINING ->
                Triple(
                    "High Load",
                    "Your recent training load is far above optimal, indicating significant accumulated fatigue and elevated injury risk. Continued loading at this level greatly increases the chance of injury.",
                    "Significantly reduce training load and allow focused recovery before resuming progression."
                )
        }
    }
}