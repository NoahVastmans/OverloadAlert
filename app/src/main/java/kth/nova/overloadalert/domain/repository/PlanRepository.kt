package kth.nova.overloadalert.domain.repository

import android.util.Log
import kth.nova.overloadalert.data.RunningRepository
import kth.nova.overloadalert.data.local.PlanStorage
import kth.nova.overloadalert.domain.model.AcwrRiskLevel
import kth.nova.overloadalert.domain.model.RiskLevel
import kth.nova.overloadalert.domain.plan.*
import kth.nova.overloadalert.domain.usecases.AnalyzeRunData
import kth.nova.overloadalert.domain.usecases.CalendarSyncService
import kth.nova.overloadalert.domain.usecases.HistoricalDataAnalyzer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

class PlanRepository(
    private val analysisRepository: AnalysisRepository,
    private val preferencesRepository: PreferencesRepository,
    private val planStorage: PlanStorage,
    private val runningRepository: RunningRepository,
    private val historicalDataAnalyzer: HistoricalDataAnalyzer,
    private val planGenerator: WeeklyTrainingPlanGenerator,
    private val analyzeRunData: AnalyzeRunData,
    private val calendarSyncService: CalendarSyncService,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {

    val latestPlan: StateFlow<WeeklyTrainingPlan?> = flow {
        // 1. Emit the stored plan first
        emit(planStorage.loadPlan())

        // 2. Create the generation key flow
        val generationKeyFlow = combine(
            preferencesRepository.preferencesFlow,
            runningRepository.getAllRuns()
        ) { preferences, allRuns ->
            val today = LocalDate.now()
            val runsForPlanning = allRuns.filter {
                OffsetDateTime.parse(it.startDateLocal).toLocalDate().isBefore(today)
            }
            PlanGenerationKey(
                planningDate = today,
                userPreferences = preferences,
                historicalRunsHash = runsForPlanning.hashCode()
            )
        }.distinctUntilChanged()

        // 3. Observe the key and decide whether to regenerate
        generationKeyFlow.flatMapLatest { key ->
            val storedPlan = planStorage.loadPlan()

            // Decide if regeneration is needed
            val needsRegeneration = storedPlan == null ||
                    storedPlan.startDate != key.planningDate ||
                    storedPlan.userPreferences != key.userPreferences ||
                    storedPlan.historicalRunsHash != key.historicalRunsHash

            if (needsRegeneration) {
                Log.d("PlanRepository", "Regeneration needed. Generating new plan.")
                // Generate and emit the new plan
                generateAndStorePlan(key)
            } else {
                // No change, emit the stored plan again
                Log.d("PlanRepository", "No regeneration needed. Emitting stored plan.")
                flow { emit(storedPlan) }
            }
        }.collect { emit(it) }

    }.stateIn(
        scope = coroutineScope,
        started = SharingStarted.Lazily,
        initialValue = planStorage.loadPlan() // Start with the stored plan immediately
    )

    private suspend fun generateAndStorePlan(key: PlanGenerationKey): StateFlow<WeeklyTrainingPlan?> {
        // This logic is extracted from the old combine block
        val allRuns = runningRepository.getAllRuns().first()
        val runsForPlanning = allRuns.filter {
            OffsetDateTime.parse(it.startDateLocal).toLocalDate().isBefore(key.planningDate)
        }

        val prePlanAnalysis = analyzeRunData(runsForPlanning, key.planningDate)

        val relevantHistoricalRuns = runsForPlanning.filter {
            OffsetDateTime.parse(it.startDateLocal)
                .toLocalDate()
                .isAfter(key.planningDate.minusWeeks(8))
        }

        val historicalData = historicalDataAnalyzer(relevantHistoricalRuns)

        // --- Start of Risk/Progression Logic (unchanged) ---
        val today = key.planningDate
        val override = planStorage.loadRiskOverride()
        val daysSinceOverride = override?.let { ChronoUnit.DAYS.between(it.startDate, today) }

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
            override?.phase == RiskPhase.COOLDOWN && daysSinceOverride!! >= 7 -> null
            override?.phase == RiskPhase.LONG_RUN_LIMITED && daysSinceOverride!! >= 7 -> null
            else -> override
        }

        if (nextOverride != override) {
            planStorage.saveRiskOverride(nextOverride)
        }

        var acwrMultiplier = 1.0f
        var longRunMultiplier = 1.1f
        var effectiveProgressionRate = key.userPreferences.progressionRate

        when (nextOverride?.phase) {
            RiskPhase.DELOAD, RiskPhase.REBUILDING -> {
                acwrMultiplier = nextOverride.acwrMultiplier
                longRunMultiplier = nextOverride.longRunMultiplier
                effectiveProgressionRate = ProgressionRate.RETAIN
            }
            RiskPhase.COOLDOWN, RiskPhase.LONG_RUN_LIMITED -> {
                longRunMultiplier = nextOverride.longRunMultiplier
                if (key.userPreferences.progressionRate == ProgressionRate.FAST) {
                    effectiveProgressionRate = ProgressionRate.SLOW
                }
            }
            null -> Unit
        }

        val baseWeeklyVolume = prePlanAnalysis.runAnalysis?.chronicLoad ?: 0f
        val baseMaxLongRun = prePlanAnalysis.runAnalysis?.safeLongRun ?: 0f
        val adjustedWeeklyVolume = baseWeeklyVolume * acwrMultiplier
        val adjustedMaxLongRun = baseMaxLongRun * longRunMultiplier

        val recentData = RecentData(
            maxSafeLongRun = adjustedMaxLongRun,
            baseWeeklyVolume = adjustedWeeklyVolume,
            minDailyVolume = adjustedWeeklyVolume / 10f,
            riskPhase = nextOverride?.phase
        )

        val planInput = PlanInput(
            userPreferences = key.userPreferences.copy(progressionRate = effectiveProgressionRate),
            historicalData = historicalData,
            recentData = recentData,
            riskOverride = nextOverride,
            previousPlan = planStorage.loadPlan()
        )

        // --- End of Risk/Progression Logic ---

        // Generate and Store
        val newPlan = planGenerator.generate(planInput, runsForPlanning, analyzeRunData)
            .copy(historicalRunsHash = key.historicalRunsHash) // Tag with the hash
        planStorage.savePlan(newPlan)

        // Trigger Sync
        coroutineScope.launch {
            calendarSyncService.syncPlanToCalendar(newPlan)
        }

        // Return as a flow
        return flow { emit(newPlan) }.stateIn(coroutineScope)
    }

    suspend fun syncCalendar() {
        latestPlan.first()?.let { calendarSyncService.syncPlanToCalendar(it) }
    }
}