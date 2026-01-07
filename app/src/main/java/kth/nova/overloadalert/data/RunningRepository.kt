package kth.nova.overloadalert.data

import kth.nova.overloadalert.data.local.Run
import kth.nova.overloadalert.data.local.RunDao
import kth.nova.overloadalert.data.remote.StravaApiService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kth.nova.overloadalert.data.remote.StravaTokenManager
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * Repository responsible for managing running activity data.
 *
 * This class acts as a mediator between local storage (database via [RunDao]) and remote data sources
 * (Strava API via [StravaApiService]). It handles data synchronization logic, ensuring that the local
 * database mirrors the relevant running activities from the remote source.
 *
 * Key responsibilities:
 * - Providing access to locally stored runs as a reactive stream.
 * - Synchronizing local data with remote data, which involves fetching new activities, identifying
 *   discrepancies (missing or outdated entries), and performing necessary insertions or deletions.
 * - Managing sync timestamps via [kth.nova.overloadalert.data.remote.StravaTokenManager].
 *
 * @property runDao The Data Access Object for local database operations on runs.
 * @property stravaApiService The service interface for communicating with the Strava API.
 * @property kth.nova.overloadalert.data.remote.StravaTokenManager A utility for managing authentication tokens and sync metadata.
 */
class RunningRepository(
    private val runDao: RunDao,
    private val stravaApiService: StravaApiService,
    private val stravaTokenManager: StravaTokenManager
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
            
            stravaTokenManager.saveLastSyncTimestamp(System.currentTimeMillis())
            
            Result.success(dataChanged)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

}