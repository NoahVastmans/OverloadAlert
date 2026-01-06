package kth.nova.overloadalert.worker

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import kth.nova.overloadalert.data.RunningRepository
import kth.nova.overloadalert.domain.repository.AnalysisRepository

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