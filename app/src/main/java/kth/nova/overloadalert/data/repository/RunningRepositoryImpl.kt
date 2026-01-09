package kth.nova.overloadalert.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kth.nova.overloadalert.data.local.Run
import kth.nova.overloadalert.data.local.RunDao
import kth.nova.overloadalert.data.remote.StravaApiService
import kth.nova.overloadalert.data.remote.StravaTokenManager
import kth.nova.overloadalert.domain.repository.RunningRepository
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
 * - Managing sync timestamps via [StravaTokenManager].
 *
 * @property runDao The Data Access Object for local database operations on runs.
 * @property stravaApiService The service interface for communicating with the Strava API.
 * @property stravaTokenManager A utility for managing authentication tokens and sync metadata.
 */
class RunningRepositoryImpl(
    private val runDao: RunDao,
    private val stravaApiService: StravaApiService,
    private val stravaTokenManager: StravaTokenManager
) : RunningRepository {

    override fun getAllRuns(): Flow<List<Run>> {
        return runDao.getAllRuns()
    }

    override suspend fun syncRuns(): Result<Boolean> {
        return try {
            var dataChanged = false
            // Get current state of local database
            val localRunsSnapshot = getAllRuns().first()
            
            // Determine fetch window:
            // If DB is empty, fetch a long history (160 days) to bootstrap analysis.
            // If DB has data, only fetch recent days (5 days) to catch new runs or recent edits.
            val daysToFetch = if (localRunsSnapshot.isEmpty()) 120L else 5L
            val fetchSinceDate = LocalDate.now().minusDays(daysToFetch)

            val nowEpoch = LocalDate.now().plusDays(1).atStartOfDay().toEpochSecond(ZoneOffset.UTC)
            val fetchSinceEpoch = fetchSinceDate.atStartOfDay().toEpochSecond(ZoneOffset.UTC)

            // Fetch from Strava API
            val remoteActivities = stravaApiService.getActivities(
                before = nowEpoch,
                after = fetchSinceEpoch,
                perPage = 200
            ).filter { it.type == "Run" } // Only interested in Runs

            val remoteIds = remoteActivities.map { it.id }.toSet()
            
            // Only consider local runs within the fetched time window for deletion checks.
            // This prevents us from accidentally deleting old runs that weren't returned by the API query.
            val localRunsInWindow = localRunsSnapshot.filter {
                OffsetDateTime.parse(it.startDateLocal).toLocalDate().isAfter(fetchSinceDate.minusDays(1))
            }

            // 1. Detection Deletions:
            // If a run exists locally in the window but is NOT in the remote list, 
            // it means the user deleted it on Strava. We should delete it locally.
            val runsToDelete = localRunsInWindow.filter { it.id !in remoteIds }
            if (runsToDelete.isNotEmpty()) {
                runDao.deleteRuns(runsToDelete)
                dataChanged = true
            }

            // 2. Detect Insertions:
            // If a run exists remotely but NOT locally, it's new. Insert it.
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
