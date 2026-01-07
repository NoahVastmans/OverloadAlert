package kth.nova.overloadalert.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kth.nova.overloadalert.data.local.AnalysisStorage
import kth.nova.overloadalert.domain.model.AnalysisKey
import kth.nova.overloadalert.domain.model.CachedAnalysis
import kth.nova.overloadalert.domain.model.UiAnalysisData
import kth.nova.overloadalert.domain.repository.AnalysisRepository
import kth.nova.overloadalert.domain.repository.RunningRepository
import kth.nova.overloadalert.domain.usecases.AnalysisMode
import kth.nova.overloadalert.domain.usecases.AnalyzeRunData
import java.time.LocalDate

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
class AnalysisRepositoryImpl(
    private val runningRepository: RunningRepository,
    private val analyzeRunData: AnalyzeRunData,
    private val analysisStorage: AnalysisStorage,
    coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) : AnalysisRepository {

    // Holds the raw cached data structure in memory to avoid repeated disk reads.
    private val _latestCachedAnalysis: MutableStateFlow<CachedAnalysis?> =
        MutableStateFlow(analysisStorage.load())
    override val latestCachedAnalysis: StateFlow<CachedAnalysis?> = _latestCachedAnalysis.asStateFlow()

    override val latestAnalysis: StateFlow<UiAnalysisData?> = runningRepository.getAllRuns()
        // Map runs to a lightweight key (hash code) to detect changes efficiently without passing heavy lists downstream.
        .map { runs -> AnalysisKey(runs.hashCode()) }
        // Only trigger recalculations if the run list content has actually changed.
        .distinctUntilChanged()
        .flatMapLatest { key ->
            flow {
                val cached = _latestCachedAnalysis.value
                val today = LocalDate.now()

                // Check if the cache exists but contains no meaningful data (e.g., initialized but not populated).
                val isCacheEmpty = cached?.acwrByDate.isNullOrEmpty()

                // Determine if the cache is stale. It is stale if:
                // 1. It doesn't exist (null).
                // 2. It's effectively empty.
                // 3. The run data hash differs from what was stored (data changed).
                // 4. The cache is from a previous day (we need to recalculate metrics relative to "today").
                val isStale = cached == null ||
                        isCacheEmpty ||
                        key.runIdsHash != cached.lastRunHash ||
                        cached.cacheDate.isBefore(today)

                if (isStale) {
                    val runs = runningRepository.getAllRuns().first()

                    // If the cache is effectively empty or null, we force a full re-analysis.
                    // Otherwise, we perform an incremental update on top of the existing cache.
                    val effectiveCache = if (isCacheEmpty) null else cached

                    // We recalculate the tail end of the analysis to ensure chronic load (28-day window)
                    // and other rolling metrics adjust correctly to the new data or new date.
                    val overlapDate = today.minusDays(40)
                    val updatedCache = analyzeRunData.updateAnalysisFrom(
                        effectiveCache,
                        runs,
                        overlapDate,
                        AnalysisMode.PERSISTENT
                    )
                    
                    // Persist the fresh analysis and update the in-memory state.
                    analysisStorage.save(updatedCache)
                    _latestCachedAnalysis.value = updatedCache
                    
                    // Emit the UI-ready data derived from the new cache.
                    emit(analyzeRunData.deriveUiDataFromCache(updatedCache, today))
                } else {
                    // Cache is fresh. Emit UI data immediately derived from the existing cache.
                    // We re-derive purely to ensure any UI-specific date formatting matches "today".
                    cached?.let { emit(analyzeRunData.deriveUiDataFromCache(it, today)) }
                }
            }
        }
        .stateIn(
            scope = coroutineScope,
            started = SharingStarted.WhileSubscribed(5000),
            // Start with initial data from disk if available, to show UI immediately on app launch.
            initialValue = _latestCachedAnalysis.value?.let { analyzeRunData.deriveUiDataFromCache(it, LocalDate.now()) }
        )
}