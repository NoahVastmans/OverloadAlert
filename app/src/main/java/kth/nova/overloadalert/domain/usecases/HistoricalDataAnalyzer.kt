package kth.nova.overloadalert.domain.usecases

import kth.nova.overloadalert.data.local.Run
import kth.nova.overloadalert.domain.plan.HistoricalData
import java.time.DayOfWeek
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.roundToInt

class HistoricalDataAnalyzer {

    operator fun invoke(runs: List<Run>): HistoricalData {
        if (runs.size < 8) { // Need at least ~2 weeks of consistent data
            return HistoricalData() // Return default values if not enough data
        }

        val sortedRuns = runs.sortedBy { OffsetDateTime.parse(it.startDateLocal) }
        val firstRunDate = OffsetDateTime.parse(sortedRuns.first().startDateLocal).toLocalDate()
        val lastRunDate = OffsetDateTime.parse(sortedRuns.last().startDateLocal).toLocalDate()
        val totalDays = ChronoUnit.DAYS.between(firstRunDate, lastRunDate) + 1
        val totalWeeks = max(1.0, totalDays / 7.0)

        val runsByDayOfWeek = sortedRuns.groupBy { OffsetDateTime.parse(it.startDateLocal).dayOfWeek }

        // 1. Determine Typical Run Days and Weekly Frequency
        val dayFrequency = runsByDayOfWeek.mapValues { it.value.size }
        val averageRunsPerWeek = runs.size / totalWeeks

        // A day is "typical" if the user runs on it for at least 50% of the weeks.
        val typicalRunDays = dayFrequency.filter { (_, count) -> count.toDouble() / totalWeeks >= 0.5 }.keys
        
        // Use the higher of the two metrics, but keep it within a reasonable range.
        val typicalRunsPerWeek = max(typicalRunDays.size, averageRunsPerWeek.roundToInt()).coerceIn(2, 7) // TODO think about what to do with several runs on 1 day

        // 2. Determine if there is a Clear Structure
        // A simple heuristic: more than 75% of runs happen on the "typical" days.
        val runsOnTypicalDays = typicalRunDays.sumOf { dayFrequency[it] ?: 0 }
        val hasClearStructure = (runsOnTypicalDays.toDouble() / runs.size) > 0.75

        // 3. Find Typical Long Run Day
        val typicalLongRunDay = runsByDayOfWeek
            .mapValues { (_, dayRuns) -> dayRuns.maxOfOrNull { it.distance } ?: 0f }
            .maxByOrNull { it.value }?.key

        return HistoricalData(
            hasClearWeeklyStructure = hasClearStructure,
            typicalRunDays = typicalRunDays,
            typicalLongRunDay = typicalLongRunDay,
            typicalRunsPerWeek = typicalRunsPerWeek
        )
    }
}