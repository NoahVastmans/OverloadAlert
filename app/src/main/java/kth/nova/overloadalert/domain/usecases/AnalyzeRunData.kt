package kth.nova.overloadalert.domain.usecases

import kth.nova.overloadalert.data.local.Run
import kth.nova.overloadalert.domain.model.RunAnalysis
import java.time.LocalDate
import java.time.OffsetDateTime

class AnalyzeRunData {

    operator fun invoke(runs: List<Run>): RunAnalysis {
        if (runs.isEmpty()) {
            return RunAnalysis(0f, 0f, 0f)
        }

        val longestRun = runs.maxOfOrNull { it.distance } ?: 0f

        val today = LocalDate.now()

        // Partition runs into weeks. Week 1 is the most recent 7 days.
        val weeklyVolumes = (1..4).map { weekNumber ->
            val startOfWeek = today.minusDays((weekNumber * 7) - 1L)
            val endOfWeek = today.minusDays((weekNumber - 1) * 7L)

            runs.filter { run ->
                // Correctly parse the full date-time string before converting to a LocalDate
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

        return RunAnalysis(
            longestRunLast30Days = longestRun,
            acuteLoad = acuteLoad,
            chronicLoad = chronicLoad
        )
    }
}