package kth.nova.overloadalert.domain.repository

import kth.nova.overloadalert.data.RunningRepository
import kth.nova.overloadalert.domain.model.UiAnalysisData
import kth.nova.overloadalert.domain.usecases.AnalyzeRunData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

/**
 * A central repository to hold the single source of truth for all analysis data.
 * It observes the raw run data and calls the analysis engine, then exposes the result.
 */
class AnalysisRepository(
    runningRepository: RunningRepository,
    analyzeRunData: AnalyzeRunData,
    coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    val latestAnalysis: StateFlow<UiAnalysisData?> = runningRepository.getAllRuns()
        .map { runs -> analyzeRunData(runs, LocalDate.now()) } // Pass the current date for live analysis
        .stateIn(
            scope = coroutineScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )
}