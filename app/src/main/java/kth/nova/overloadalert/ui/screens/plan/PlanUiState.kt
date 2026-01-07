package kth.nova.overloadalert.ui.screens.plan

import kth.nova.overloadalert.domain.plan.WeeklyTrainingPlan

/**
 * Represents the UI state of the Plan screen.
 *
 * This state holder encapsulates all data necessary to render the user's weekly training plan interface,
 * including loading status, the plan data itself, and external service connection status.
 *
 * @property isLoading Indicates whether the training plan data is currently being fetched or processed.
 * @property trainingPlan The [WeeklyTrainingPlan] containing the schedule and details for the current week, or null if not yet loaded.
 * @property isGoogleConnected Indicates if the user has an active connection to Google services, used to control the visibility or state of synchronization controls.
 */
data class PlanUiState(
    val isLoading: Boolean = true,
    val trainingPlan: WeeklyTrainingPlan? = null,
    val isGoogleConnected: Boolean = false // Added for sync button logic
)