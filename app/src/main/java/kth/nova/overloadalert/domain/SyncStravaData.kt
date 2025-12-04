package kth.nova.overloadalert.domain

import kth.nova.overloadalert.domain.repository.RunRepository
import java.time.OffsetDateTime
import javax.inject.Inject

class SyncStravaData @Inject constructor(
    private val runRepository: RunRepository
) {
    suspend operator fun invoke() {
        val endDate = OffsetDateTime.now()
        val startDate = endDate.minusDays(30)
        runRepository.syncRuns(startDate, endDate)
    }
}