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

        // --- Data for HomeScreen ---
        val acuteLoad = acuteLoadSeries.lastOrNull() ?: 0f
        val chronicLoad = chronicLoadSeries.lastOrNull() ?: 0f

        val acwrAssessment = if (chronicLoad > 0) {
            val ratio = acuteLoad / chronicLoad
            when {
                ratio > 2.0f -> AcwrAssessment(AcwrRiskLevel.HIGH_OVERTRAINING, ratio, "")
                ratio > 1.3f -> AcwrAssessment(AcwrRiskLevel.MODERATE_OVERTRAINING, ratio, "")
                ratio > 0.8f -> AcwrAssessment(AcwrRiskLevel.OPTIMAL, ratio, "")
                else -> AcwrAssessment(AcwrRiskLevel.UNDERTRAINING, ratio, "")
            }
        } else null

        val mergedRuns = mergeRuns(runs)
        val mostRecentRun = mergedRuns.first()
        val precedingRuns = mergedRuns.drop(1)

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
        val maxSafeLongRun = safeLongestRunForDisplay * 1.1f
        val minRecommendedTodaysRun = min(max(0f, chronicLoad * 0.8f - acuteLoad), maxSafeLongRun * 0.5f)


        val runAnalysis = RunAnalysis(acuteLoad, chronicLoad, recommendedTodaysRun, maxWeeklyLoad, combinedRisk, maxSafeLongRun, minRecommendedTodaysRun)

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
        return UiAnalysisData(runAnalysis, graphData)
    }

    private fun generateCombinedRisk(runRisk: SingleRunRiskAssessment, acwr: AcwrAssessment?): CombinedRisk {
        val runRiskLevel = runRisk.riskLevel
        val acwrRiskLevel = acwr?.riskLevel ?: AcwrRiskLevel.OPTIMAL

        when (acwrRiskLevel) {
            AcwrRiskLevel.UNDERTRAINING -> {
                when (runRiskLevel) {
                    RiskLevel.NONE -> return CombinedRisk("De-training/Recovery", "Your recent training load is low, and this run does not represent a spike. While injury risk is low, prolonged undertraining may limit performance gains.", Color.Blue)
                    RiskLevel.MODERATE -> return CombinedRisk("Risky Spike", "Your overall training load has been low, and this run is noticeably longer than what you’ve done recently. Sudden increases after undertraining can raise injury risk.", Color.Yellow)
                    RiskLevel.HIGH -> return CombinedRisk("High Risk Spike", "You are coming from a low training base, and this run is a large jump in distance. This combination significantly increases injury risk due to insufficient adaptation.", Color.Red)
                    RiskLevel.VERY_HIGH -> return CombinedRisk("Very High Risk Spike", "Your training load has been very low, and this run is more than double your recent longest effort. This is a major spike and carries a very high risk of injury.", Color.Red)
                }
            }
            AcwrRiskLevel.OPTIMAL -> {
                when (runRiskLevel) {
                    RiskLevel.NONE -> return CombinedRisk("Optimal", "Your training load is well balanced, and this run fits within your recent training pattern. Injury risk is currently low.", Color.Green)
                    RiskLevel.MODERATE -> return CombinedRisk("Caution", "Your overall load is well managed, but this run is somewhat longer than usual. Monitor recovery and avoid stacking similar sessions too soon.", Color.Yellow)
                    RiskLevel.HIGH -> return CombinedRisk("Warning", "Your weekly load is optimal, but this run is a large distance increase. Even with good fitness, big single-run spikes can elevate injury risk.", Color.Red)
                    RiskLevel.VERY_HIGH -> return CombinedRisk("Danger", "Despite a well-balanced training load, this run is more than double your recent longest effort. This sudden spike places you at high injury risk.", Color.Red)
                }
            }
            AcwrRiskLevel.MODERATE_OVERTRAINING -> {
                when (runRiskLevel) {
                    RiskLevel.NONE -> return CombinedRisk("Overreaching", "Your overall training load is elevated, indicating accumulated fatigue. Even without a run spike, recovery should be prioritized.", Color.Yellow)
                    RiskLevel.MODERATE -> return CombinedRisk("High Risk", "You are already training above your optimal load, and this run adds additional stress. Injury risk is high if recovery is insufficient.", Color.Red)
                    RiskLevel.HIGH -> return CombinedRisk("Very High Risk", "Your training load is high, and this run represents a major distance increase. This combination substantially increases injury risk.", Color.Red)
                    RiskLevel.VERY_HIGH -> return CombinedRisk("Extreme Risk", "You are in an overtrained state, and this run is an extreme spike compared to recent training. The risk of injury is very high.", Color.Red)
                }
            }
            AcwrRiskLevel.HIGH_OVERTRAINING -> {
                when (runRiskLevel) {
                    RiskLevel.NONE -> return CombinedRisk("High Overtraining Risk", "Your recent training load is extremely high, suggesting significant accumulated fatigue. Even normal runs carry increased injury risk.", Color.Red)
                    RiskLevel.MODERATE -> return CombinedRisk("Very High Risk", "You are heavily overloaded, and this run adds further stress. Injury risk is very high and recovery is strongly advised.", Color.Red)
                    RiskLevel.HIGH -> return CombinedRisk("Extreme Risk", "Your training load is excessive, and this run is a large distance jump. This is an extreme risk scenario for injury.", Color.Red)
                    RiskLevel.VERY_HIGH -> return CombinedRisk("Danger Zone", "Your training load is far beyond optimal, and this run is more than double your recent longest effort. This places you in a critical injury risk zone.", Color.Red)
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

    fun analyzeFullHistory(allRuns: List<Run>): List<AnalyzedRun> {
        val mergedRuns = mergeRuns(allRuns)
        if (mergedRuns.isEmpty()) return emptyList()

        return mergedRuns.mapIndexed { index, run ->
            val allPrecedingRuns = mergedRuns.subList(index + 1, mergedRuns.size)
            val runDate = OffsetDateTime.parse(run.startDateLocal).toLocalDate()
            val thirtyDaysBefore = runDate.minusDays(30)

            val relevantPrecedingRuns = allPrecedingRuns.filter {
                OffsetDateTime.parse(it.startDateLocal).toLocalDate().isAfter(thirtyDaysBefore)
            }

            val baseline = getStableLongestRun(relevantPrecedingRuns)
            val risk = calculateRisk(run.distance, baseline)
            
            AnalyzedRun(run, risk)
        }
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

    private fun mergeRuns(runs: List<Run>): List<Run> {
        val sortedRuns = runs.sortedBy {
            OffsetDateTime.parse(it.startDateLocal)
        }

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

    internal fun getStableLongestRun(runs: List<Run>): Float {
        if (runs.size < 4) {
            return runs.maxOfOrNull { it.distance } ?: 0f
        }

        val distances = runs.map { it.distance }.sorted()

        val q1Index = (distances.size * 0.25).toInt()
        val q3Index = (distances.size * 0.75).toInt()
        val q1 = distances[q1Index]
        val q3 = distances[q3Index]
        val iqr = q3 - q1

        val upperFence = q3 + 1.5f * iqr

        val normalRuns = distances.filter { it <= upperFence }
        return normalRuns.maxOrNull() ?: 0f
    }
}