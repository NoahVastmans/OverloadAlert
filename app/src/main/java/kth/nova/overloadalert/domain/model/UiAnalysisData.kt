package kth.nova.overloadalert.domain.model

import java.time.LocalDate

/**
 * A single container class holding all the analyzed data needed by the entire UI.
 */
data class UiAnalysisData(
    val runAnalysis: RunAnalysis?, // For the HomeScreen
    val graphData: GraphData?,      // For the GraphsScreen
    val combinedRiskByDate: Map<LocalDate, CombinedRisk> = emptyMap()
)