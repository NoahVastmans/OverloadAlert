package kth.nova.overloadalert.domain.repository

import android.util.Log
import kth.nova.overloadalert.data.RunningRepository
import kth.nova.overloadalert.domain.usecases.CalendarSyncService
import kth.nova.overloadalert.data.local.PlanStorage
import kth.nova.overloadalert.data.local.Run
import kth.nova.overloadalert.domain.model.AcwrRiskLevel
import kth.nova.overloadalert.domain.model.CachedAnalysis
import kth.nova.overloadalert.domain.model.RiskLevel
import kth.nova.overloadalert.domain.model.RunAnalysis
import kth.nova.overloadalert.domain.plan.*
import kth.nova.overloadalert.domain.usecases.AnalyzeRunData
import kth.nova.overloadalert.domain.usecases.HistoricalDataAnalyzer
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

        // 2. Create the generation key flow, now including the latest analysis
        val generationKeyFlow = combine(
            preferencesRepository.preferencesFlow,
            runningRepository.getAllRuns(),
            analysisRepository.latestAnalysis.filterNotNull(),
            analysisRepository.latestCachedAnalysis.filterNotNull()
        ) { preferences, allRuns, analysisData, cachedAnalysis ->
            val today = LocalDate.now()
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
            old.key == new.key
        }

        // 3. Observe the key and decide whether to regenerate
        generationKeyFlow.flatMapLatest { context ->
            val storedPlan = planStorage.loadPlan()

            // Decide if regeneration is needed
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
        val runAnalysis = context.runAnalysis!!
        val runsForPlanning = context.runsForPlanning

        // We no longer call analyzeRunData() here. We use the passed runAnalysis.
        val relevantHistoricalRuns = runsForPlanning.filter {
            OffsetDateTime.parse(it.startDateLocal)
                .toLocalDate()
                .isAfter(key.planningDate.minusWeeks(8))
        }

        val historicalData = historicalDataAnalyzer(relevantHistoricalRuns)

        // --- Start of Risk/Progression Logic ---
        val today = key.planningDate
        val override = planStorage.loadRiskOverride()
        val daysSinceOverride = override?.let { ChronoUnit.DAYS.between(it.startDate, today) }

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
        val newPlan = planGenerator.generate(planInput, runsForPlanning, analyzeRunData, context.cachedAnalysis)
            .copy(historicalRunsHash = key.historicalRunsHash)
        planStorage.savePlan(newPlan)

        // Trigger Sync
        coroutineScope.launch {
            calendarSyncService.syncPlanToCalendar(newPlan)
        }

        return flow { emit(newPlan) }.stateIn(coroutineScope)
    }

    suspend fun syncCalendar() {
        latestPlan.first()?.let { calendarSyncService.syncPlanToCalendar(it) }
    }

    private data class PlanGenerationContext(
        val key: PlanGenerationKey,
        val runAnalysis: RunAnalysis?,
        val runsForPlanning: List<Run>,
        val cachedAnalysis: CachedAnalysis
    )
}