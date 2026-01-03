package kth.nova.overloadalert.domain.plan

import com.squareup.moshi.JsonClass
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Represents a full, seven-day training plan.
 */
@JsonClass(generateAdapter = true)
data class WeeklyTrainingPlan(
    val startDate: LocalDate? = null,
    val days: List<DailyPlan> = emptyList(),
    val riskPhase: RiskPhase? = null,
    val progressionRate: ProgressionRate,
    val runTypesStructure: Map<DayOfWeek, RunType> = emptyMap(),
    val userPreferences: UserPreferences? = null,
    val historicalRunsHash: Int? = null
)