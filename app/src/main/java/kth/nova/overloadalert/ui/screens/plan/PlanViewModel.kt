package kth.nova.overloadalert.ui.screens.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kth.nova.overloadalert.data.RunningRepository
import kth.nova.overloadalert.domain.plan.PlanInput
import kth.nova.overloadalert.domain.plan.RecentData
import kth.nova.overloadalert.domain.plan.WeeklyTrainingPlanGenerator
import kth.nova.overloadalert.domain.repository.AnalysisRepository
import kth.nova.overloadalert.domain.repository.PreferencesRepository
import kth.nova.overloadalert.domain.usecases.HistoricalDataAnalyzer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update

class PlanViewModel(
    analysisRepository: AnalysisRepository,
    preferencesRepository: PreferencesRepository,
    private val historicalDataAnalyzer: HistoricalDataAnalyzer,
    private val planGenerator: WeeklyTrainingPlanGenerator,
    runningRepository: RunningRepository // Need the raw runs for historical analysis
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlanUiState())
    val uiState: StateFlow<PlanUiState> = _uiState.asStateFlow()

    init {
        // Combine all data sources to generate the plan
        combine(
            analysisRepository.latestAnalysis.filterNotNull(),
            preferencesRepository.preferencesFlow,
            runningRepository.getAllRuns()
        ) { analysisData, userPreferences, allRuns ->

            val historicalData = historicalDataAnalyzer(allRuns)

            val recentData = RecentData(
                maxSafeLongRun = analysisData.runAnalysis?.maxSafeLongRun ?: 0f,
                maxWeeklyVolume = analysisData.runAnalysis?.maxWeeklyLoad ?: 0f,
                minDailyVolume = 2000f, // TODO: Make this dynamic
                complianceScore = 1.0f, // TODO: Implement compliance tracking
                restWeekRequired = false // TODO: Implement rest week logic
            )

            val planInput = PlanInput(
                userPreferences = userPreferences,
                historicalData = historicalData,
                recentData = recentData
            )

            planGenerator.generate(planInput)

        }.onEach { plan ->
            _uiState.update { it.copy(isLoading = false, trainingPlan = plan) }
        }.launchIn(viewModelScope)
    }

    companion object {
        fun provideFactory(
            analysisRepository: AnalysisRepository,
            preferencesRepository: PreferencesRepository,
            historicalDataAnalyzer: HistoricalDataAnalyzer,
            planGenerator: WeeklyTrainingPlanGenerator,
            runningRepository: RunningRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return PlanViewModel(analysisRepository, preferencesRepository, historicalDataAnalyzer, planGenerator, runningRepository) as T
            }
        }
    }
}