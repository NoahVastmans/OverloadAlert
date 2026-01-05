package kth.nova.overloadalert.ui.screens.home

import kth.nova.overloadalert.domain.model.CombinedRisk
import kth.nova.overloadalert.domain.model.RunAnalysis

class HomeUiMapper {

    fun mapRiskCard(combinedRisk: CombinedRisk): RiskCardUi {
        val loadTitle = combinedRisk.title.split("·", "-", "|").first().trim()
        val (description, guidance) = mapRiskDescription(loadTitle)

        return RiskCardUi(
            title = loadTitle,
            description = description,
            guidancePrefix = "Guidance:",
            guidance = guidance,
            color = combinedRisk.color
        )
    }

    fun mapRecommendationCard(analysis: RunAnalysis): RecommendationCardUi {
        return RecommendationCardUi(
            maxRunToday = String.format("%.2f km", analysis.recommendedTodaysRun / 1000f),
            minRunToday = String.format("%.2f km", analysis.minRecommendedTodaysRun / 1000f),
            maxWeeklyVolume = String.format("%.2f km", analysis.maxWeeklyLoad / 1000f)
        )
    }

    private fun mapRiskDescription(loadTitle: String): Pair<String, String> {
        return when {
            loadTitle.contains("Low", ignoreCase = true) ->
                "Your recent training load is low, indicating detraining/recovery and reduced stimulation of muscles, tendons, and bones. While short-term injury risk may appear low, detrained tissues tolerate sudden increases poorly." to
                "Rebuild volume gradually and avoid rapid increases in weekly distance or intensity."

            loadTitle.contains("Optimal", ignoreCase = true) ->
                "Your recent training load is well balanced, indicating good adaptation to training stress. This range is generally associated with the lowest injury risk when progression is controlled." to
                "Maintain steady progression and avoid unnecessary spikes in volume or intensity."

            loadTitle.contains("Elevated", ignoreCase = true) ->
                "Your recent training load is above the optimal range, suggesting accumulating fatigue. Injury risk increases when elevated load is maintained without sufficient recovery." to
                "Reduce training load slightly and prioritize recovery before further progression."

            loadTitle.contains("High", ignoreCase = true) ->
                "Your recent training load is far above optimal, indicating significant accumulated fatigue and elevated injury risk. Continued loading at this level greatly increases the chance of injury." to
                "Significantly reduce training load and allow focused recovery before resuming progression."

            else ->
                "Your recent training load could not be classified." to
                "Use caution when progressing training volume."
        }
    }
}