package kth.nova.overloadalert.worker

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import kth.nova.overloadalert.domain.repository.RunningRepository
import kth.nova.overloadalert.domain.repository.AnalysisRepository

/**
 * A custom [WorkerFactory] responsible for creating instances of workers that require dependency injection.
 *
 * This factory is used to inject dependencies such as [RunningRepository] and [AnalysisRepository]
 * into workers (e.g., [SyncWorker]) which cannot be handled by the default no-argument worker factory.
 *
 * @property runningRepository The repository handling running session data.
 * @property analysisRepository The repository handling data analysis operations.
 */
class MyWorkerFactory(
    private val runningRepository: RunningRepository,
    private val analysisRepository: AnalysisRepository
) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? {
        return when (workerClassName) {
            SyncWorker::class.java.name ->
                SyncWorker(appContext, workerParameters, runningRepository, analysisRepository)
            else -> null
        }
    }
}