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

class AnalyzeRunData {

    operator fun invoke(runs: List<Run>, referenceDate: LocalDate): UiAnalysisData {
        if (runs.isEmpty()) return UiAnalysisData(null, null)

        val today = referenceDate // Use the explicit reference date
        val startDate = runs.minOf { OffsetDateTime.parse(it.startDateLocal).toLocalDate() }

        // --- Full Historical Analysis ---
        val dailyLoads = createDailyLoadSeries(runs, startDate, today)
        val capValue = getIqrCapValue(dailyLoads)
        val cappedDailyLoads = dailyLoads.map { it.coerceAtMost(capValue) }

        val acuteLoadSeries = calculateRollingSum(dailyLoads, 7)
        val cappedAcuteLoadSeries = calculateRollingSum(cappedDailyLoads, 7)
        val chronicLoadSeries = calculateEwma(cappedAcuteLoadSeries, 28)

        // --- ACWR time series (single source of truth) ---
        val acwrByDate = mutableMapOf<LocalDate, AcwrAssessment>()

        dailyLoads.indices.forEach { i ->
            val date = startDate.plusDays(i.toLong())
            val acute = acuteLoadSeries[i]
            val chronic = chronicLoadSeries.getOrNull(i) ?: 0f

            if (chronic > 0f) {
                val ratio = acute / chronic
                val risk = when {
                    ratio > 2.0f -> AcwrRiskLevel.HIGH_OVERTRAINING
                    ratio > 1.3f -> AcwrRiskLevel.MODERATE_OVERTRAINING
                    ratio > 0.8f -> AcwrRiskLevel.OPTIMAL
                    else -> AcwrRiskLevel.UNDERTRAINING
                }

                acwrByDate[date] = AcwrAssessment(risk, ratio, "")
            }
        }

        // --- Data for HomeScreen ---
        val acuteLoad = acuteLoadSeries.lastOrNull() ?: 0f
        val chronicLoad = chronicLoadSeries.lastOrNull() ?: 0f
        val acwrAssessment = acwrByDate[today]


        val mergedRuns = mergeRuns(runs)
        val mostRecentRun = mergedRuns.last()
        val precedingRuns = mergedRuns.dropLast(1)

        val thirtyDaysBeforeMostRecent = OffsetDateTime.parse(mostRecentRun.startDateLocal).toLocalDate().minusDays(30)
        val relevantPrecedingRuns = precedingRuns.filter {
            OffsetDateTime.parse(it.startDateLocal).toLocalDate().isAfter(thirtyDaysBeforeMostRecent)
        }
        val stableBaseline = getStableLongestRun(relevantPrecedingRuns)
        val singleRunRiskAssessment = calculateRisk(mostRecentRun.distance, stableBaseline)
        val combinedRisk = generateCombinedRisk(singleRunRiskAssessment, acwrAssessment)

        val safeLongestRunForDisplay = if (mostRecentRun.distance > stableBaseline * 1.1f && stableBaseline > 0) {
            stableBaseline * 1.1f
        } else if (mostRecentRun.distance > stableBaseline && mostRecentRun.distance < stableBaseline * 1.1f) {
            mostRecentRun.distance
        } else {
            stableBaseline
        }

        val todaysLoad = dailyLoads.lastOrNull() ?: 0f
        val remainingLongestRun = safeLongestRunForDisplay * 1.1f - todaysLoad
        val remainingChronicLoad = chronicLoad * 1.3f - acuteLoad
        val recommendedTodaysRun = max(0f, min(remainingLongestRun, remainingChronicLoad))
        val maxWeeklyLoad = max(0f, chronicLoad * 1.3f - todaysLoad)
        val safeLongRun = safeLongestRunForDisplay * 1.1f
        val minRecommendedTodaysRun = min(max(0f, chronicLoad * 0.8f - acuteLoad), safeLongRun * 0.5f)


        val runAnalysis = RunAnalysis(acuteLoad, chronicLoad, recommendedTodaysRun, maxWeeklyLoad, combinedRisk, safeLongRun, minRecommendedTodaysRun, acwrAssessment, singleRunRiskAssessment)

        // --- Data for GraphsScreen ---
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

        val finalDateLabels = (0..29).map { today.minusDays(29L - it).dayOfMonth.toString() }
        val dailyLoadBars = dailyLoads.takeLast(30).mapIndexed { index, value -> BarEntry(index.toFloat(), value) }
        val longestRunThresholdLine = smoothedLongestRunThresholds.takeLast(30).mapIndexed { index, value -> Entry(index.toFloat(), value) }
        val acuteLoadLine = acuteLoadSeries.takeLast(30).mapIndexed { index, value -> Entry(index.toFloat(), value) }
        val chronicLoadLine = chronicLoadSeries.takeLast(30).mapIndexed { index, value -> Entry(index.toFloat(), value) }

        val graphData = GraphData(dailyLoadBars, longestRunThresholdLine, finalDateLabels, acuteLoadLine, chronicLoadLine)

        // --- Data for Historyscreen ---
        val combinedRiskByDate = mutableMapOf<LocalDate, CombinedRisk>()
        val baselineByDate = mutableMapOf<LocalDate, Float>()

        smoothedLongestRunThresholds.forEachIndexed { index, value ->
            val date = startDate.plusDays(index.toLong())
            baselineByDate[date] = value
        }

        mergedRuns.forEachIndexed { index, run ->
            val runDate = OffsetDateTime.parse(run.startDateLocal).toLocalDate()
            val acwrAssessment = acwrByDate[runDate]
            val baseline = baselineByDate[runDate] ?: 0f
            val singleRunRiskAssessment = calculateRisk(run.distance, baseline)
            val combinedRisk = generateCombinedRisk(singleRunRiskAssessment, acwrAssessment)
            combinedRiskByDate[runDate] = combinedRisk

        }
        return UiAnalysisData(runAnalysis, graphData, combinedRiskByDate)
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

    internal fun createDailyLoadSeries(runs: List<Run>, startDate: LocalDate, endDate: LocalDate): List<Float> {
        val dailyLoadMap = runs.groupBy { OffsetDateTime.parse(it.startDateLocal).toLocalDate() }
            .mapValues { (_, runsOnDay) -> runsOnDay.sumOf { it.distance.toDouble() }.toFloat() }

        return (0..ChronoUnit.DAYS.between(startDate, endDate)).map {
            val date = startDate.plusDays(it)
            dailyLoadMap[date] ?: 0f
        }
    }

    internal fun getIqrCapValue(loads: List<Float>): Float {
        if (loads.size < 4) return loads.maxOrNull() ?: Float.MAX_VALUE
        val sortedLoads = loads.filter { it > 0 }.sorted()
        if (sortedLoads.isEmpty()) return Float.MAX_VALUE

        val q1Index = (sortedLoads.size * 0.25).toInt()
        val q3Index = (sortedLoads.size * 0.75).toInt()
        val q1 = sortedLoads[q1Index]
        val q3 = sortedLoads[q3Index]
        val iqr = q3 - q1
        return q3 + 1.5f * iqr
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
        if (runs.size < 4) {
            return runs.maxOfOrNull { it.distance } ?: 0f
        }

        val distances = runs.map { it.distance }.sorted()

        val q1Index = (distances.size * 0.25).toInt()
        val q3Index = (distances.size * 0.75).toInt() // Corrected to use sortedDistances
        val q1 = distances[q1Index]
        val q3 = distances[q3Index]
        val iqr = q3 - q1

        val upperFence = q3 + 1.5f * iqr

        val normalRuns = distances.filter { it <= upperFence }
        return normalRuns.maxOrNull() ?: 0f
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