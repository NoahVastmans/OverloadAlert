package kth.nova.overloadalert.domain.plan

import com.squareup.moshi.JsonClass
import java.time.LocalDate

/**
 * Represents a persisted state to enforce a multi-day training load reduction.
 */
@JsonClass(generateAdapter = true)
data class RiskOverride(
    val startDate: LocalDate,
    val phase: RiskPhase? = null,
    val acwrMultiplier: Float,
    val longRunMultiplier: Float
)