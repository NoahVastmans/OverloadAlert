package kth.nova.overloadalert.data

import kth.nova.overloadalert.data.local.Run
import kth.nova.overloadalert.data.local.RunDao
import kth.nova.overloadalert.data.remote.StravaApiService
import java.time.LocalDate
import java.time.ZoneOffset

class RunningRepository(
    private val runDao: RunDao,
    private val stravaApiService: StravaApiService
) {

    suspend fun getRunsForLast30Days(): List<Run> {
        val thirtyDaysAgoDate = LocalDate.now().minusDays(30)
        val thirtyDaysAgoString = thirtyDaysAgoDate.toString()

        val localRuns = runDao.getRunsSince(thirtyDaysAgoString)

        // If the local database is empty for the period, fetch from the remote API.
        // A more advanced implementation could check the age of the most recent data point.
        if (localRuns.isEmpty()) {
            val nowEpoch = LocalDate.now().plusDays(1).atStartOfDay().toEpochSecond(ZoneOffset.UTC)
            val thirtyDaysAgoEpoch = thirtyDaysAgoDate.atStartOfDay().toEpochSecond(ZoneOffset.UTC)

            // Let exceptions propagate to the ViewModel to be handled.
            val remoteActivities = stravaApiService.getActivities(
                before = nowEpoch,
                after = thirtyDaysAgoEpoch,
                perPage = 100 // Fetch a reasonable number of activities for the period
            )

            val newRuns = remoteActivities
                .filter { it.type == "Run" || it.type == "Walk" } // We only care about runs and walks
                .map {
                    Run(
                        id = it.id,
                        distance = it.distance,
                        startDateLocal = it.startDateLocal,
                        movingTime = it.movingTime
                    )
                }

            // Cache the result, even if it's an empty list (meaning no runs in the period).
            runDao.insertAll(newRuns)
            return newRuns
        }

        return localRuns
    }
}