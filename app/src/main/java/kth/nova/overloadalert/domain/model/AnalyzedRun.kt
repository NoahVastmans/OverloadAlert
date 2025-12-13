package kth.nova.overloadalert.domain.model

import kth.nova.overloadalert.data.local.Run

/**
 * A data class that pairs a run with its calculated risk assessment.
 */
data class AnalyzedRun(
    val run: Run,
    val singleRunRiskAssessment: SingleRunRiskAssessment
)