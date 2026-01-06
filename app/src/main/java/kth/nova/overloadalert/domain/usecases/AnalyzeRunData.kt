package kth.nova.overloadalert.domain.usecases

import android.util.Log
import androidx.compose.ui.graphics.Color
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import kth.nova.overloadalert.data.local.Run
import kth.nova.overloadalert.domain.model.*
import java.time.Duration
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.min

enum class AnalysisMode {
    PERSISTENT, SIMULATION
}

class AnalyzeRunData {

//    operator fun invoke(runs: List<Run>, referenceDate: LocalDate): UiAnalysisData {
//        if (runs.isEmpty()) return UiAnalysisData(null, null, emptyMap())
//
//        val startDate = runs.minOf { OffsetDateTime.parse(it.startDateLocal).toLocalDate() }
//        val cachedAnalysis = performFullAnalysis(runs, startDate, referenceDate)
//        return deriveUiDataFromCache(cachedAnalysis, referenceDate)
//    }

    fun getAnalysisForFutureDate(cached: CachedAnalysis, newRuns: List<Run>, targetDate: LocalDate): UiAnalysisData {
        val futureCachedAnalysis = updateAnalysisFrom(cached, newRuns, targetDate, AnalysisMode.SIMULATION)
        return deriveUiDataFromCache(futureCachedAnalysis, targetDate)
    }

    fun updateAnalysisFrom(cached: CachedAnalysis?, runs: List<Run>, overlapDate: LocalDate, mode: AnalysisMode): CachedAnalysis {
        if (cached == null) {
            val startDate = runs.minOfOrNull { OffsetDateTime.parse(it.startDateLocal).toLocalDate() } ?: LocalDate.now()
            return performFullAnalysis(runs, startDate, LocalDate.now())
        }

        val startDate = cached.acwrByDate.keys.minOrNull()!!
        val overlapIndex = ChronoUnit.DAYS.between(startDate, overlapDate).toInt().coerceAtLeast(0)

        val seriesEndDate = if (mode == AnalysisMode.SIMULATION) {
            runs.maxOfOrNull { OffsetDateTime.parse(it.startDateLocal).toLocalDate() } ?: overlapDate
        } else {
            LocalDate.now()
        }

        val truncatedDailyLoads = cached.dailyLoads.take(overlapIndex)
        val updatedRuns = runs.filter { OffsetDateTime.parse(it.startDateLocal).toLocalDate().isAfter(overlapDate.minusDays(1)) }
        val newDailyLoads = createDailyLoadSeries(updatedRuns, overlapDate, seriesEndDate)

        val combinedDailyLoads = truncatedDailyLoads + newDailyLoads

        val previousCappedLoads = cached.cappedDailyLoads.take(overlapIndex)
        val newCaps = appendRollingLoadCaps(truncatedDailyLoads, newDailyLoads)
        val newCappedDailyLoads = newDailyLoads.mapIndexed { i, load ->
            val cap = newCaps[i]
            if (cap > 0f) load.coerceAtMost(cap) else load
        }
        val cappedDailyLoads = previousCappedLoads + newCappedDailyLoads

        val previousAcute = cached.acuteLoadSeries.take(overlapIndex)
        val newAcute = appendRollingSum(cached.dailyLoads.take(overlapIndex), newDailyLoads, 7)
        val acuteLoadSeries = previousAcute + newAcute

        val previousChronic = cached.chronicLoadSeries.take(overlapIndex)
        val lastChronic = previousChronic.lastOrNull() ?: 0f

        val newChronic = appendEwma(lastChronic, newAcute, 28)

        val chronicLoadSeries = previousChronic + newChronic

        val acwrByDate = appendAcwrMap(cached.acwrByDate, startDate, overlapIndex, acuteLoadSeries, chronicLoadSeries)

        var finalSmoothed = cached.smoothedLongestRunThresholds
        var combinedRiskByRunID = cached.combinedRiskByRunID

        if (mode == AnalysisMode.PERSISTENT) {
            val totalDays = ChronoUnit.DAYS.between(startDate, LocalDate.now()).toInt() + 1
            val rawThresholds = appendRawLongestRunThresholds(overlapIndex, startDate, totalDays, runs)
            val previousSmoothed = cached.smoothedLongestRunThresholds.getOrNull(overlapIndex - 1) ?: 0f
            val newSmoothedThresholds = appendEwma(previousSmoothed, rawThresholds, 7)
            finalSmoothed = cached.smoothedLongestRunThresholds.take(overlapIndex) + newSmoothedThresholds

            val truncatedCombinedRiskByRunID = cached.combinedRiskByRunID.filter { (runId, _) ->
                val runDate = runs.find { it.id == runId }
                    ?.let { OffsetDateTime.parse(it.startDateLocal).toLocalDate() }
                runDate?.isBefore(overlapDate) ?: true
            }.toMutableMap()

            // Build baseline map
            val baselineByDate =
                finalSmoothed.indices.associate { i -> startDate.plusDays(i.toLong()) to finalSmoothed[i] }

            val newCombinedRiskByRunID = runs.filter { OffsetDateTime.parse(it.startDateLocal).toLocalDate() >= overlapDate }.associate { run ->
                    val runDate = OffsetDateTime.parse(run.startDateLocal).toLocalDate()
                    val baseline = baselineByDate[runDate] ?: 0f
                    val runSingleRisk = calculateRisk(run.distance, baseline)
                    run.id to generateCombinedRisk(runSingleRisk, acwrByDate[runDate])
                }
            combinedRiskByRunID = truncatedCombinedRiskByRunID + newCombinedRiskByRunID
        }


        return CachedAnalysis(
            cacheDate = LocalDate.now(),
            lastRunHash = runs.hashCode(),
            dailyLoads = combinedDailyLoads,
            cappedDailyLoads = cappedDailyLoads,
            acuteLoadSeries = acuteLoadSeries,
            chronicLoadSeries = chronicLoadSeries,
            acwrByDate = acwrByDate,
            smoothedLongestRunThresholds = finalSmoothed,
            combinedRiskByRunID = combinedRiskByRunID
        )
    }

