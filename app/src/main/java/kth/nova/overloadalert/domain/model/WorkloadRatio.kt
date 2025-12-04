package kth.nova.overloadalert.domain.model

data class WorkloadRatio(
    val ratio: Float,
    val riskLevel: RiskLevel
) {
    enum class RiskLevel {
        LOW, OPTIMAL, HIGH, VERY_HIGH
    }
}