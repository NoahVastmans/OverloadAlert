package kth.nova.overloadalert.domain.model


/**
 * A container holding aggregated analysis data required to populate the UI.
 *
 * This class serves as a centralized model for passing processed data between domain logic
 * and presentation layers (such as `HomeScreen` and `GraphsScreen`).
 *
 * @property runAnalysis The analysis summary for a specific run, primarily used by the Home Screen.
 * @property graphData The data points required to render charts on the Graphs Screen.
 * @property combinedRiskByRunID A map associating Run IDs with their calculated [CombinedRisk], defaulting to an empty map if no risks are calculated.
 */
data class UiAnalysisData(
    val runAnalysis: RunAnalysis?, // For the HomeScreen
    val graphData: GraphData?,      // For the GraphsScreen
    val combinedRiskByRunID: Map<Long, CombinedRisk> = emptyMap()
)