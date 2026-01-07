package kth.nova.overloadalert.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kth.nova.overloadalert.data.RunningRepository
import kth.nova.overloadalert.domain.model.AnalyzedRun
import kth.nova.overloadalert.domain.repository.AnalysisRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kth.nova.overloadalert.domain.usecases.mergeRuns
import java.time.OffsetDateTime

/**
 * ViewModel for the History screen that manages the display of past running activities and their associated injury risks.
 *
 * This ViewModel combines data from the [RunningRepository] (raw run data) and [AnalysisRepository]
 * (calculated risk analysis) to produce a list of [AnalyzedRun] objects.
 *
 * Key responsibilities include:
 * - Observing updates from both repositories.
 * - Filtering out runs within the initial 30-day "warm-up" period where risk cannot be accurately calculated.
 * - Merging multiple runs occurring on the same day into single entries using [mergeRuns].
 * - Mapping runs to their corresponding risk scores.
 * - Sorting the final list of analyzed runs in descending chronological order.
 * - Exposing the result via [uiState] for UI consumption.
 *
 * @property runningRepository The repository providing access to raw running activity data.
 * @property analysisRepository The repository providing access to the latest risk analysis results.
 */
class HistoryViewModel(
    runningRepository: RunningRepository,
    analysisRepository: AnalysisRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        combine(
            runningRepository.getAllRuns(),
            analysisRepository.latestAnalysis
        ) { runs, analysisData ->
            if (runs.isEmpty() || analysisData == null) {
                emptyList()
            } else {
                val firstEverRunDate = runs.minOf { OffsetDateTime.parse(it.startDateLocal).toLocalDate() }
                val displayStartDate = firstEverRunDate.plusDays(30)

                // Group runs by date, then flatMap each day to a list of AnalyzedRun
                runs.groupBy { OffsetDateTime.parse(it.startDateLocal).toLocalDate() }
                    .flatMap { (runDate, runsOnDay) ->
                        if (runDate.isBefore(displayStartDate)) {
                            return@flatMap emptyList<AnalyzedRun>() // Skip days within the initial 30-day period
                        }

                        // Merge runs for the same day and map to AnalyzedRun
                        val mergedRunsForDay = mergeRuns(runsOnDay).reversed()
                        mergedRunsForDay.mapNotNull { mergedRun ->
                            val risk = analysisData.combinedRiskByRunID[mergedRun.id]
                                ?: return@mapNotNull null // Skip if no risk is calculated

                            AnalyzedRun(mergedRun, risk)
                        }
                    }
                    .sortedByDescending { OffsetDateTime.parse(it.run.startDateLocal).toLocalDate() }
            }
        }.onEach { runsForDisplay ->
            _uiState.update {
                it.copy(
                    isLoading = false,
                    analyzedRuns = runsForDisplay
                )
            }
        }.launchIn(viewModelScope)
    }

    companion object {
        fun provideFactory(
            runningRepository: RunningRepository,
            analysisRepository: AnalysisRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return HistoryViewModel(runningRepository, analysisRepository) as T
            }
        }
    }
}