package kth.nova.overloadalert.domain

import kth.nova.overloadalert.domain.model.WorkloadRatio
import kth.nova.overloadalert.domain.repository.RunRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.OffsetDateTime
import javax.inject.Inject

class CalculateWorkloadRatio @Inject constructor(
    private val runRepository: RunRepository
) {
    operator fun invoke(): Flow<WorkloadRatio> {
        val endDate = OffsetDateTime.now()
        val startDate = endDate.minusWeeks(4)

        return runRepository.getRuns(startDate, endDate).map { runs ->
            val weeklyDistances = (0..3).map { weekIndex ->
                val weekEnd = endDate.minusWeeks(weekIndex.toLong())
                val weekStart = weekEnd.minusWeeks(1)
                runs.filter { it.startDate.isAfter(weekStart) && it.startDate.isBefore(weekEnd) }
                    .sumOf { it.distance.toDouble() }.toFloat()
            }

            val acuteLoad = weeklyDistances.getOrElse(0) { 0f }
            val chronicLoad = weeklyDistances.drop(1).average().toFloat()

            val ratio = if (chronicLoad > 0) acuteLoad / chronicLoad else 0f

            val riskLevel = when {
                ratio < 0.8f -> WorkloadRatio.RiskLevel.LOW
                ratio <= 1.3f -> WorkloadRatio.RiskLevel.OPTIMAL
                ratio <= 1.5f -> WorkloadRatio.RiskLevel.HIGH
                else -> WorkloadRatio.RiskLevel.VERY_HIGH
            }

            WorkloadRatio(ratio = ratio, riskLevel = riskLevel)
        }
    }
}