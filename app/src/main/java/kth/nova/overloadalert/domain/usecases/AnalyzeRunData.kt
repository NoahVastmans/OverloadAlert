package kth.nova.overloadalert.domain.usecases

import kotlinx.coroutines.currentCoroutineContext
import kth.nova.overloadalert.data.local.Run
import kth.nova.overloadalert.domain.model.AcwrAssessment
import kth.nova.overloadalert.domain.model.AcwrRiskLevel
import kth.nova.overloadalert.domain.model.AnalyzedRun
import kth.nova.overloadalert.domain.model.RiskAssessment
import kth.nova.overloadalert.domain.model.RiskLevel
import kth.nova.overloadalert.domain.model.RunAnalysis
import java.time.Duration
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.min

class AnalyzeRunData {

    /**
     * Analyzes the provided runs to produce a summary and risk assessment for the home screen.
     */
    operator fun invoke(runs: List<Run>): RunAnalysis {
        val mergedRuns = mergeRuns(runs)

        if (mergedRuns.isEmpty()) {
            return RunAnalysis(0f, 0f, 0f, null, null, 0f, 0f)
        }

        // --- New EWMA-based Acute and Chronic Load Calculation ---
        val today = LocalDate.now()
        val startDate = if (mergedRuns.isNotEmpty()) mergedRuns.minOf { OffsetDateTime.parse(it.startDateLocal).toLocalDate() } else today
        
        val dailyLoads = createDailyLoadSeries(mergedRuns, startDate, today)
        val capValue = getIqrCapValue(dailyLoads)*1.1f
        val cappedDailyLoads = dailyLoads.map { it.coerceAtMost(capValue) }

        val acuteLoadSeries = calculateRollingSum(dailyLoads, 7)
        val acuteLoad = acuteLoadSeries.lastOrNull() ?: 0f

        val cappedAcuteLoadSeries = calculateRollingSum(cappedDailyLoads, 7)
        val chronicLoad = calculateEwma(cappedAcuteLoadSeries, 28).lastOrNull() ?: 0f
        // -------------------------------------------------------------

        // --- New ACWR Calculation ---
        val acwrAssessment = if (chronicLoad > 0) {
            val ratio = acuteLoad / chronicLoad
            when {
                ratio > 2.0f -> AcwrAssessment(AcwrRiskLevel.HIGH_OVERTRAINING, ratio, "High risk of overtraining.")
                ratio > 1.3f -> AcwrAssessment(AcwrRiskLevel.MODERATE_OVERTRAINING, ratio, "Moderate risk of overtraining.")
                ratio > 0.8f -> AcwrAssessment(AcwrRiskLevel.OPTIMAL, ratio, "Optimal training load.")
                else -> AcwrAssessment(AcwrRiskLevel.UNDERTRAINING, ratio, "Risk of undertraining.")
            }
        } else {
            null
        }
        // --------------------------------

        // --- Existing Risk Assessment Logic ---
        val mostRecentRun = mergedRuns.first()
        val allPrecedingRuns = mergedRuns.drop(1)

        val thirtyDaysBeforeMostRecent = OffsetDateTime.parse(mostRecentRun.startDateLocal).toLocalDate().minusDays(30)
        val relevantPrecedingRuns = allPrecedingRuns.filter {
            OffsetDateTime.parse(it.startDateLocal).toLocalDate().isAfter(thirtyDaysBeforeMostRecent)
        }
        val stableBaseline = getStableLongestRun(relevantPrecedingRuns)
        val riskAssessment = calculateRisk(mostRecentRun.distance, stableBaseline)

        val safeLongestRunForDisplay = if (mostRecentRun.distance > stableBaseline * 1.1f && stableBaseline > 0) {
            stableBaseline * 1.1f
        } else if (mostRecentRun.distance > stableBaseline && mostRecentRun.distance < stableBaseline * 1.1f) {
            mostRecentRun.distance
        } else {
            stableBaseline
        }
        
        // --- New Prescriptive Metrics ---
        val recommendedTodaysRun = max(0f, min(safeLongestRunForDisplay * 1.1f, chronicLoad * 1.3f - acuteLoad))
        val todaysLoad = dailyLoads.lastOrNull() ?: 0f
        val maxWeeklyLoad = max(0f, chronicLoad * 1.3f - todaysLoad)

        return RunAnalysis(safeLongestRunForDisplay, acuteLoad, chronicLoad, riskAssessment, acwrAssessment, recommendedTodaysRun, maxWeeklyLoad)
    }

    private fun createDailyLoadSeries(runs: List<Run>, startDate: LocalDate, endDate: LocalDate): List<Float> {
        val dailyLoadMap = runs.groupBy { OffsetDateTime.parse(it.startDateLocal).toLocalDate() }
            .mapValues { (_, runsOnDay) -> runsOnDay.sumOf { it.distance.toDouble() }.toFloat() }

        return (0..ChronoUnit.DAYS.between(startDate, endDate)).map {
            val date = startDate.plusDays(it)
            dailyLoadMap[date] ?: 0f
        }
    }

    private fun getIqrCapValue(loads: List<Float>): Float {
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

    private fun calculateRollingSum(data: List<Float>, window: Int): List<Float> {
        val result = mutableListOf<Float>()
        for (i in data.indices) {
            val start = max(0, i - window + 1)
            val windowSlice = data.subList(start, i + 1)
            result.add(windowSlice.sum())
        }
        return result
    }

    private fun calculateEwma(data: List<Float>, span: Int): List<Float> {
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

    private fun calculateRisk(distance: Float, baseline: Float): RiskAssessment {
        if (baseline <= 0f) {
            return RiskAssessment(RiskLevel.NONE, "No baseline to compare against.")
        }
        val increasePercentage = (distance - baseline) / baseline
        return when {
            increasePercentage > 1.0 -> RiskAssessment(RiskLevel.VERY_HIGH, "This run was more than double your recent longest run.")
            increasePercentage > 0.3 -> RiskAssessment(RiskLevel.HIGH, "This run was over 30% longer than your recent longest run.")
            increasePercentage > 0.1 -> RiskAssessment(RiskLevel.MODERATE, "This run was over 10% longer than your recent longest run.")
            else -> RiskAssessment(RiskLevel.NONE, "This run was within a safe range of your recent longest run.")
        }
    }

    private fun mergeRuns(runs: List<Run>): List<Run> {
        val sortedRuns = runs.reversed()
        val mergedRuns = mutableListOf<Run>()
        for (run in sortedRuns) {
            if (mergedRuns.isEmpty()) {
                mergedRuns.add(run)
            } else {
                val lastMergedRun = mergedRuns.last()
                val hoursBetween = Duration.between(OffsetDateTime.parse(lastMergedRun.startDateLocal), OffsetDateTime.parse(run.startDateLocal)).toHours()
                if (hoursBetween < 2) {
                    mergedRuns[mergedRuns.size - 1] = lastMergedRun.copy(distance = lastMergedRun.distance + run.distance, movingTime = lastMergedRun.movingTime + run.movingTime)
                } else {
                    mergedRuns.add(run)
                }
            }
        }
        return mergedRuns.reversed()
    }

    private fun getStableLongestRun(runs: List<Run>): Float {
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