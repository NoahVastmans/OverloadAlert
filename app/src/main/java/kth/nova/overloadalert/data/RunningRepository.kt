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
     * Fetches runs for analysis with an optimized refresh strategy.
     * If the local database is empty, it performs a full 60-day sync.
     * For subsequent refreshes (forced or stale), it only fetches the last 5 days.
     */
    suspend fun getRunsForAnalysis(forceRefresh: Boolean = false): List<Run> {
        val sixtyDaysAgoDate = LocalDate.now().minusDays(60)
        val sixtyDaysAgoString = sixtyDaysAgoDate.toString()

        val localRuns = runDao.getRunsSince(sixtyDaysAgoString)

        val lastSync = tokenManager.getLastSyncTimestamp()
        val isCacheStale = (System.currentTimeMillis() - lastSync) > 3600 * 1000 // 1 hour

        val shouldSync = forceRefresh || localRuns.isEmpty() || isCacheStale

        if (shouldSync) {
            // Determine the date range for the sync
            val daysToFetch = if (localRuns.isEmpty()) 60L else 5L
            val fetchSinceDate = LocalDate.now().minusDays(daysToFetch)

            val nowEpoch = LocalDate.now().plusDays(1).atStartOfDay().toEpochSecond(ZoneOffset.UTC)
            val fetchSinceEpoch = fetchSinceDate.atStartOfDay().toEpochSecond(ZoneOffset.UTC)

            val remoteActivities = stravaApiService.getActivities(
                before = nowEpoch,
                after = fetchSinceEpoch,
                perPage = 200 // Increase page size to accommodate more data
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
            
            // After syncing, return the fresh data for the last 60 days from the local DB
            return runDao.getRunsSince(sixtyDaysAgoString)
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