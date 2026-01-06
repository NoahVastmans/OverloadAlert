package kth.nova.overloadalert.domain.repository

import android.util.Log
import kth.nova.overloadalert.data.RunningRepository
import kth.nova.overloadalert.data.local.AnalysisStorage
import kth.nova.overloadalert.domain.model.AnalysisKey
import kth.nova.overloadalert.domain.model.CachedAnalysis
import kth.nova.overloadalert.domain.model.UiAnalysisData
import kth.nova.overloadalert.domain.usecases.AnalyzeRunData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kth.nova.overloadalert.domain.usecases.AnalysisMode
import java.time.LocalDate

class AnalysisRepository(
    private val runningRepository: RunningRepository,
    private val analyzeRunData: AnalyzeRunData,
    private val analysisStorage: AnalysisStorage,
    coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {

    private val _latestCachedAnalysis: MutableStateFlow<CachedAnalysis?> = MutableStateFlow(analysisStorage.load())
    val latestCachedAnalysis: StateFlow<CachedAnalysis?> = _latestCachedAnalysis.asStateFlow()

    val latestAnalysis: StateFlow<UiAnalysisData?> = runningRepository.getAllRuns()
        .map { runs -> AnalysisKey(runs.hashCode()) }
        .distinctUntilChanged()
        .flatMapLatest { key ->
            val cached = _latestCachedAnalysis.value
            val today = LocalDate.now()

            val isStale = cached == null ||
                          key.runIdsHash != cached.lastRunHash ||
                          cached.cacheDate.isBefore(today)

            if (isStale) {
                Log.d("AnalysisRepository", "Analysis cache is stale. Recalculating...")
                val runs = runningRepository.getAllRuns().first()
                val overlapDate = today.minusDays(5) // Recalculate enough history for chronic load
                val updatedCache = analyzeRunData.updateAnalysisFrom(cached, runs, overlapDate, AnalysisMode.PERSISTENT)
                analysisStorage.save(updatedCache)
                _latestCachedAnalysis.value = updatedCache
            }
            
            // This flow now reacts to updates to the cached analysis
            latestCachedAnalysis.map { finalCache ->
                if (finalCache != null) {
                    analyzeRunData.deriveUiDataFromCache(finalCache, today)
                } else {
                    null
                }
            }
        }
        .stateIn(
            scope = coroutineScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = _latestCachedAnalysis.value?.let { analyzeRunData.deriveUiDataFromCache(it, LocalDate.now()) }
        )
}