package kth.nova.overloadalert.domain.repository

import kth.nova.overloadalert.data.RunningRepository
import kth.nova.overloadalert.domain.plan.PlanInput
import kth.nova.overloadalert.domain.plan.RecentData
import kth.nova.overloadalert.domain.plan.WeeklyTrainingPlan
import kth.nova.overloadalert.domain.plan.WeeklyTrainingPlanGenerator
import kth.nova.overloadalert.domain.usecases.AnalyzeRunData
import kth.nova.overloadalert.domain.usecases.HistoricalDataAnalyzer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.OffsetDateTime

class PlanRepository(
    analysisRepository: AnalysisRepository,
    preferencesRepository: PreferencesRepository,
    runningRepository: RunningRepository,
    historicalDataAnalyzer: HistoricalDataAnalyzer,
    planGenerator: WeeklyTrainingPlanGenerator,
    analyzeRunData: AnalyzeRunData,
    coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {

    val latestPlan: StateFlow<WeeklyTrainingPlan?> = combine(
        analysisRepository.latestAnalysis.filterNotNull(),
        preferencesRepository.preferencesFlow,
        runningRepository.getAllRuns()
    ) { analysisData, userPreferences, allRuns ->

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
            minDailyVolume = 2000f, // TODO: Make this dynamic
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


    }.stateIn(
        scope = coroutineScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )
}