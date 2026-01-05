package kth.nova.overloadalert.domain.repository

import android.util.Log
import kth.nova.overloadalert.data.RunningRepository
import kth.nova.overloadalert.data.local.AnalysisStorage
import kth.nova.overloadalert.domain.model.AnalysisKey
import kth.nova.overloadalert.domain.model.UiAnalysisData
import kth.nova.overloadalert.domain.usecases.AnalyzeRunData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

class AnalysisRepository(
    private val runningRepository: RunningRepository,
    private val analyzeRunData: AnalyzeRunData,
    private val analysisStorage: AnalysisStorage,
    coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {

    val latestAnalysis: StateFlow<UiAnalysisData?> = runningRepository.getAllRuns()
        .map { runs -> AnalysisKey(runs.hashCode()) } // Create a key from the current runs
        .distinctUntilChanged() // Only proceed if the runs have actually changed
        .flatMapLatest { key ->
            flow {
                val cached = analysisStorage.load()
                val today = LocalDate.now()

                // Determine if the cache is stale by checking the hash OR if the day has changed.
                val isStale = cached == null ||
                              key.runIdsHash != cached.lastRunHash ||
                              cached.cacheDate.isBefore(today)

                val runs = runningRepository.getAllRuns().first()

                if (isStale) {
                    Log.d("AnalysisRepository", "Analysis cache is stale. Recalculating...")
                    val overlapDate = today.minusDays(5)
                    val updatedCache = analyzeRunData.updateAnalysisFrom(cached, runs, overlapDate)
                    analysisStorage.save(updatedCache)
                    emit(analyzeRunData.deriveUiDataFromCache(updatedCache, today))
                } else {
                    Log.d("AnalysisRepository", "Analysis cache is up to date.")
                    emit(analyzeRunData.deriveUiDataFromCache(cached, today))
                }
            }
        }
        .stateIn(
            scope = coroutineScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )
}