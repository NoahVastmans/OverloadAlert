package kth.nova.overloadalert.domain.usecases

import kth.nova.overloadalert.data.local.Run
import kth.nova.overloadalert.domain.plan.HistoricalData
import java.time.DayOfWeek
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Analyzes a list of historical running activities to extract patterns and structural habits.
 *
 * This use case processes raw [Run] data to determine a runner's typical weekly schedule,
 * including preferred running days, frequency, and the specific day usually dedicated to long runs.
 * It employs heuristics to decide if the user follows a structured training plan or runs irregularly.
 *
 * The analysis requires a minimum amount of data (currently 8 runs, approx. 2 weeks) to produce meaningful results.
 * If insufficient data is provided, default [HistoricalData] is returned.
 *
 * The analysis includes:
 * - **Typical Run Days:** Days where a run occurs in at least 50% of the recorded weeks.
 * - **Weekly Frequency:** An estimate of runs per week, derived from both average frequency and specific day consistency.
 * - **Structure Detection:** Determines if >75% of runs happen on "typical" days.
 * - **Long Run Day:** Identifies the day of the week historically associated with the longest distance.
 */
class HistoricalDataAnalyzer {

    operator fun invoke(runs: List<Run>): HistoricalData {
        // Require a minimum baseline of data to avoid skewed results from one or two atypical weeks.
        if (runs.size < 8) { // Need at least ~2 weeks of consistent data
            return HistoricalData() // Return default values if not enough data
        }
        
        // Merge runs that happened on the same day (e.g. morning + evening run) to analyze days, not sessions.
        val mergedRuns = mergeRuns(runs)

        val sortedRuns = mergedRuns.sortedBy { OffsetDateTime.parse(it.startDateLocal) }
        val firstRunDate = OffsetDateTime.parse(sortedRuns.first().startDateLocal).toLocalDate()
        val lastRunDate = OffsetDateTime.parse(sortedRuns.last().startDateLocal).toLocalDate()
        
        // Calculate the exact duration of the history in weeks to normalize frequency.
        val totalDays = ChronoUnit.DAYS.between(firstRunDate, lastRunDate) + 1
        val totalWeeks = max(1.0, totalDays / 7.0)

        // Group all runs by their day of the week (MONDAY..SUNDAY)
        val runsByDayOfWeek = sortedRuns.groupBy { OffsetDateTime.parse(it.startDateLocal).dayOfWeek }

        // 1. Determine Typical Run Days and Weekly Frequency
        val dayFrequency = runsByDayOfWeek.mapValues { it.value.size }
        val averageRunsPerWeek = runs.size / totalWeeks

        // Identify "Typical" Days:
        // A day is considered typical if the user runs on it for at least 50% of the total weeks.
        // e.g., if history is 4 weeks and they ran on Monday 3 times, Monday is typical.
        val typicalRunDays = dayFrequency.filter { (_, count) -> count.toDouble() / totalWeeks >= 0.5 }.keys
        
        // Estimate Weekly Frequency:
        // We take the higher of two values:
        // - The number of specific "typical" days found.
        // - The raw average runs per week (rounded).
        // This handles cases where a user runs consistently 3 times a week but on random days (0 typical days, but avg 3).
        // We clamp the result between 2 and 7 to ensure a valid plan can be generated.
        val typicalRunsPerWeek = max(typicalRunDays.size, averageRunsPerWeek.roundToInt()).coerceIn(2, 7)

        // 2. Determine if there is a Clear Structure
        // Heuristic: Does the user stick to their "typical" days?
        // If more than 75% of all runs occurred on the identified typical days, we assume a structured habit.
        // This helps the planner decide whether to strictly follow history or just use volume.
        val runsOnTypicalDays = typicalRunDays.sumOf { dayFrequency[it] ?: 0 }
        val hasClearStructure = (runsOnTypicalDays.toDouble() / runs.size) > 0.75

        // 3. Find Typical Long Run Day
        // Identify the day of the week that has the highest *maximum* distance recorded.
        // This assumes the user consistently does their longest run on a specific day (e.g., Sunday).
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
