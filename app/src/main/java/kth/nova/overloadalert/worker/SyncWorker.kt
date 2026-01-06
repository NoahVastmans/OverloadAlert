package kth.nova.overloadalert.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kth.nova.overloadalert.data.RunningRepository
import kth.nova.overloadalert.domain.repository.AnalysisRepository
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import java.time.LocalDate

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
    private val runningRepository: RunningRepository,
    private val analysisRepository: AnalysisRepository // Changed dependency
) : CoroutineWorker(appContext, workerParams) {

    private val notificationHelper = NotificationHelper(appContext)

    override suspend fun doWork(): Result {
        return try {
            notificationHelper.createNotificationChannel() // Ensure channel is created
            
            val syncResult = runningRepository.syncRuns()
            val dataWasChanged = syncResult.getOrNull() ?: false

            // Only send a notification if the sync was successful AND data actually changed.
            if (dataWasChanged) {
                // The analysisRepository flow will trigger an efficient, cached update automatically.
                // We just need to get the latest result to decide on the notification.
                val analysis = analysisRepository.latestAnalysis.filterNotNull().first()

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
            Result.success()
        } catch (e: Exception) {
            Log.e("SyncWorker", "Error during background sync", e)
            Result.failure()
        }
    }
}