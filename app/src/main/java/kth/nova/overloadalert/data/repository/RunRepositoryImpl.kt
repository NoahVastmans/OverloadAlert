package kth.nova.overloadalert.data.repository

import kth.nova.overloadalert.data.local.Run
import kth.nova.overloadalert.data.local.RunDao
import kth.nova.overloadalert.data.remote.StravaApi
import kth.nova.overloadalert.data.remote.dto.toRun
import kth.nova.overloadalert.domain.repository.RunRepository
import kotlinx.coroutines.flow.Flow
import java.time.OffsetDateTime
import javax.inject.Inject

class RunRepositoryImpl @Inject constructor(
    private val stravaApi: StravaApi,
    private val runDao: RunDao
) : RunRepository {

    override fun getRuns(startDate: OffsetDateTime, endDate: OffsetDateTime): Flow<List<Run>> {
        return runDao.getRuns(startDate, endDate)
    }

    override suspend fun syncRuns(startDate: OffsetDateTime, endDate: OffsetDateTime) {
        try {
            val remoteRuns = stravaApi.getActivities(
                after = startDate.toEpochSecond(),
                before = endDate.toEpochSecond()
            )
            runDao.insertAll(remoteRuns.map { it.toRun() })
        } catch (e: Exception) {
            // In a real app, you'd want to handle this error more gracefully,
            // maybe by logging it or showing a message to the user.
            e.printStackTrace()
        }
    }
}