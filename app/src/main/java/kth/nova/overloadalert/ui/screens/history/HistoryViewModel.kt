package kth.nova.overloadalert.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kth.nova.overloadalert.data.RunningRepository
import kth.nova.overloadalert.domain.usecases.AnalyzeRunData
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.OffsetDateTime

class HistoryViewModel(
    runningRepository: RunningRepository,
    analyzeRunData: AnalyzeRunData
) : ViewModel() {

    val uiState: StateFlow<HistoryUiState> = runningRepository.getAllRuns()
        .map { allRuns ->
            if (allRuns.isEmpty()) {
                return@map HistoryUiState(isLoading = false, analyzedRuns = emptyList())
            }

            // Analyze the full history to get risk assessments for every run.
            val analyzedRuns = analyzeRunData.analyzeFullHistory(allRuns)

            // The oldest run determines the start of our valid analysis window.
            val oldestRunDate = OffsetDateTime.parse(allRuns.last().startDateLocal).toLocalDate()
            // We can only display runs that have a full 30-day history before them.
            val displayStartDate = oldestRunDate.plusDays(30)

            val runsForDisplay = analyzedRuns.filter {
                OffsetDateTime.parse(it.run.startDateLocal).toLocalDate().isAfter(displayStartDate)
            }

            HistoryUiState(isLoading = false, analyzedRuns = runsForDisplay)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = HistoryUiState(isLoading = true)
        )

    companion object {
        fun provideFactory(
            runningRepository: RunningRepository,
            analyzeRunData: AnalyzeRunData
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return HistoryViewModel(runningRepository, analyzeRunData) as T
            }
        }
    }
}