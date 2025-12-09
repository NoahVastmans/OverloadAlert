package kth.nova.overloadalert.domain.usecases

import kth.nova.overloadalert.data.local.Run
import kth.nova.overloadalert.domain.model.AnalyzedRun
import kth.nova.overloadalert.domain.model.RiskAssessment
import kth.nova.overloadalert.domain.model.RiskLevel
import kth.nova.overloadalert.domain.model.RunAnalysis
import java.time.Duration
import java.time.LocalDate
import java.time.OffsetDateTime

class AnalyzeRunData {

    operator fun invoke(runs: List<Run>): RunAnalysis {
        val mergedRuns = mergeRuns(runs)

        if (mergedRuns.isEmpty()) {
            return RunAnalysis(0f, 0f, 0f, null)
        }

        val today = LocalDate.now()
        val thirtyDaysAgo = today.minusDays(30)
        val sixtyDaysAgo = today.minusDays(60)

        val currentRuns = mergedRuns.filter { OffsetDateTime.parse(it.startDateLocal).toLocalDate().isAfter(thirtyDaysAgo) }
        val historicalRuns = mergedRuns.filter {
            val runDate = OffsetDateTime.parse(it.startDateLocal).toLocalDate()
            runDate.isAfter(sixtyDaysAgo) && !runDate.isAfter(thirtyDaysAgo)
        }

        val historicalLongestRun = getStableLongestRun(historicalRuns)
        val longestRunCurrentPeriod = currentRuns.maxOfOrNull { it.distance } ?: 0f

        val effectiveLongestRun = if (longestRunCurrentPeriod > historicalLongestRun && historicalLongestRun > 0) {
            (historicalLongestRun * 1.1f)
        } else {
            longestRunCurrentPeriod
        }

        val weeklyVolumes = (1..4).map { weekNumber ->
            val startOfWeek = today.minusDays((weekNumber * 7) - 1L)
            val endOfWeek = today.minusDays((weekNumber - 1) * 7L)
            currentRuns.filter { run ->
                val runDate = OffsetDateTime.parse(run.startDateLocal).toLocalDate()
                !runDate.isBefore(startOfWeek) && !runDate.isAfter(endOfWeek)
            }.sumOf { it.distance.toDouble() }.toFloat()
        }
        val acuteLoad = if (weeklyVolumes.isNotEmpty()) weeklyVolumes[0] else 0f
        val chronicLoad = if (weeklyVolumes.size > 1) weeklyVolumes.subList(1, weeklyVolumes.size).average().toFloat() else 0f

        val riskAssessment = if (currentRuns.isEmpty()) {
            RiskAssessment(RiskLevel.NONE, "No runs in the current period to assess.")
        } else {
            val mostRecentRun = currentRuns.first()
            val baseline = maxOf(historicalLongestRun, currentRuns.drop(1).maxOfOrNull { it.distance } ?: 0f)
            calculateRisk(mostRecentRun.distance, baseline)
        }

        return RunAnalysis(effectiveLongestRun, acuteLoad, chronicLoad, riskAssessment)
    }

    fun analyzeFullHistory(allRuns: List<Run>): List<AnalyzedRun> {
        val mergedRuns = mergeRuns(allRuns)
        if (mergedRuns.isEmpty()) return emptyList()

        return mergedRuns.mapIndexed { index, run ->
            val precedingRuns = mergedRuns.subList(index + 1, mergedRuns.size)
            val runDate = OffsetDateTime.parse(run.startDateLocal).toLocalDate()
            val thirtyDaysBeforeRun = runDate.minusDays(30)

            val relevantPrecedingRuns = precedingRuns.filter {
                OffsetDateTime.parse(it.startDateLocal).toLocalDate().isAfter(thirtyDaysBeforeRun)
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

    /**
     * Calculates a stable longest run baseline using the 80th percentile.
     * This is more robust against outliers than a simple max() or an aggressive IQR.
     */
    private fun getStableLongestRun(runs: List<Run>): Float {
        if (runs.isEmpty()) {
            return 0f
        }

        val distances = runs.map { it.distance }.sorted()

        // Calculate the index for the 80th percentile
        val index = (distances.size * 0.8).toInt()

        // Make sure the index is within bounds
        val percentileIndex = if (index < distances.size) index else distances.size - 1

        return distances[percentileIndex]
    }
}