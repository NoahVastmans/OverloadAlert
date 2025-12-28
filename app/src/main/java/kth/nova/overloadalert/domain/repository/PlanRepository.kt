package kth.nova.overloadalert.domain.repository

import kth.nova.overloadalert.data.RunningRepository
import kth.nova.overloadalert.domain.model.AcwrRiskLevel
import kth.nova.overloadalert.domain.model.RiskLevel
import kth.nova.overloadalert.domain.plan.PlanInput
import kth.nova.overloadalert.domain.plan.RecentData
import kth.nova.overloadalert.domain.plan.RiskOverride
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
import java.time.temporal.ChronoUnit

class PlanRepository(
    analysisRepository: AnalysisRepository,
    private val preferencesRepository: PreferencesRepository,
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

        val planningStartDate = LocalDate.now()
        val runsForPlanning = allRuns.filter {
            OffsetDateTime.parse(it.startDateLocal)
                .toLocalDate()
                .isBefore(planningStartDate)
        }

        val prePlanAnalysis = analyzeRunData(runsForPlanning, planningStartDate)

        val historicalData = historicalDataAnalyzer(runsForPlanning)

        val today = LocalDate.now()
        // --- Risk Override Logic ---
        val currentOverride = userPreferences.riskOverride
        val isOverrideActive = currentOverride != null &&
                ChronoUnit.DAYS.between(currentOverride.startDate, today) < 7

        val (acwrMultiplier, longRunMultiplier) = if (isOverrideActive) {
            // SCENARIO A: Override is active. Enforce persisted restrictions.
            currentOverride!!.acwrMultiplier to currentOverride.longRunMultiplier
        } else {
            // SCENARIO B: No active override. Check fresh analysis for new risks.
            if (currentOverride != null) {
                // Override expired (> 7 days). Clear it.
                preferencesRepository.savePreferences(userPreferences.copy(riskOverride = null))
            }

            val freshAcwrMultiplier = when (prePlanAnalysis.runAnalysis?.acwrAssessment?.riskLevel) {
                AcwrRiskLevel.UNDERTRAINING -> 0.9f
                AcwrRiskLevel.OPTIMAL -> 1.0f
                AcwrRiskLevel.MODERATE_OVERTRAINING -> 0.85f
                AcwrRiskLevel.HIGH_OVERTRAINING -> 0.65f
                null -> 1.0f
            }

            val freshLongRunMultiplier = when (prePlanAnalysis.runAnalysis?.singleRunRiskAssessment?.riskLevel) {
                RiskLevel.NONE -> 1.1f
                RiskLevel.MODERATE -> 1.0f
                RiskLevel.HIGH -> 0.9f
                RiskLevel.VERY_HIGH -> 0.75f
                null -> 1.1f
            }

            // THE TRIGGER: If risk is elevated, persist the override.
            if (freshAcwrMultiplier < 1.0f || freshLongRunMultiplier < 1.1f) {
                val newOverride = RiskOverride(
                    startDate = today,
                    acwrMultiplier = freshAcwrMultiplier,
                    longRunMultiplier = freshLongRunMultiplier
                )
                preferencesRepository.savePreferences(userPreferences.copy(riskOverride = newOverride))
            }

            freshAcwrMultiplier to freshLongRunMultiplier
        }

        val baseWeeklyVolume = prePlanAnalysis.runAnalysis?.chronicLoad ?: 0f
        val baseMaxLongRun = prePlanAnalysis.runAnalysis?.safeLongRun ?: 0f

        // Apply the determined multipliers
        val adjustedWeeklyVolume = baseWeeklyVolume * acwrMultiplier
        val adjustedMaxLongRun = baseMaxLongRun * longRunMultiplier


        // 5. Construct RecentData using the "pre-plan" analysis values
        val recentData = RecentData(
            maxSafeLongRun = adjustedMaxLongRun,
            baseWeeklyVolume = adjustedWeeklyVolume,
            minDailyVolume = 2000f, // TODO: Make this dynamic
            restWeekRequired = (acwrMultiplier < 1.0f || longRunMultiplier < 1.1f)
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
        started = SharingStarted.Lazily,
        initialValue = null
    )
}