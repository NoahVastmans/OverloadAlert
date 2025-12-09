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
     * Fetches the runs for the last 30 days with an optimized refresh strategy.
     * If the local database is empty, it performs a full 30-day sync.
     * For subsequent refreshes (forced or stale), it only fetches the last 5 days.
     */
    suspend fun getRunsForLast30Days(forceRefresh: Boolean = false): List<Run> {
        val thirtyDaysAgoDate = LocalDate.now().minusDays(30)
        val thirtyDaysAgoString = thirtyDaysAgoDate.toString()

        val localRuns = runDao.getRunsSince(thirtyDaysAgoString)

        val lastSync = tokenManager.getLastSyncTimestamp()
        val isCacheStale = (System.currentTimeMillis() - lastSync) > 3600 * 1000 // 1 hour

        val shouldSync = forceRefresh || localRuns.isEmpty() || isCacheStale

        if (shouldSync) {
            // Determine the date range for the sync
            val daysToFetch = if (localRuns.isEmpty()) 30L else 5L
            val fetchSinceDate = LocalDate.now().minusDays(daysToFetch)

            val nowEpoch = LocalDate.now().plusDays(1).atStartOfDay().toEpochSecond(ZoneOffset.UTC)
            val fetchSinceEpoch = fetchSinceDate.atStartOfDay().toEpochSecond(ZoneOffset.UTC)

            val remoteActivities = stravaApiService.getActivities(
                before = nowEpoch,
                after = fetchSinceEpoch,
                perPage = 100
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

            // Insert or update the new runs into the database
            if (newRuns.isNotEmpty()) {
                runDao.insertAll(newRuns)
            }
            
            tokenManager.saveLastSyncTimestamp(System.currentTimeMillis())
            
            // After syncing, return the fresh data for the last 30 days from the local DB
            return runDao.getRunsSince(thirtyDaysAgoString)
        }

        // If no sync was needed, return the runs from the local cache
        return localRuns
    }

    /**
     * Gets all runs stored in the local database.
     */
    suspend fun getAllRuns(): List<Run> {
        return runDao.getAllRuns()
    }
}