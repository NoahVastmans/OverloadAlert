package kth.nova.overloadalert.domain.plan

/**
 * Represents safety and compliance metrics derived from the user's recent training history.
 *
 * This data encapsulates key limits and baselines used to calculate safe training loads,
 * ensuring that future planned activities do not exceed established risk thresholds.
 *
 * @property maxSafeLongRun The maximum distance or duration considered safe for a single long run based on recent performance.
 * @property baseWeeklyVolume The baseline total volume (distance or duration) the user has been maintaining, serving as a foundation for progression.
 * @property minDailyVolume The minimum volume required for a session to be considered significant or effective within the current plan.
 * @property riskPhase The current risk classification of the user (e.g., if they are currently overloading or recovering), if applicable.
 */
data class RecentData(
    val maxSafeLongRun: Float,
    val baseWeeklyVolume: Float,
    val minDailyVolume: Float,
    val riskPhase: RiskPhase? = null
)