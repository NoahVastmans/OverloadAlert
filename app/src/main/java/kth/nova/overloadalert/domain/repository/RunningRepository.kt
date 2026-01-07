package kth.nova.overloadalert.domain.repository

import kth.nova.overloadalert.data.local.Run
import kotlinx.coroutines.flow.Flow

/**
 * Interface defining the operations for accessing and managing run data.
 * This belongs in the Domain layer.
 */
interface RunningRepository {
    fun getAllRuns(): Flow<List<Run>>
    suspend fun syncRuns(): Result<Boolean>
}
