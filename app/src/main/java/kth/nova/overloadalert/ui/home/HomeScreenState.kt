package kth.nova.overloadalert.ui.home

import kth.nova.overloadalert.domain.model.RunRecommendation
import kth.nova.overloadalert.domain.model.WorkloadRatio

data class HomeScreenState(
    val isLoading: Boolean = false,
    val runRecommendation: RunRecommendation? = null,
    val workloadRatio: WorkloadRatio? = null,
    val error: String? = null
)