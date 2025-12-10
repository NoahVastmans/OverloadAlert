package kth.nova.overloadalert.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kth.nova.overloadalert.OverloadAlertApplication
import kth.nova.overloadalert.domain.model.RiskLevel
import kth.nova.overloadalert.util.NotificationHelper
import kotlinx.coroutines.flow.first

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val appComponent = (applicationContext as OverloadAlertApplication).appComponent
        val runningRepository = appComponent.runningRepository
        val analyzeRunData = appComponent.analyzeRunData
        val notificationHelper = NotificationHelper(applicationContext)

        return try {
            // 1. Get a snapshot of runs before the sync
            val runsBeforeSync = runningRepository.getAllRuns().first().map { it.id }.toSet()

            // 2. Perform the sync
            val syncResult = runningRepository.syncRuns()

            if (syncResult.isSuccess) {
                // 3. Get a snapshot of runs after the sync
                val runsAfterSync = runningRepository.getAllRuns().first()

                // 4. Identify which runs are new
                val newRuns = runsAfterSync.filter { it.id !in runsBeforeSync }

                if (newRuns.isNotEmpty()) {
                    // 5. Analyze the full history to get risk for all runs
                    val allAnalyzedRuns = analyzeRunData.analyzeFullHistory(runsAfterSync)

                    // 6. Find the highest risk among the NEW runs
                    val highestRiskNewRun = allAnalyzedRuns
                        .filter { analyzedRun -> analyzedRun.run.id in newRuns.map { it.id } }
                        .maxByOrNull { it.riskAssessment.riskLevel.ordinal }

                    highestRiskNewRun?.let {
                        val risk = it.riskAssessment.riskLevel
                        // 7. Send a notification if any new run is high or very high risk
                        if (risk == RiskLevel.HIGH || risk == RiskLevel.VERY_HIGH) {
                            notificationHelper.createNotificationChannel()
                            notificationHelper.showHighRiskNotification(
                                it.riskAssessment.message
                            )
                        }
                    }
                }
                Result.success()
            } else {
                Result.failure()
            }
        } catch (e: Exception) {
            Result.failure()
        }
    }
}