package kth.nova.overloadalert.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kth.nova.overloadalert.data.RunningRepository
import kth.nova.overloadalert.domain.usecases.AnalyzeRunData
import kotlinx.coroutines.flow.first

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
    private val runningRepository: RunningRepository,
    private val analyzeRunData: AnalyzeRunData
) : CoroutineWorker(appContext, workerParams) {

    private val notificationHelper = NotificationHelper(appContext)

    override suspend fun doWork(): Result {
        return try {
            notificationHelper.createNotificationChannel() // Ensure channel is created
            
            val syncResult = runningRepository.syncRuns()
            val dataWasChanged = syncResult.getOrNull() ?: false

            // Only send a notification if the sync was successful AND data actually changed.
            if (dataWasChanged) {
                val allRuns = runningRepository.getAllRuns().first()
                if (allRuns.size > 1) {
                    val analysis = analyzeRunData(allRuns)

                    analysis.runAnalysis?.combinedRisk?.let { risk ->
                        when (risk.title) {
                            "Optimal", "De-training/Recovery" -> {
                                notificationHelper.showEncouragementNotification(risk.title, risk.message)
                            }
                            else -> {
                                // Any other title is considered a warning
                                notificationHelper.showWarningNotification(risk.title, risk.message)
                            }
                        }
                    }
                }
            }
            Result.success()
        } catch (e: Exception) {
            Log.e("SyncWorker", "Error during background sync", e)
            Result.failure()
        }
    }
}