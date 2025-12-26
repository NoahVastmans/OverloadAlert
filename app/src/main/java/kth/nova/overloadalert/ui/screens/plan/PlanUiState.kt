package kth.nova.overloadalert.ui.screens.plan

import kth.nova.overloadalert.domain.plan.WeeklyTrainingPlan

/**
 * Represents the state of the Plan screen.
 */
data class PlanUiState(
    val isLoading: Boolean = true,
    val trainingPlan: WeeklyTrainingPlan? = null
)