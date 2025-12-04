package kth.nova.overloadalert.domain

import kth.nova.overloadalert.domain.model.RunRecommendation
import kth.nova.overloadalert.domain.repository.RunRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.OffsetDateTime
import javax.inject.Inject

class GetRunRecommendations @Inject constructor(
    private val runRepository: RunRepository
) {
    operator fun invoke(): Flow<RunRecommendation> {
        val endDate = OffsetDateTime.now()
        val startDate = endDate.minusDays(30)

        return runRepository.getRuns(startDate, endDate).map { runs ->
            val longestRun = runs.maxByOrNull { it.distance }?.distance ?: 0f

            // These are placeholder thresholds. We can adjust them later based on research.
            val todayRecommendation = longestRun * 0.5f
            val weeklyRecommendation = longestRun * 2.0f

            RunRecommendation(
                today = todayRecommendation,
                nextSevenDays = weeklyRecommendation
            )
        }
    }
}