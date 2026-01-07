package kth.nova.overloadalert.domain.plan

import java.time.DayOfWeek

/**
 * Represents long-term patterns derived from the user's run history.
 *
 * This data class encapsulates statistical insights about a user's running habits,
 * serving as a baseline for generating future training plans or detecting anomalies.
 *
 * @property hasClearWeeklyStructure Indicates if the user adheres to a consistent weekly schedule.
 * @property typicalRunDays A set of [DayOfWeek] representing the days the user usually runs.
 * @property typicalLongRunDay The specific [DayOfWeek] usually dedicated to the longest run of the week, or null if no pattern exists.
 * @property typicalRunsPerWeek The average or median number of runs performed per week (defaults to 3).
 */
data class HistoricalData(
    val hasClearWeeklyStructure: Boolean = false,
    val typicalRunDays: Set<DayOfWeek> = emptySet(),
    val typicalLongRunDay: DayOfWeek? = null,
    val typicalRunsPerWeek: Int = 3
)