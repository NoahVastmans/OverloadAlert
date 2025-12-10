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

    fun getRunsForAnalysis(): Flow<List<Run>> {
        val sixtyDaysAgoDate = LocalDate.now().minusDays(60)
        return runDao.getRunsSince(sixtyDaysAgoDate.toString())
    }

    suspend fun syncRuns(): Result<Unit> {
        return try {
            val localRunsSnapshot = getRunsForAnalysis().first()
            val daysToFetch = if (localRunsSnapshot.isEmpty()) 60L else 5L
            val fetchSinceDate = LocalDate.now().minusDays(daysToFetch)

            val nowEpoch = LocalDate.now().plusDays(1).atStartOfDay().toEpochSecond(ZoneOffset.UTC)
            val fetchSinceEpoch = fetchSinceDate.atStartOfDay().toEpochSecond(ZoneOffset.UTC)

            val remoteActivities = stravaApiService.getActivities(
                before = nowEpoch,
                after = fetchSinceEpoch,
                perPage = 200
            )

            // --- New Deletion Logic ---
            val remoteIds = remoteActivities.map { it.id }.toSet()
            val localRunsToConsider = localRunsSnapshot.filter {
                OffsetDateTime.parse(it.startDateLocal).toLocalDate().isAfter(fetchSinceDate)
            }
            val runsToDelete = localRunsToConsider.filter { it.id !in remoteIds }
            if (runsToDelete.isNotEmpty()) {
                runDao.deleteRuns(runsToDelete)
            }
            // --- End Deletion Logic ---

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

    fun getAllRuns(): Flow<List<Run>> {
        return runDao.getAllRuns()
    }
}