package kth.nova.overloadalert.domain.usecases

import kth.nova.overloadalert.data.local.Run
import kth.nova.overloadalert.domain.model.RiskAssessment
import kth.nova.overloadalert.domain.model.RiskLevel
import kth.nova.overloadalert.domain.model.RunAnalysis
import java.time.Duration
import java.time.LocalDate
import java.time.OffsetDateTime

class AnalyzeRunData {

    operator fun invoke(runs: List<Run>): RunAnalysis {
        // --- Run merging logic ---
        val mergedRuns = mergeRuns(runs)

        if (mergedRuns.isEmpty()) {
            return RunAnalysis(0f, 0f, 0f, null)
        }

        // --- Partition data into current and historical windows ---
        val today = LocalDate.now()
        val thirtyDaysAgo = today.minusDays(30)
        val sixtyDaysAgo = today.minusDays(60)

        val currentRuns = mergedRuns.filter { OffsetDateTime.parse(it.startDateLocal).toLocalDate().isAfter(thirtyDaysAgo) }
        val historicalRuns = mergedRuns.filter {
            val runDate = OffsetDateTime.parse(it.startDateLocal).toLocalDate()
            runDate.isAfter(sixtyDaysAgo) && !runDate.isAfter(thirtyDaysAgo)
        }

        // --- Calculate Stable Historical Baseline using IQR ---
        val historicalLongestRun = getStableLongestRun(historicalRuns)

        // --- Analyze Current Period ---
        val longestRunCurrentPeriod = currentRuns.maxOfOrNull { it.distance } ?: 0f

        val effectiveLongestRun = if (longestRunCurrentPeriod > historicalLongestRun) {
            // Apply the 10% cap for future calculations
            (historicalLongestRun * 1.1f)
        } else {
            longestRunCurrentPeriod
        }

        // --- Standard Metrics Calculation ---
        val weeklyVolumes = (1..4).map { weekNumber ->
            val startOfWeek = today.minusDays((weekNumber * 7) - 1L)
            val endOfWeek = today.minusDays((weekNumber - 1) * 7L)
            currentRuns.filter { run ->
                val runDate = OffsetDateTime.parse(run.startDateLocal).toLocalDate()
                !runDate.isBefore(startOfWeek) && !runDate.isAfter(endOfWeek)
            }.sumOf { it.distance.toDouble() }.toFloat()
        }
        val acuteLoad = weeklyVolumes[0]
        val chronicLoad = if (weeklyVolumes.size > 1) {
            weeklyVolumes.subList(1, weeklyVolumes.size).average().toFloat()
        } else {
            0f
        }

        // --- Risk Assessment for the Most Recent Run ---
        val riskAssessment = if (currentRuns.size < 2) {
            RiskAssessment(RiskLevel.NONE, "Not enough data to assess risk. At least two runs are needed.")
        } else {
            val mostRecentRun = currentRuns.first()
            // The baseline is now the stable historical longest run, or the longest of the other current runs.
            val baseline = maxOf(historicalLongestRun, currentRuns.drop(1).maxOfOrNull { it.distance } ?: 0f)

            if (baseline > 0f) {
                val increasePercentage = (mostRecentRun.distance - baseline) / baseline
                when {
                    increasePercentage > 1.0 -> RiskAssessment(RiskLevel.VERY_HIGH, "This run was more than double your recent longest run.")
                    increasePercentage > 0.3 -> RiskAssessment(RiskLevel.HIGH, "This run was over 30% longer than your recent longest run.")
                    increasePercentage > 0.1 -> RiskAssessment(RiskLevel.MODERATE, "This run was over 10% longer than your recent longest run.")
                    else -> RiskAssessment(RiskLevel.NONE, "This run was within a safe range of your recent longest run.")
                }
            } else {
                RiskAssessment(RiskLevel.NONE, "First run in the new period. No baseline to compare against.")
            }
        }

        return RunAnalysis(
            longestRunLast30Days = effectiveLongestRun, // Use the capped value
            acuteLoad = acuteLoad,
            chronicLoad = chronicLoad,
            riskAssessment = riskAssessment
        )
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

        val upperFence = q3 + 1.5 * iqr

        val normalRuns = distances.filter { it <= upperFence }
        return normalRuns.maxOrNull() ?: 0f
    }
}