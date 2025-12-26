package kth.nova.overloadalert.domain.plan

import java.time.DayOfWeek

/**
 * Represents long-term patterns derived from the user's run history.
 */
data class HistoricalData(
    val hasClearWeeklyStructure: Boolean = false,
    val typicalRunDays: Set<DayOfWeek> = emptySet(),
    val typicalLongRunDay: DayOfWeek? = null,
    val typicalRunsPerWeek: Int = 3
)