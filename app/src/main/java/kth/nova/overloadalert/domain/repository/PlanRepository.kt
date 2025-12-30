package kth.nova.overloadalert.domain.repository

import kth.nova.overloadalert.data.RunningRepository
import kth.nova.overloadalert.data.local.PlanStorage
import kth.nova.overloadalert.domain.model.AcwrRiskLevel
import kth.nova.overloadalert.domain.model.RiskLevel
import kth.nova.overloadalert.domain.plan.*
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
    preferencesRepository: PreferencesRepository,
    private val planStorage: PlanStorage,
    runningRepository: RunningRepository,
    historicalDataAnalyzer: HistoricalDataAnalyzer,
    planGenerator: WeeklyTrainingPlanGenerator,
    analyzeRunData: AnalyzeRunData,
    coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {

    val latestPlan: StateFlow<WeeklyTrainingPlan?> = combine(
        analysisRepository.latestAnalysis.filterNotNull(),
        preferencesRepository.preferencesFlow,
        planStorage.riskOverrideFlow,
        runningRepository.getAllRuns()
    ) { analysisData, userPreferences, override, allRuns ->

        val planningStartDate = LocalDate.now()
        val runsForPlanning = allRuns.filter {
            OffsetDateTime.parse(it.startDateLocal)
                .toLocalDate()
                .isBefore(planningStartDate)
        }

        val prePlanAnalysis = analyzeRunData(runsForPlanning, planningStartDate)

        val historicalData = historicalDataAnalyzer(runsForPlanning)

        val today = LocalDate.now()

        val freshAcwrMultiplier = when (prePlanAnalysis.runAnalysis?.acwrAssessment?.riskLevel) {
            AcwrRiskLevel.UNDERTRAINING -> 0.9f
            AcwrRiskLevel.OPTIMAL -> 1.0f
            AcwrRiskLevel.MODERATE_OVERTRAINING -> 0.85f
            AcwrRiskLevel.HIGH_OVERTRAINING -> 0.65f
            null -> 1.0f
        }

        val freshLongRunMultiplier =
            when (prePlanAnalysis.runAnalysis?.singleRunRiskAssessment?.riskLevel) {
                RiskLevel.NONE -> 1.1f
                RiskLevel.MODERATE -> 1.0f
                RiskLevel.HIGH -> 0.9f
                RiskLevel.VERY_HIGH -> 0.75f
                null -> 1.1f
            }

        val freshAcwrRiskDetected = freshAcwrMultiplier < 1.0f
        val freshLongRunRiskDetected =  freshLongRunMultiplier < 1.1f


        val daysSinceOverride = override?.let { ChronoUnit.DAYS.between(it.startDate, today) }

        val nextOverride = when {
            override == null && freshAcwrRiskDetected ->
                RiskOverride(
                    startDate = today,
                    phase = if (freshAcwrMultiplier < 0.9f) RiskPhase.DELOAD else RiskPhase.REBUILDING,
                    acwrMultiplier = freshAcwrMultiplier,
                    longRunMultiplier = freshLongRunMultiplier
                )

            override == null && freshLongRunRiskDetected ->
                RiskOverride(
                    startDate = today,
                    phase = RiskPhase.LONG_RUN_LIMITED,
                    acwrMultiplier = freshAcwrMultiplier,
                    longRunMultiplier = freshLongRunMultiplier
                )

            override?.phase == RiskPhase.DELOAD && freshAcwrMultiplier >= 0.9f ->
                override.copy(
                    startDate = today,
                    phase = if (freshAcwrMultiplier > 0.9f) RiskPhase.COOLDOWN else RiskPhase.REBUILDING,
                    acwrMultiplier = freshAcwrMultiplier
                )

            override?.phase == RiskPhase.REBUILDING && freshAcwrMultiplier > 0.9f ->
                override.copy(
                    startDate = today,
                    phase = RiskPhase.COOLDOWN,
                    acwrMultiplier = freshAcwrMultiplier
                )

            override?.phase == RiskPhase.COOLDOWN && freshAcwrRiskDetected ->
                RiskOverride(
                    startDate = today,
                    phase = if (freshAcwrMultiplier < 0.9f) RiskPhase.DELOAD else RiskPhase.REBUILDING,
                    acwrMultiplier = freshAcwrMultiplier,
                    longRunMultiplier = freshLongRunMultiplier
                )

            override?.phase == RiskPhase.COOLDOWN && daysSinceOverride!! >= 7 ->
                null

            override?.phase == RiskPhase.LONG_RUN_LIMITED && daysSinceOverride!! >= 7 ->
                null

            else -> override
        }

        if (nextOverride != override) {
            planStorage.saveRiskOverride(nextOverride)
        }

        var acwrMultiplier = 1.0f
        var longRunMultiplier = 1.1f
        var effectiveProgressionRate = userPreferences.progressionRate

        when (nextOverride?.phase) {
            RiskPhase.DELOAD, RiskPhase.REBUILDING -> {
                acwrMultiplier = nextOverride.acwrMultiplier
                longRunMultiplier = nextOverride.longRunMultiplier
                effectiveProgressionRate = ProgressionRate.RETAIN
            }

            RiskPhase.COOLDOWN, RiskPhase.LONG_RUN_LIMITED -> {
                longRunMultiplier = nextOverride.longRunMultiplier
                if (userPreferences.progressionRate == ProgressionRate.FAST) {
                    effectiveProgressionRate = ProgressionRate.SLOW
                }
            }

            null -> Unit
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
            minDailyVolume = adjustedWeeklyVolume/10f,
            riskPhase = nextOverride?.phase
        )

        val previousPlan = planStorage.loadPlan()

        val planInput = PlanInput(
            userPreferences = userPreferences.copy(progressionRate = effectiveProgressionRate),
            historicalData = historicalData,
            recentData = recentData,
            riskOverride = nextOverride,
            previousPlan = previousPlan
        )

        // Pass the clean runs and the analyzer to the generator
        val newPlan = planGenerator.generate(planInput, runsForPlanning, analyzeRunData)
        planStorage.savePlan(newPlan)
        newPlan

    }.stateIn(
        scope = coroutineScope,
        started = SharingStarted.Lazily,
        initialValue = null
    )
}