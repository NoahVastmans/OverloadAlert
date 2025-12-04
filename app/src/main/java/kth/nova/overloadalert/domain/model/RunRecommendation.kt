package kth.nova.overloadalert.domain.model

data class RunRecommendation(
    val today: Float,
    val nextSevenDays: Float
)