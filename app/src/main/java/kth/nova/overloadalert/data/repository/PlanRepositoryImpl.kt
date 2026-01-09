package kth.nova.overloadalert.data.repository

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kth.nova.overloadalert.data.local.PlanStorage
import kth.nova.overloadalert.data.local.Run
import kth.nova.overloadalert.domain.model.AcwrRiskLevel
import kth.nova.overloadalert.domain.model.CachedAnalysis
import kth.nova.overloadalert.domain.model.RiskLevel
import kth.nova.overloadalert.domain.model.RunAnalysis
import kth.nova.overloadalert.domain.plan.PlanGenerationKey
import kth.nova.overloadalert.domain.plan.PlanInput
import kth.nova.overloadalert.domain.plan.ProgressionRate
import kth.nova.overloadalert.domain.plan.RecentData
import kth.nova.overloadalert.domain.plan.RiskOverride
import kth.nova.overloadalert.domain.plan.RiskPhase
import kth.nova.overloadalert.domain.plan.WeeklyTrainingPlan
import kth.nova.overloadalert.domain.repository.AnalysisRepository
import kth.nova.overloadalert.domain.repository.PlanRepository
import kth.nova.overloadalert.domain.repository.PreferencesRepository
import kth.nova.overloadalert.domain.repository.RunningRepository
import kth.nova.overloadalert.domain.usecases.AnalyzeRunData
import kth.nova.overloadalert.domain.usecases.CalendarSyncService
import kth.nova.overloadalert.domain.usecases.HistoricalDataAnalyzer
import kth.nova.overloadalert.domain.usecases.WeeklyTrainingPlanGenerator
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

/**
 * Repository responsible for managing the lifecycle, generation, and storage of weekly training plans.
 *
 * This class orchestrates the interaction between various data sources and logic components to ensure
 * the user always has an up-to-date training plan. Its primary responsibilities include:
 *
 * 1.  **Plan Generation:** Triggers the generation of new [WeeklyTrainingPlan]s based on user preferences,
 *     historical run data, and calculated risk metrics.
 * 2.  **Reactive Updates:** Exposes a [latestPlan] StateFlow that automatically regenerates the plan
 *     whenever significant changes occur (e.g., new run data, changed preferences, or a new week).
 * 3.  **Risk Management:** Applies progression logic and risk overrides (e.g., DELOAD, REBUILDING phases)
 *     by adjusting volume multipliers based on the latest [RunAnalysis] (ACWR and single-run risk).
 * 4.  **Persistence:** Delegates loading and saving of plans and risk overrides to [PlanStorage].
 * 5.  **External Sync:** Initiates synchronization with the system calendar via [CalendarSyncService]
 *     whenever a new plan is generated.
 *
 * @property analysisRepository Provides access to the latest run analysis metrics.
 * @property preferencesRepository Provides access to user settings (e.g., preferred training days, progression rate).
 * @property planStorage Handles local storage of the generated plan and risk override states.
 * @property runningRepository Provides access to raw run data.
 * @property historicalDataAnalyzer Analyzes historical runs to determine training patterns.
 * @property planGenerator Core logic component that creates the schedule of workouts.
 */
