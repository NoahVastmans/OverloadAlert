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
        .map { runs -> AnalysisKey( runs.hashCode()) }
        .distinctUntilChanged()
        .flatMapLatest { key ->
            flow {
                val cached = analysisStorage.load()
                val storedKey = cached?.let { AnalysisKey( it.lastRunHash) } // Construct a comparable key

                val runs = runningRepository.getAllRuns().first()

                if (key == storedKey) {
                    Log.d("AnalysisRepository", "Analysis cache is up to date.")
                    emit(analyzeRunData.deriveUiDataFromCache(cached, runs, LocalDate.now()))
                } else {
                    Log.d("AnalysisRepository", "Analysis cache is stale. Recalculating...")
                    val overlapDate = LocalDate.now().minusDays(5)
                    val updatedCache = analyzeRunData.updateAnalysisFrom(cached, runs, overlapDate)
                    analysisStorage.save(updatedCache)
                    emit(analyzeRunData.deriveUiDataFromCache(updatedCache, runs, LocalDate.now()))
                }
            }
        }
        .stateIn(
            scope = coroutineScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null // Start with null to ensure the flow triggers
        )
}