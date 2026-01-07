package kth.nova.overloadalert.domain.plan

/**
 * A comprehensive container that aggregates all necessary input data required to generate a training plan.
 *
 * This class encapsulates user settings, historical training patterns, recent performance metrics,
 * optional risk overrides, and any previously active plan to facilitate the creation of a new
 * [WeeklyTrainingPlan].
 *
 * @property userPreferences Configuration settings specific to the user, such as available training days and goals.
 * @property historicalData Long-term training history used to establish baseline capacity and trends.
 * @property recentData Short-term training data (e.g., last 4 weeks) used to assess current fatigue and readiness.
 * @property riskOverride An optional manual override for the calculated injury risk factor. If null, the system calculates risk automatically.
 * @property previousPlan The training plan currently in effect or just completed, used for continuity or progression logic. Defaults to null if no prior plan exists.
 */
data class PlanInput(
    val userPreferences: UserPreferences,
    val historicalData: HistoricalData,
    val recentData: RecentData,
    val riskOverride: RiskOverride?,
    val previousPlan: WeeklyTrainingPlan? = null
)