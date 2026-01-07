package kth.nova.overloadalert.domain.plan

import com.squareup.moshi.JsonClass
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Represents a comprehensive training schedule spanning a single week (seven days).
 *
 * This aggregate root encapsulates the daily workout details, the overall risk assessment
 * for the week, and the configuration parameters used to generate the plan. It serves
 * as the primary output of the planning engine.
 *
 * @property startDate The date of the first day (Monday) of this training week. Can be null if the plan is a template.
 * @property days A list of [DailyPlan] objects detailing the specific workouts for each day of the week.
 * @property riskPhase The calculated injury risk phase (e.g., DELOAD, COOLDOWN, REBUILDING) associated with this week's volume/intensity.
 * @property progressionRate The rate at which training load is increased compared to the previous week.
 * @property runTypesStructure A map defining the intended type of run (e.g., Long Run, Moderate Run) for each specific day of the week.
 * @property userPreferences The specific user settings (e.g., days available to run) honored during the generation of this plan.
 * @property historicalRunsHash A hash of the user's run history at the time of generation, used to detect if the plan needs regeneration due to new data.
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