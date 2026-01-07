package kth.nova.overloadalert.domain.repository

import android.util.Log
import kth.nova.overloadalert.data.RunningRepository
import kth.nova.overloadalert.data.local.AnalysisStorage
import kth.nova.overloadalert.domain.model.AnalysisKey
import kth.nova.overloadalert.domain.model.UiAnalysisData
import kth.nova.overloadalert.domain.usecases.AnalyzeRunData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kth.nova.overloadalert.domain.model.CachedAnalysis
import kth.nova.overloadalert.domain.usecases.AnalysisMode
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * Repository responsible for managing and providing access to run analysis data.
 *
 * This repository orchestrates the retrieval of raw run data, triggers the analysis logic
 * (calculating metrics like ACWR), handles caching of analysis results to persistent storage,
 * and exposes a reactive stream of the latest UI-ready analysis data.
 *
 * Key features:
 * - **Reactive Updates:** Exposes `latestAnalysis` as a `StateFlow` that automatically updates whenever the underlying run data changes.
 * - **Caching Strategy:** Maintains a `CachedAnalysis` object in local storage (`AnalysisStorage`). It checks for staleness based on run data hash codes and the current date.
 * - **Incremental Updates:** Uses `AnalyzeRunData` to perform incremental analysis updates when possible, rather than re-calculating everything from scratch, unless the cache is empty or invalid.
 * - **Staleness Logic:** Refreshes the analysis if:
 *      - No cache exists.
 *      - The cache is empty.
 *      - The hash of the current run data differs from the cached hash (indicating data changes).
 *      - The current date is later than the cache date (indicating a new day has started).
 *
 * @property runningRepository Source of truth for raw run data.
 * @property analyzeRunData Use case containing the core logic for calculating and updating analysis metrics.
 * @property analysisStorage Local storage mechanism for persisting the analysis cache.
 * @property coroutineScope The scope in which the analysis flow collection and computation operate.
 */
class AnalysisRepository(
    private val runningRepository: RunningRepository,
    private val analyzeRunData: AnalyzeRunData,
    private val analysisStorage: AnalysisStorage,
    coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {

    private val _latestCachedAnalysis: MutableStateFlow<CachedAnalysis?> = MutableStateFlow(analysisStorage.load())
    val latestCachedAnalysis: StateFlow<CachedAnalysis?> = _latestCachedAnalysis.asStateFlow()

    val latestAnalysis: StateFlow<UiAnalysisData?> = runningRepository.getAllRuns()
        .map { runs -> AnalysisKey(runs.hashCode()) } // Create a key from the current runs
        .distinctUntilChanged() // Only proceed if the runs have actually changed
        .flatMapLatest { key ->
            flow {
                val cached = _latestCachedAnalysis.value
                val today = LocalDate.now()

                val isCacheEmpty = cached?.acwrByDate.isNullOrEmpty()

                // Determine if the cache is stale by checking the hash OR if the day has changed.
                val isStale = cached == null ||
                              isCacheEmpty ||
                              key.runIdsHash != cached.lastRunHash ||
                              cached.cacheDate.isBefore(today)



                if (isStale) {
                    val runs = runningRepository.getAllRuns().first()

                    // If the cache is effectively empty, we treat it as null to force a full re-analysis of ALL runs.
                    // Otherwise, we do a standard incremental update.
                    val effectiveCache = if (isCacheEmpty) null else cached

                    val overlapDate = today.minusDays(5)
                    val updatedCache = analyzeRunData.updateAnalysisFrom(effectiveCache, runs, overlapDate, AnalysisMode.PERSISTENT)
                    analysisStorage.save(updatedCache)
                    _latestCachedAnalysis.value = updatedCache
                    emit(analyzeRunData.deriveUiDataFromCache(updatedCache, today))
                } else {
                    emit(analyzeRunData.deriveUiDataFromCache(cached, today))
                }
            }
        }
        .stateIn(
            scope = coroutineScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = _latestCachedAnalysis.value?.let { analyzeRunData.deriveUiDataFromCache(it, LocalDate.now()) }
        )
}