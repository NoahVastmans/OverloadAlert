package kth.nova.overloadalert.domain.plan

import com.squareup.moshi.JsonClass
import java.time.LocalDate

/**
 * Represents a persisted state used to enforce a multi-day training load reduction.
 *
 * This configuration allows the system to override standard load calculations for a specific
 * period, typically to manage injury risk or recovery.
 *
 * @property startDate The date when this override period begins.
 * @property phase An optional [RiskPhase] associated with this override (e.g., Return to Play), if applicable.
 * @property acwrMultiplier The multiplier applied to the Acute:Chronic Workload Ratio (ACWR) limit during this period.
 * @property longRunMultiplier The multiplier applied to long run distance limits during this period.
 */
@JsonClass(generateAdapter = true)
data class RiskOverride(
    val startDate: LocalDate,
    val phase: RiskPhase? = null,
    val acwrMultiplier: Float,
    val longRunMultiplier: Float
)