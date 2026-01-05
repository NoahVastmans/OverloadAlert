package kth.nova.overloadalert.domain.model

import java.time.LocalDate

/**
 * A data class to hold the results of a full historical analysis.
 * This is cached to prevent re-calculating the entire history on every app start.
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
