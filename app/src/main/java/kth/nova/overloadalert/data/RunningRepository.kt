package kth.nova.overloadalert.data

import kth.nova.overloadalert.data.local.Run
import kth.nova.overloadalert.data.local.RunDao
import kth.nova.overloadalert.data.remote.StravaApiService
import java.time.LocalDate
import java.time.ZoneOffset

class RunningRepository(
    private val runDao: RunDao,
    private val stravaApiService: StravaApiService,
    private val tokenManager: TokenManager
) {

    /**
     * Fetches the runs for the last 60 days directly from the local database.
     */
    suspend fun getRunsForAnalysis(): List<Run> {
        val sixtyDaysAgoDate = LocalDate.now().minusDays(60)
        return runDao.getRunsSince(sixtyDaysAgoDate.toString())
    }

    /**
     * Syncs runs with the Strava API.
     * If the local database is empty, it performs a full 60-day sync.
     * For subsequent refreshes, it only fetches the last 5 days.
     * Returns a Result indicating success or failure.
     */
    suspend fun syncRuns(): Result<Unit> {
        return try {
            val localRuns = getRunsForAnalysis()
            val daysToFetch = if (localRuns.isEmpty()) 60L else 5L
            val fetchSinceDate = LocalDate.now().minusDays(daysToFetch)

            val nowEpoch = LocalDate.now().plusDays(1).atStartOfDay().toEpochSecond(ZoneOffset.UTC)
            val fetchSinceEpoch = fetchSinceDate.atStartOfDay().toEpochSecond(ZoneOffset.UTC)

            val remoteActivities = stravaApiService.getActivities(
                before = nowEpoch,
                after = fetchSinceEpoch,
                perPage = 200
            )

            val newRuns = remoteActivities
                .filter { it.type == "Run" || it.type == "Walk" }
                .map {
                    Run(
                        id = it.id,
                        distance = it.distance,
                        startDateLocal = it.startDateLocal,
                        movingTime = it.movingTime
                    )
                }

            if (newRuns.isNotEmpty()) {
                runDao.insertAll(newRuns)
            }
            
            tokenManager.saveLastSyncTimestamp(System.currentTimeMillis())
            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Gets all runs stored in the local database.
     */
    suspend fun getAllRuns(): List<Run> {
        return runDao.getAllRuns()
    }
}