    fun deriveUiDataFromCache(cached: CachedAnalysis? , referenceDate: LocalDate): UiAnalysisData {
        if (cached == null) return UiAnalysisData(null, null)

        val today = referenceDate
        val dailyLoads = cached.dailyLoads
        val acuteLoadSeries = cached.acuteLoadSeries
        val chronicLoadSeries = cached.chronicLoadSeries
        val acwrByDate = cached.acwrByDate
        val smoothedLongestRunThresholds = cached.smoothedLongestRunThresholds
        val combinedRiskByRunID = cached.combinedRiskByRunID

        val acuteLoad = acuteLoadSeries.lastOrNull() ?: 0f
        val chronicLoad = chronicLoadSeries.lastOrNull() ?: 0f

        val stableBaseline = smoothedLongestRunThresholds.lastOrNull() ?: 0f
        val combinedRisk = combinedRiskByRunID.values.lastOrNull()
            ?: CombinedRisk("No Data", "", Color.Gray)

        val singleRunRisk = combinedRisk.toSingleRunRisk()
        val safeLongestRunForDisplay = stableBaseline * 1.1f


        val todaysLoad = dailyLoads.lastOrNull() ?: 0f
        val remainingLongestRun = safeLongestRunForDisplay - todaysLoad
        val remainingChronicLoad = chronicLoad * 1.3f - acuteLoad
        val recommendedTodaysRun = max(0f, min(remainingLongestRun, remainingChronicLoad))
        val maxWeeklyLoad = max(0f, chronicLoad * 1.3f - todaysLoad)
        val minRecommendedTodaysRun = min(max(0f, chronicLoad * 0.8f - acuteLoad), safeLongestRunForDisplay * 0.5f)

        val runAnalysis = RunAnalysis(acuteLoad, chronicLoad, recommendedTodaysRun, maxWeeklyLoad, combinedRisk, safeLongestRunForDisplay, minRecommendedTodaysRun, acwrByDate[today], singleRunRisk)

        // Data For GraphsScreen
        val finalDateLabels = (0..29).map { today.minusDays(29L - it).dayOfMonth.toString() }
        val dailyLoadBars = dailyLoads.takeLast(30).mapIndexed { index, value -> BarEntry(index.toFloat(), value) }
        val longestRunThresholdLine = smoothedLongestRunThresholds.takeLast(30).mapIndexed { index, value -> Entry(index.toFloat(), value) }
        val acuteLoadLine = acuteLoadSeries.takeLast(30).mapIndexed { index, value -> Entry(index.toFloat(), value) }
        val chronicLoadLine = chronicLoadSeries.takeLast(30).mapIndexed { index, value -> Entry(index.toFloat(), value) }

        val graphData = GraphData(dailyLoadBars, longestRunThresholdLine, finalDateLabels, acuteLoadLine, chronicLoadLine)

        // Data For HistoryScreen
        return UiAnalysisData(runAnalysis, graphData, combinedRiskByRunID)
    }