class PlanRepositoryImpl(
    private val analysisRepository: AnalysisRepository,
    private val preferencesRepository: PreferencesRepository,
    private val planStorage: PlanStorage,
    private val runningRepository: RunningRepository,
    private val historicalDataAnalyzer: HistoricalDataAnalyzer,
    private val planGenerator: WeeklyTrainingPlanGenerator,
    private val analyzeRunData: AnalyzeRunData,
    private val calendarSyncService: CalendarSyncService,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) : PlanRepository {

    override val latestPlan: StateFlow<WeeklyTrainingPlan?> = flow {
        // 1. Emit the stored plan first
        // Fast path: show what we have immediately on app launch.
        emit(planStorage.loadPlan())

        // 2. Create the generation key flow, now including the latest analysis
        // We combine all inputs that could trigger a plan change: preferences, run history, and analysis metrics.
        val generationKeyFlow = combine(
            preferencesRepository.preferencesFlow,
            runningRepository.getAllRuns(),
            analysisRepository.latestAnalysis.filterNotNull(),
            analysisRepository.latestCachedAnalysis.filterNotNull()
        ) { preferences, allRuns, analysisData, cachedAnalysis ->
            val today = LocalDate.now()
            // We only consider runs before today for historical analysis to avoid circular dependencies
            // or reacting to the planned run itself if it was just logged.
            val runsForPlanning = allRuns.filter {
                OffsetDateTime.parse(it.startDateLocal).toLocalDate().isBefore(today)
            }

            PlanGenerationContext(
                key = PlanGenerationKey(
                    planningDate = today,
                    userPreferences = preferences,
                    historicalRunsHash = runsForPlanning.hashCode()
                ),
                runAnalysis = analysisData.runAnalysis,
                runsForPlanning = runsForPlanning,
                cachedAnalysis = cachedAnalysis
            )
        }.distinctUntilChanged { old, new ->
            // Only regenerate if the key changes. The analysis data is implicitly tied to the runs hash.
            // Optimization: We check if the inputs that *define* the plan structure have changed.
            old.key == new.key
        }

        // 3. Observe the key and decide whether to regenerate
        generationKeyFlow.flatMapLatest { context ->
            val storedPlan = planStorage.loadPlan()

            // Decide if regeneration is needed
            // A plan needs regeneration if:
            // - It doesn't exist.
            // - It's outdated (previous day/week).
            // - User settings changed.
            // - Historical runs changed (e.g., sync, deletion).
            val needsRegeneration = storedPlan == null ||
                    storedPlan.startDate != context.key.planningDate ||
                    storedPlan.userPreferences != context.key.userPreferences ||
                    storedPlan.historicalRunsHash != context.key.historicalRunsHash

            if (needsRegeneration && context.runAnalysis != null) {
                generateAndStorePlan(context)
            } else {
                flow { emit(storedPlan) }
            }
        }.collect { emit(it) }

    }.stateIn(
        scope = coroutineScope,
        started = SharingStarted.Lazily,
        initialValue = planStorage.loadPlan()
    )

    private suspend fun generateAndStorePlan(
        context: PlanGenerationContext
    ): StateFlow<WeeklyTrainingPlan?> {
        val key = context.key
        
        // Use analysis from the previous day to determine initial risk state.
        // This prevents "undertraining" flags due to simply not having run yet today (which causes ACWR to dip).
        // By looking at the end of yesterday, we get a more stable status for planning the week ahead.
        val previousDay = key.planningDate.minusDays(1)
        val previousDayUiData = analyzeRunData.deriveUiDataFromCache(context.cachedAnalysis, previousDay)
        val runAnalysis = previousDayUiData.runAnalysis ?: context.runAnalysis!!
        
        val runsForPlanning = context.runsForPlanning

        // historicalDataAnalyzer determines typical running days and volumes from the last 8 weeks.
        val relevantHistoricalRuns = runsForPlanning.filter {
            OffsetDateTime.parse(it.startDateLocal)
                .toLocalDate()
                .isAfter(key.planningDate.minusWeeks(8))
        }

        val historicalData = historicalDataAnalyzer(relevantHistoricalRuns)

        // --- Start of Risk/Progression Logic ---
        // This section acts as a state machine for managing injury risk.
        val today = key.planningDate
        val override = planStorage.loadRiskOverride()
        val daysSinceOverride = override?.let { ChronoUnit.DAYS.between(it.startDate, today) }

        // Determine multipliers based on current risk status.
        // Lower multipliers reduce volume to allow recovery.
        val freshAcwrMultiplier = when (runAnalysis.acwrAssessment?.riskLevel) {
            AcwrRiskLevel.UNDERTRAINING -> 0.9f
            AcwrRiskLevel.OPTIMAL -> 1.0f
            AcwrRiskLevel.MODERATE_OVERTRAINING -> 0.85f
            AcwrRiskLevel.HIGH_OVERTRAINING -> 0.65f
            null -> 1.0f
        }

        val freshLongRunMultiplier =
            when (runAnalysis.singleRunRiskAssessment?.riskLevel) {
                RiskLevel.NONE -> 1.1f
                RiskLevel.MODERATE -> 1.0f
                RiskLevel.HIGH -> 0.9f
                RiskLevel.VERY_HIGH -> 0.75f
                null -> 1.1f
            }

        val freshAcwrRiskDetected = freshAcwrMultiplier < 1.0f
        val freshLongRunRiskDetected =  freshLongRunMultiplier < 1.1f

        // State Machine for Risk Override:
        // - Null -> Risk Detected: Enter DELOAD/REBUILDING or LIMITED mode.
        // - DELOAD/REBUILDING -> Recovery: If metrics improve, move to COOLDOWN or REBUILDING.
        // - COOLDOWN/LIMITED -> Expiry: After 7 days, remove override if stable.
        val nextOverride = when {
            // New ACWR risk: Trigger immediate deload or rebuilding phase
            override == null && freshAcwrRiskDetected ->
                RiskOverride(
                    startDate = today,
                    phase = if (freshAcwrMultiplier < 0.9f) RiskPhase.DELOAD else RiskPhase.REBUILDING,
                    acwrMultiplier = freshAcwrMultiplier,
                    longRunMultiplier = freshLongRunMultiplier
                )
            // New Single Run risk: Limit long runs specifically
            override == null && freshLongRunRiskDetected ->
                RiskOverride(
                    startDate = today,
                    phase = RiskPhase.LONG_RUN_LIMITED,
                    acwrMultiplier = freshAcwrMultiplier,
                    longRunMultiplier = freshLongRunMultiplier
                )
            // Transition from DELOAD: If recovering, move to COOLDOWN or stay in REBUILDING
            override?.phase == RiskPhase.DELOAD && freshAcwrMultiplier >= 0.9f ->
                override.copy(
                    startDate = today,
                    phase = if (freshAcwrMultiplier > 0.9f) RiskPhase.COOLDOWN else RiskPhase.REBUILDING,
                    acwrMultiplier = freshAcwrMultiplier
                )
            // Transition from REBUILDING: If fully recovered, start COOLDOWN
            override?.phase == RiskPhase.REBUILDING && freshAcwrMultiplier > 0.9f ->
                override.copy(
                    startDate = today,
                    phase = RiskPhase.COOLDOWN,
                    acwrMultiplier = freshAcwrMultiplier
                )
            // Relapse in COOLDOWN: Go back to DELOAD/REBUILDING
            override?.phase == RiskPhase.COOLDOWN && freshAcwrRiskDetected ->
                RiskOverride(
                    startDate = today,
                    phase = if (freshAcwrMultiplier < 0.9f) RiskPhase.DELOAD else RiskPhase.REBUILDING,
                    acwrMultiplier = freshAcwrMultiplier,
                    longRunMultiplier = freshLongRunMultiplier
                )
            // Expiry: Remove restrictions after 7 days if stable
            override?.phase == RiskPhase.COOLDOWN && daysSinceOverride!! >= 7 -> null
            override?.phase == RiskPhase.LONG_RUN_LIMITED && daysSinceOverride!! >= 7 -> null
            else -> override
        }

        if (nextOverride != override) {
            planStorage.saveRiskOverride(nextOverride)
        }

        // Apply constraints based on the determined phase
        var acwrMultiplier = 1.0f
        var longRunMultiplier = 1.1f
        var effectiveProgressionRate = key.userPreferences.progressionRate

        when (nextOverride?.phase) {
            RiskPhase.DELOAD, RiskPhase.REBUILDING -> {
                acwrMultiplier = nextOverride.acwrMultiplier
                longRunMultiplier = nextOverride.longRunMultiplier
                effectiveProgressionRate = ProgressionRate.RETAIN // Halt progression during recovery
            }
            RiskPhase.COOLDOWN, RiskPhase.LONG_RUN_LIMITED -> {
                longRunMultiplier = nextOverride.longRunMultiplier
                if (key.userPreferences.progressionRate == ProgressionRate.FAST) {
                    effectiveProgressionRate = ProgressionRate.SLOW // Slow down progression after injury/risk
                }
            }
            null -> Unit
        }

        val baseWeeklyVolume = runAnalysis.chronicLoad
        val baseMaxLongRun = runAnalysis.safeLongRun
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

        // Generate and Store
        // The generator creates specific daily distances using the cached analysis for future simulation validation.
        val newPlan = planGenerator.generate(planInput, runsForPlanning, analyzeRunData, context.cachedAnalysis)
            .copy(historicalRunsHash = key.historicalRunsHash)
        planStorage.savePlan(newPlan)

        // Trigger Sync
        // Update Google Calendar immediately with the new plan.
        coroutineScope.launch {
            calendarSyncService.syncPlanToCalendar(newPlan)
        }

        return flow { emit(newPlan) }.stateIn(coroutineScope)
    }

    override suspend fun syncCalendar() {
        latestPlan.first()?.let { calendarSyncService.syncPlanToCalendar(it) }
    }

    private data class PlanGenerationContext(
        val key: PlanGenerationKey,
        val runAnalysis: RunAnalysis?,
        val runsForPlanning: List<Run>,
        val cachedAnalysis: CachedAnalysis
    )
}