package kth.nova.overloadalert.data

import kth.nova.overloadalert.data.local.Run
import kth.nova.overloadalert.data.local.RunDao
import kth.nova.overloadalert.data.remote.StravaApiService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

class RunningRepository(
    private val runDao: RunDao,
    private val stravaApiService: StravaApiService,
    private val tokenManager: TokenManager
) {

    fun getAllRuns(): Flow<List<Run>> {
        return runDao.getAllRuns()
    }

    suspend fun syncRuns(): Result<Boolean> {
        return try {
            var dataChanged = false
            val localRunsSnapshot = getAllRuns().first()
            val daysToFetch = if (localRunsSnapshot.isEmpty()) 160L else 5L
            val fetchSinceDate = LocalDate.now().minusDays(daysToFetch)

            val nowEpoch = LocalDate.now().plusDays(1).atStartOfDay().toEpochSecond(ZoneOffset.UTC)
            val fetchSinceEpoch = fetchSinceDate.atStartOfDay().toEpochSecond(ZoneOffset.UTC)

            val remoteActivities = stravaApiService.getActivities(
                before = nowEpoch,
                after = fetchSinceEpoch,
                perPage = 200
            ).filter { it.type == "Run" }

            val remoteIds = remoteActivities.map { it.id }.toSet()
            
            // Only consider local runs within the fetched time window for deletion checks.
            val localRunsInWindow = localRunsSnapshot.filter {
                OffsetDateTime.parse(it.startDateLocal).toLocalDate().isAfter(fetchSinceDate.minusDays(1))
            }

            // 1. Find runs to delete (present in the local window but not remotely)
            val runsToDelete = localRunsInWindow.filter { it.id !in remoteIds }
            if (runsToDelete.isNotEmpty()) {
                runDao.deleteRuns(runsToDelete)
                dataChanged = true
            }

            // 2. Find runs to add (present remotely but not locally)
            val localIds = localRunsSnapshot.map { it.id }.toSet()
            val runsToAdd = remoteActivities.filter { it.id !in localIds }.map {
                Run(
                    id = it.id,
                    distance = it.distance,
                    startDateLocal = it.startDateLocal,
                    movingTime = it.movingTime
                )
            }
            if (runsToAdd.isNotEmpty()) {
                runDao.insertAll(runsToAdd)
                dataChanged = true
            }
            
            tokenManager.saveLastSyncTimestamp(System.currentTimeMillis())
            
            Result.success(dataChanged)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun clearAllRuns() {
        runDao.clearAll()
    }
}