    private fun performFullAnalysis(runs: List<Run>, startDate: LocalDate, endDate: LocalDate): CachedAnalysis {
        val dailyLoads = createDailyLoadSeries(runs, startDate, endDate)
        val loadCapSeries = createRollingLoadCapSeries(dailyLoads)
        val cappedDailyLoads = dailyLoads.mapIndexed { i, load ->
            val cap = loadCapSeries[i]
            if (cap > 0f) load.coerceAtMost(cap) else load
        }


        val acuteLoadSeries = calculateRollingSum(cappedDailyLoads, 7)
        val cappedAcuteLoadSeries = calculateRollingSum(cappedDailyLoads, 7)
        val chronicLoadSeries = calculateEwma(cappedAcuteLoadSeries, 28)

        val acwrByDate = mutableMapOf<LocalDate, AcwrAssessment>()
        dailyLoads.indices.forEach { i ->
            val date = startDate.plusDays(i.toLong())
            val acute = acuteLoadSeries[i]
            val chronic = chronicLoadSeries.getOrNull(i) ?: 0f

            val assessment = computeAcwrAssessment(acute, chronic)
            if (assessment != null) {acwrByDate[date] = assessment}
        }

        val longestRunThresholds = (0 until dailyLoads.size).map { i ->
            val date = startDate.plusDays(i.toLong())
            val thirtyDaysBefore = date.minusDays(30)
            val preceding = runs.filter { run ->
                val runDate = OffsetDateTime.parse(run.startDateLocal).toLocalDate()
                runDate.isAfter(thirtyDaysBefore) && !runDate.isAfter(date.minusDays(1))
            }
            getStableLongestRun(preceding) * 1.1f
        }
        val smoothedLongestRunThresholds = calculateEwma(longestRunThresholds, 7)

        val baselineByDate = smoothedLongestRunThresholds.indices.associate { i ->
            startDate.plusDays(i.toLong()) to smoothedLongestRunThresholds[i]
        }

        val combinedRiskByRunID = runs.associate { run ->
            val runDate = OffsetDateTime.parse(run.startDateLocal).toLocalDate()
            val baseline = baselineByDate[runDate] ?: 0f
            val runSingleRisk = calculateRisk(run.distance, baseline)
            run.id to generateCombinedRisk(runSingleRisk, acwrByDate[runDate])
        }

        return CachedAnalysis(
            cacheDate = LocalDate.now(),
            lastRunHash = runs.hashCode(),
            dailyLoads = dailyLoads,
            cappedDailyLoads = cappedDailyLoads,
            acuteLoadSeries = acuteLoadSeries,
            chronicLoadSeries = chronicLoadSeries,
            acwrByDate = acwrByDate,
            smoothedLongestRunThresholds = smoothedLongestRunThresholds,
            combinedRiskByRunID = combinedRiskByRunID
        )
    }

    private fun computeAcwrAssessment(
        acute: Float,
        chronic: Float
    ): AcwrAssessment? {
        if (chronic <= 0f) return null

        val ratio = acute / chronic
        val risk = when {
            ratio > 2.0f -> AcwrRiskLevel.HIGH_OVERTRAINING
            ratio > 1.3f -> AcwrRiskLevel.MODERATE_OVERTRAINING
            ratio > 0.8f -> AcwrRiskLevel.OPTIMAL
            else -> AcwrRiskLevel.UNDERTRAINING
        }

        return AcwrAssessment(risk, ratio, "")
    }

