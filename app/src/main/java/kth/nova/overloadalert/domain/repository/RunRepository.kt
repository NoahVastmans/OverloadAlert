package kth.nova.overloadalert.domain.repository

import kth.nova.overloadalert.data.local.Run
import kotlinx.coroutines.flow.Flow
import java.time.OffsetDateTime

interface RunRepository {

    fun getRuns(startDate: OffsetDateTime, endDate: OffsetDateTime): Flow<List<Run>>

    suspend fun syncRuns(startDate: OffsetDateTime, endDate: OffsetDateTime)
}