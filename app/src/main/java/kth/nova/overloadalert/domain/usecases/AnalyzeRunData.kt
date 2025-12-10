package kth.nova.overloadalert.domain.usecases

import kotlinx.coroutines.currentCoroutineContext
import kth.nova.overloadalert.data.local.Run
import kth.nova.overloadalert.domain.model.AnalyzedRun
import kth.nova.overloadalert.domain.model.RiskAssessment
import kth.nova.overloadalert.domain.model.RiskLevel
import kth.nova.overloadalert.domain.model.RunAnalysis
import java.time.Duration
import java.time.LocalDate
import java.time.OffsetDateTime

class AnalyzeRunData {

    /**
     * Analyzes the provided runs to produce a summary and risk assessment for the home screen.
     */
    operator fun invoke(runs: List<Run>): RunAnalysis {
        val mergedRuns = mergeRuns(runs)

        if (mergedRuns.isEmpty()) {
            return RunAnalysis(0f, 0f, 0f, null)
        }

        val mostRecentRun = mergedRuns.first()
        val allPrecedingRuns = mergedRuns.drop(1)

        // --- Risk Assessment Logic ---
        val thirtyDaysBeforeMostRecent = OffsetDateTime.parse(mostRecentRun.startDateLocal).toLocalDate().minusDays(30)
        val relevantPrecedingRuns = allPrecedingRuns.filter {
            OffsetDateTime.parse(it.startDateLocal).toLocalDate().isAfter(thirtyDaysBeforeMostRecent)
        }
        val stableBaseline = getStableLongestRun(relevantPrecedingRuns)
        val riskAssessment = calculateRisk(mostRecentRun.distance, stableBaseline)

        // --- Value for UI Display ---
        val safeLongestRunForDisplay = if (mostRecentRun.distance > stableBaseline * 1.1f) {
            stableBaseline * 1.1f
        } else if (mostRecentRun.distance > stableBaseline && mostRecentRun.distance < stableBaseline * 1.1f) {
            mostRecentRun.distance
        } else {
            stableBaseline
        }

        // --- Standard Metrics Calculation (based on today's date) ---
        val today = LocalDate.now()
        val thirtyDaysAgo = today.minusDays(30)
        val currentRunsForMetrics = mergedRuns.filter { OffsetDateTime.parse(it.startDateLocal).toLocalDate().isAfter(thirtyDaysAgo) }
        
        val weeklyVolumes = (1..4).map { weekNumber ->
            val startOfWeek = today.minusDays((weekNumber * 7) - 1L)
            val endOfWeek = today.minusDays((weekNumber - 1) * 7L)
            currentRunsForMetrics.filter { run ->
                val runDate = OffsetDateTime.parse(run.startDateLocal).toLocalDate()
                !runDate.isBefore(startOfWeek) && !runDate.isAfter(endOfWeek)
            }.sumOf { it.distance.toDouble() }.toFloat()
        }
        val acuteLoad = if (weeklyVolumes.isNotEmpty()) weeklyVolumes[0] else 0f
        val chronicLoad = if (weeklyVolumes.size > 1) weeklyVolumes.subList(1, weeklyVolumes.size).average().toFloat() else 0f

        return RunAnalysis(safeLongestRunForDisplay, acuteLoad, chronicLoad, riskAssessment)
    }

    /**
     * Analyzes the full history of runs, providing a risk assessment for each one.
     */
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