    internal fun appendAcwrMap(
        cachedAcwr: Map<LocalDate, AcwrAssessment>,
        startDate: LocalDate,
        overlapIndex: Int,
        acuteLoadSeries: List<Float>,
        chronicLoadSeries: List<Float>
    ): Map<LocalDate, AcwrAssessment> {

        val result = cachedAcwr
            .entries
            .filter { entry ->
                ChronoUnit.DAYS.between(startDate, entry.key) < overlapIndex
            }
            .associate { it.toPair() }
            .toMutableMap()

        for (i in overlapIndex until acuteLoadSeries.size) {
            val acute = acuteLoadSeries[i]
            val chronic = chronicLoadSeries.getOrNull(i) ?: continue
            val date = startDate.plusDays(i.toLong())

            val assessment = computeAcwrAssessment(acute, chronic)
            if (assessment != null) {
                result[date] = assessment
            }
        }

        return result
    }

    private fun generateCombinedRisk(runRisk: SingleRunRiskAssessment, acwr: AcwrAssessment?): CombinedRisk {
        val runRiskLevel = runRisk.riskLevel
        val acwrRiskLevel = acwr?.riskLevel ?: AcwrRiskLevel.OPTIMAL

        when (acwrRiskLevel) {
            AcwrRiskLevel.UNDERTRAINING -> {
                when (runRiskLevel) {
                    RiskLevel.NONE -> return CombinedRisk("Low Load - Stable Run", "Your overall training load is low, indicating detraining/recovery and reduced stimulation of muscles and connective tissue. This run does not create a distance spike, so immediate risk is low, but tissues may tolerate less stress after some time.", Color(0xFF4B71BB))
                    RiskLevel.MODERATE -> return CombinedRisk("Low Load - Moderate Spike", "Your overall training load is low, indicating detraining/recovery and reduced tissue tolerance. This run is moderately longer than your recent longest effort, creating a spike from a detrained baseline and increasing injury risk", Color(0xFFFFA726))
                    RiskLevel.HIGH -> return CombinedRisk("Low Load - Large Spike", "Your overall training load is low, indicating detraining/recovery and reduced tissue tolerance. This run is a large increase over your recent longest effort, placing stress beyond recent adaptation and resulting in high injury risk", Color(0xFFD93535))
                    RiskLevel.VERY_HIGH -> return CombinedRisk("Low Load - Extreme Spike", "Your overall training load is low, indicating detraining/recovery and reduced tissue tolerance. This run is more than double your recent longest effort, creating a sudden overload and very high injury risk.", Color(0xFFD93535))
                }
            }
            AcwrRiskLevel.OPTIMAL -> {
                when (runRiskLevel) {
                    RiskLevel.NONE -> return CombinedRisk("Optimal Load - Stable Run", "Your overall training load is well balanced, indicating good adaptation. This run fits within your recent training pattern and does not create a spike. Injury risk is low.", Color(0xFF5EC961))
                    RiskLevel.MODERATE -> return CombinedRisk("Optimal Load - Moderate Spike", "Your overall training load is well managed, but this run is moderately longer than your recent longest effort. While your base is solid, single-run spikes still increase injury risk.", Color(0xFFFFA726))
                    RiskLevel.HIGH -> return CombinedRisk("Optimal Load - Large Spike", "Your overall training load is balanced, but this run represents a large increase over your recent longest run. Even with good fitness, large spikes stress tissues beyond recent adaptation.", Color(0xFFD93535))
                    RiskLevel.VERY_HIGH -> return CombinedRisk("Optimal Load - Extreme Spike", "Your overall training load is optimal, but this run is more than double your recent longest effort. The spike alone creates a high injury risk despite a good training base.", Color(0xFFD93535))
                }
            }
            AcwrRiskLevel.MODERATE_OVERTRAINING -> {
                when (runRiskLevel) {
                    RiskLevel.NONE -> return CombinedRisk("Elevated Load - Stable Run", "Your overall training load is elevated, suggesting accumulating fatigue. This run does not create a distance spike, but injury risk is already increased due to high load.", Color(0xFFFFA726))
                    RiskLevel.MODERATE -> return CombinedRisk("Elevated Load - Moderate Spike", "Your overall training load is high, and this run adds a moderate distance spike. Combined load and spike increase your injury risk a lot.", Color(0xFFD93535))
                    RiskLevel.HIGH -> return CombinedRisk("Elevated Load - Large Spike", "Your overall training load is elevated, and this run is a large increase over your recent longest effort. This combination places significant stress on an already fatigued system.", Color(0xFFD93535))
                    RiskLevel.VERY_HIGH -> return CombinedRisk("Elevated Load - Extreme Spike", "Your overall training load is high, and this run is more than double your recent longest effort. This creates an extreme injury risk due to both fatigue and sudden overload.", Color(0xFFD93535))
                }
            }
            AcwrRiskLevel.HIGH_OVERTRAINING -> {
                when (runRiskLevel) {
                    RiskLevel.NONE -> return CombinedRisk("High Load - Stable Run", "Your overall training load is very high, indicating high accumulated fatigue. Even without a distance spike, injury risk is high.", Color(0xFFD93535))
                    RiskLevel.MODERATE -> return CombinedRisk("High Load - Moderate Spike", "Your overall training load is very high, and this run adds additional stress. Injury risk is very high even with a moderate spike.", Color(0xFFD93535))
                    RiskLevel.HIGH -> return CombinedRisk("High Load - Large Spike", "Your overall training load is very high, and this run is a large increase over your recent baseline. This combination creates a critical injury risk.", Color(0xFFD93535))
                    RiskLevel.VERY_HIGH -> return CombinedRisk("High Load - Extreme Spike", "Your overall training load is excessive, and this run is more than double your recent longest effort. This represents a critical overload with extremely high injury risk.", Color(0xFFD93535))
                }
            }
        }
    }

