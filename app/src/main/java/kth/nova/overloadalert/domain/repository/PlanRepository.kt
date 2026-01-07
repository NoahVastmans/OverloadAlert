package kth.nova.overloadalert.domain.repository

import kth.nova.overloadalert.domain.plan.WeeklyTrainingPlan
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface defining the operations for managing the weekly training plan.
 * This belongs in the Domain layer.
 */
interface PlanRepository {
    val latestPlan: StateFlow<WeeklyTrainingPlan?>
    suspend fun syncCalendar()
}
