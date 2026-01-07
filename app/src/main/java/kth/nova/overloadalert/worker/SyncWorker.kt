package kth.nova.overloadalert.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kth.nova.overloadalert.data.RunningRepository
import kth.nova.overloadalert.domain.repository.AnalysisRepository

/**
 * A [CoroutineWorker] responsible for executing background synchronization of run data.
 *
 * This worker performs the following tasks:
 * 1. Initializes the notification channel.
 * 2. Triggers a synchronization of run data via the [RunningRepository].
 * 3. Observes the latest analysis data from the [AnalysisRepository] if the synchronization resulted in data changes.
 * 4. Dispatches appropriate notifications based on the calculated risk level (e.g., encouragement for "Optimal" states, warnings for high overload risks).
 *
 * The worker suppresses notifications if the analysis indicates "No Data" to prevent confusion during initial data loads.
 *
 * @property runningRepository The repository used to sync run data from external sources.
 * @property analysisRepository The repository used to fetch the latest risk analysis based on the synced data.
 */
class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
    private val runningRepository: RunningRepository,
    private val analysisRepository: AnalysisRepository
) : CoroutineWorker(appContext, workerParams) {

    private val notificationHelper = NotificationHelper(appContext)

    override suspend fun doWork(): Result {
        return try {
            notificationHelper.createNotificationChannel() // Ensure channel is created
            
            val syncResult = runningRepository.syncRuns()
            val dataWasChanged = syncResult.getOrNull() ?: false

            // Only send a notification if the sync was successful AND data actually changed.
            if (dataWasChanged) {
                val analysis = analysisRepository.latestAnalysis.filterNotNull().first()
                analysis.runAnalysis?.combinedRisk?.let { risk ->
                    when (risk.title) {
                        "No Data" -> {
                            // Do nothing. The analysis might be lagging behind the sync (due to debounce).
                            // Suppress this notification to avoid confusing the user during first connect.
                        }
                        "Optimal", "De-training/Recovery" -> {
                            notificationHelper.showEncouragementNotification(
                                risk.title,
                                risk.message
                            )
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