    fun CombinedRisk.toSingleRunRisk(): SingleRunRiskAssessment {
        val level = when {
            title.contains("Extreme Spike") -> RiskLevel.VERY_HIGH
            title.contains("Large Spike") -> RiskLevel.HIGH
            title.contains("Moderate Spike") -> RiskLevel.MODERATE
            else -> RiskLevel.NONE
        }

        return SingleRunRiskAssessment(level, "")
    }


    internal fun createDailyLoadSeries(runs: List<Run>, startDate: LocalDate, endDate: LocalDate): List<Float> {
        val dailyLoadMap = runs.groupBy { OffsetDateTime.parse(it.startDateLocal).toLocalDate() }
            .mapValues { (_, runsOnDay) -> runsOnDay.sumOf { it.distance.toDouble() }.toFloat() }

        return (0..ChronoUnit.DAYS.between(startDate, endDate)).map {
            dailyLoadMap[startDate.plusDays(it)] ?: 0f
        }
    }

    internal fun stableUpperBound(
        values: List<Float>,
        minSamples: Int = 4
    ): Float {
        val nonZero = values.filter { it > 0f }

        if (nonZero.isEmpty()) {
            return 0f
        }

        if (nonZero.size < minSamples) {
            return nonZero.maxOrNull() ?: 0f
        }
        val sorted = nonZero.sorted()

        val q1Index = (sorted.size * 0.25).toInt()
        val q3Index = (sorted.size * 0.75).toInt()

        val q1 = sorted[q1Index]
        val q3 = sorted[q3Index]
        val iqr = q3 - q1

        val upperFence = q3 + 1.5f * iqr

        return sorted.filter { it <= upperFence }.maxOrNull() ?: 0f
    }


    internal fun calculateRollingSum(data: List<Float>, window: Int): List<Float> {
        val result = mutableListOf<Float>()
        for (i in data.indices) {
            val start = max(0, i - window + 1)
            val windowSlice = data.subList(start, i + 1)
            result.add(windowSlice.sum())
        }
        return result
    }

    internal fun appendRollingSum(
        previousData: List<Float>,
        newData: List<Float>,
        window: Int
    ): List<Float> {
        val result = mutableListOf<Float>()

        val buffer = previousData
            .takeLast(window - 1)
            .toMutableList()

        newData.forEach { value ->
            buffer.add(value)
            if (buffer.size > window) {
                buffer.removeAt(0)
            }
            result.add(buffer.sum())
        }

        return result
    }


    internal fun calculateEwma(data: List<Float>, span: Int): List<Float> {
        val alpha = 2.0f / (span + 1.0f)
        val ewma = mutableListOf<Float>()
        if (data.isNotEmpty()) {
            ewma.add(data[0]) // The first value is its own EWMA
            for (i in 1 until data.size) {
                val nextVal = alpha * data[i] + (1 - alpha) * ewma.last()
                ewma.add(nextVal)
            }
        }
        return ewma
    }

