package kth.nova.overloadalert.domain.plan

/**
 * A single container class for all inputs required by the training plan generator.
 */
data class PlanInput(
    val userPreferences: UserPreferences,
    val historicalData: HistoricalData,
    val recentData: RecentData
)