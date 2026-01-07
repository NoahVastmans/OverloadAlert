package kth.nova.overloadalert.domain.model

import java.time.LocalDate

/**
 * Represents the persisted result of a full historical analysis of training data.
 *
 * This data class serves as a cache mechanism to avoid computationally expensive
 * recalculations of historical load metrics (ACWR, acute/chronic loads, etc.)
 * every time the application launches or updates.
 *
 * @property cacheDate The date when this specific analysis snapshot was generated.
 * @property lastRunHash A hash of the last processed run, used to verify if the cache is stale against current data.
 * @property dailyLoads A time-series list of raw daily training loads.
 * @property cappedDailyLoads A time-series list of daily loads capped at specific thresholds to handle outliers.
 * @property acuteLoadSeries A time-series list of the calculated acute (short-term) training loads.
 * @property chronicLoadSeries A time-series list of the calculated chronic (long-term) training loads.
 * @property acwrByDate A map associating specific dates with their calculated Acute:Chronic Workload Ratio (ACWR) assessment.
 * @property smoothedLongestRunThresholds A list of threshold values derived from longest runs, smoothed over time.
 * @property combinedRiskByRunID A map linking specific run IDs to their calculated combined injury risk assessment.
 */
data class CachedAnalysis(
    val cacheDate: LocalDate, // The date this cache was generated
    val lastRunHash: Int,
    val dailyLoads: List<Float>,
    val cappedDailyLoads: List<Float>,
    val acuteLoadSeries: List<Float>,
    val chronicLoadSeries: List<Float>,
    val acwrByDate: Map<LocalDate, AcwrAssessment>,
    val smoothedLongestRunThresholds: List<Float>,
    val combinedRiskByRunID: Map<Long, CombinedRisk>
)