    internal fun appendEwma(
        lastEwma: Float,
        newData: List<Float>,
        span: Int
    ): List<Float> {
        val alpha = 2f / (span + 1f)
        val result = mutableListOf<Float>()

        var prev = lastEwma
        newData.forEach { value ->
            val next = alpha * value + (1f - alpha) * prev
            result.add(next)
            prev = next
        }

        return result
    }


    private fun calculateRisk(distance: Float, baseline: Float): SingleRunRiskAssessment {
        if (baseline <= 0f) {
            return SingleRunRiskAssessment(RiskLevel.NONE, "No baseline to compare against.")
        }
        val increasePercentage = (distance - baseline) / baseline
        return when {
            increasePercentage > 1.0 -> SingleRunRiskAssessment(RiskLevel.VERY_HIGH, "This run was more than double your recent longest run.")
            increasePercentage > 0.3 -> SingleRunRiskAssessment(RiskLevel.HIGH, "This run was over 30% longer than your recent longest run.")
            increasePercentage > 0.1 -> SingleRunRiskAssessment(RiskLevel.MODERATE, "This run was over 10% longer than your recent longest run.")
            else -> SingleRunRiskAssessment(RiskLevel.NONE, "This run was within a safe range of your recent longest run.")
        }
    }

    internal fun getStableLongestRun(runs: List<Run>): Float {
        return stableUpperBound(runs.map { it.distance })
    }

    internal fun createRollingLoadCapSeries(
        dailyLoads: List<Float>,
        window: Int = 30
    ): List<Float> {
        return dailyLoads.indices.map { i ->
            val start = max(0, i - window)
            val precedingLoads = dailyLoads
                .subList(start, i)
                .filter { it > 0f }

            stableUpperBound(precedingLoads)
        }
    }

    internal fun appendRollingLoadCaps(
        previousDailyLoads: List<Float>,
        newDailyLoads: List<Float>,
        window: Int = 30
    ): List<Float> {
        val caps = mutableListOf<Float>()

        val rollingWindow = previousDailyLoads
            .takeLast(window)
            .toMutableList()

        newDailyLoads.forEach { load ->
            val cap = stableUpperBound(rollingWindow.filter { it > 0f })
            caps.add(cap)

            rollingWindow.add(load)
            if (rollingWindow.size > window) {
                rollingWindow.removeAt(0)
            }
        }

        return caps
    }

    internal fun appendRawLongestRunThresholds(
        overlapIndex: Int,
        startDate: LocalDate,
        totalDays: Int,
        runs: List<Run>
    ): List<Float> {

        val rawThresholds = mutableListOf<Float>()

        val runsByDate = runs
            .map { OffsetDateTime.parse(it.startDateLocal).toLocalDate() to it }
            .sortedBy { it.first }

        var runStartIdx = 0

        for (i in overlapIndex until totalDays) {
            val date = startDate.plusDays(i.toLong())
            val windowStart = date.minusDays(30)

            while (
                runStartIdx < runsByDate.size &&
                runsByDate[runStartIdx].first.isBefore(windowStart)
            ) {
                runStartIdx++
            }

            val relevantRuns = runsByDate
                .drop(runStartIdx)
                .takeWhile { (d, _) -> d.isBefore(date) }
                .map { it.second }

            val threshold = if (relevantRuns.isEmpty()) 0f else getStableLongestRun(relevantRuns) * 1.1f
            rawThresholds.add(threshold)
        }

        return rawThresholds
    }


}

internal fun mergeRuns(runs: List<Run>): List<Run> {
    val sortedRuns = runs.sortedBy { OffsetDateTime.parse(it.startDateLocal) }

    val mergedRuns = mutableListOf<Run>()

    for (run in sortedRuns) {
        if (mergedRuns.isEmpty()) {
            mergedRuns.add(run)
        } else {
            val lastMergedRun = mergedRuns.last()
            val hoursBetween = Duration.between(
                OffsetDateTime.parse(lastMergedRun.startDateLocal),
                OffsetDateTime.parse(run.startDateLocal)
            ).toHours()

            if (hoursBetween in 0 until 2) {
                mergedRuns[mergedRuns.size - 1] =
                    lastMergedRun.copy(
                        distance = lastMergedRun.distance + run.distance,
                        movingTime = lastMergedRun.movingTime + run.movingTime
                    )
            } else {
                mergedRuns.add(run)
            }
        }
    }

    return mergedRuns
}