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
import kth.nova.overloadalert.domain.usecases.AnalyzeRunData
import kth.nova.overloadalert.domain.usecases.HistoricalDataAnalyzer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.OffsetDateTime

class PlanViewModel(
    analysisRepository: AnalysisRepository,
    preferencesRepository: PreferencesRepository,
    private val historicalDataAnalyzer: HistoricalDataAnalyzer,
    private val planGenerator: WeeklyTrainingPlanGenerator,
    runningRepository: RunningRepository,
    private val analyzeRunData: AnalyzeRunData
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlanUiState())
    val uiState: StateFlow<PlanUiState> = _uiState.asStateFlow()

    init {
        // Combine all data sources to generate the plan
        combine(
            analysisRepository.latestAnalysis.filterNotNull(), // Still needed as a trigger
            preferencesRepository.preferencesFlow,
            runningRepository.getAllRuns()
        ) { _, userPreferences, allRuns ->

            // 1. Define "Today" for planning purposes
            val planningStartDate = LocalDate.now()

            // 2. Filter runs to exclude anything that happened today or in the future.
            // This gives us a "clean slate" history so the analyzer doesn't subtract today's run from the capacity.
            val runsForPlanning = allRuns.filter {
                OffsetDateTime.parse(it.startDateLocal)
                    .toLocalDate()
                    .isBefore(planningStartDate)
            }

            // 3. Run a SPECIFIC analysis for planning using the clean list.
            // Because runsForPlanning has no data for 'planningStartDate',
            // the analyzer will calculate todaysLoad = 0 and return full capacity.
            val prePlanAnalysis = analyzeRunData(runsForPlanning, planningStartDate)

            // 4. Get historical context based on the clean list
            val historicalData = historicalDataAnalyzer(runsForPlanning)

            // 5. Construct RecentData using the "pre-plan" analysis values
            val recentData = RecentData(
                maxSafeLongRun = prePlanAnalysis.runAnalysis?.maxSafeLongRun ?: 0f,
                maxWeeklyVolume = prePlanAnalysis.runAnalysis?.maxWeeklyLoad ?: 0f,
                minDailyVolume = 1500f, // TODO: Make this dynamic
                complianceScore = 1.0f, // TODO: Implement compliance tracking
                restWeekRequired = false // TODO: Implement rest week logic
            )

            val planInput = PlanInput(
                userPreferences = userPreferences,
                historicalData = historicalData,
                recentData = recentData
            )

            // Pass the clean runs and the analyzer to the generator
            planGenerator.generate(planInput, runsForPlanning, analyzeRunData)

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
            runningRepository: RunningRepository,
            analyzeRunData: AnalyzeRunData
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return PlanViewModel(analysisRepository, preferencesRepository, historicalDataAnalyzer, planGenerator, runningRepository, analyzeRunData) as T
            }
        }
    }
}
