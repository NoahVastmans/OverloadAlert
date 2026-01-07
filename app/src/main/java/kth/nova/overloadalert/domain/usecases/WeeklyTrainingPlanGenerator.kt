package kth.nova.overloadalert.domain.usecases

import kth.nova.overloadalert.data.local.Run
import kth.nova.overloadalert.domain.model.CachedAnalysis
import kth.nova.overloadalert.domain.plan.DailyPlan
import kth.nova.overloadalert.domain.plan.PlanInput
import kth.nova.overloadalert.domain.plan.ProgressionRate
import kth.nova.overloadalert.domain.plan.RiskPhase
import kth.nova.overloadalert.domain.plan.RunType
import kth.nova.overloadalert.domain.plan.WeeklyTrainingPlan
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Generates a personalized weekly running plan for the upcoming week.
 *
 * This class is the core of the training plan generation logic. It synthesizes user preferences,
 * historical performance, recent training load, and physiological risk analysis to create a
 * structured, safe, and effective training schedule for the next 7 days.
 *
 * The generation process involves several key steps:
 * 1.  **Structure Determination:** It first decides whether to create a new weekly structure
 *     (i.e., which days to run and what type of run for each day) or reuse the structure from a
 *     previous plan. A new structure is created if there's no previous plan, user preferences have
 *     changed, the risk phase has changed, or the user deviated significantly from the previous day's plan.
 * 2.  **Volume Calculation:** It calculates the target total weekly running distance (volume) based on
 *     the user's recent training history and their desired progression rate (e.g., retain, slow, fast).
 * 3.  **Initial Load Distribution:** It distributes the target weekly volume across the planned
 *     running days, assigning initial distances based on the run type (long, moderate, short).
 * 4.  **Iterative Validation and Adjustment:** This is a crucial step where the plan is refined for safety.
 *     The generator simulates the plan day by day, using `AnalyzeRunData` to predict the physiological
 *     impact (e.g., risk of overtraining) for each run. If a planned run is deemed too risky or too easy,
 *     its distance is adjusted to fall within a "safe range".
 * 5.  **Rebalancing:** After adjustments, the total weekly volume might no longer match the target. The
 *     `rebalanceVolume` function intelligently redistributes the surplus or deficit across the other
 *     running days, respecting the safe ranges and run type hierarchy, to converge on a plan that is
 *     both safe and meets the overall volume goal.
 */
class WeeklyTrainingPlanGenerator {

    fun generate(input: PlanInput, allRuns: List<Run>, analyzeRunData: AnalyzeRunData, cachedAnalysis: CachedAnalysis): WeeklyTrainingPlan {
        val today = LocalDate.now()
        // Determine if we need to regenerate the structure (which days/types) or can simply adjust volumes.
        val shouldRecompute = shouldRecomputeStructure(input, allRuns, today)

        val runTypes = if (shouldRecompute) {
            createStructure(input)
        } else {
            input.previousPlan!!.runTypesStructure
        }

        // Calculate the goal volume for this week based on progression settings.
        val weeklyVolume = calculateWeeklyVolume(input)

        // Initial naive distribution of volume based on run types.
        var dailyDistances = distributeLoad(runTypes, weeklyVolume, input)
        var twoBack: Map<DayOfWeek, Float>? = null

        // Optimization Loop:
        // Because clamping a run to a safe range (validation) changes the total weekly volume,
        // we need to re-distribute (rebalance) the difference to other days.
        // We iterate until the plan stabilizes (converges) or we hit a limit.
        if (input.recentData.riskPhase != RiskPhase.DELOAD || input.recentData.riskPhase != RiskPhase.REBUILDING) {
            for (i in 0 until 10) {
                val before = dailyDistances.toMap()

                // 1. Simulate the week and clamp runs to safe physiological ranges.
                val (validatedDistances, safeRanges) = validateAndAdjustPlan(dailyDistances.toMutableMap(), analyzeRunData, cachedAnalysis)

                // 2. Redistribute any volume lost or gained during validation to other days.
                dailyDistances = rebalanceVolume(validatedDistances.toMutableMap(), weeklyVolume, runTypes, safeRanges)

                // Check for convergence (stability)
                if (maxDelta(dailyDistances, before) < 0.1) {break}
                // Check for oscillation (flipping between two states)
                if (twoBack != null && maxDelta(dailyDistances, twoBack) < 0.1f) {break}
                twoBack = before
            }
        }

        // Generate daily plans for 7 days starting from today
        val dailyPlans = (0..6).map { i ->
            val date = today.plusDays(i.toLong())
            val dayOfWeek = date.dayOfWeek
            DailyPlan(
                date = date,
                dayOfWeek = dayOfWeek,
                runType = runTypes[dayOfWeek] ?: RunType.REST,
                plannedDistance = dailyDistances[dayOfWeek] ?: 0f,
                isRestWeek = input.recentData.riskPhase == RiskPhase.DELOAD
            )
        }

        return WeeklyTrainingPlan(
            startDate = today,
            days = dailyPlans,
            riskPhase = input.recentData.riskPhase,
            progressionRate = input.userPreferences.progressionRate,
            runTypesStructure = runTypes,
            userPreferences = input.userPreferences
        )
    }

    fun maxDelta(a: Map<DayOfWeek, Float>, b: Map<DayOfWeek, Float>): Float {
        if (a.keys != b.keys) return Float.MAX_VALUE

        return a.keys.maxOf { day ->
            val va: Float = a[day] ?: 0f
            val vb: Float = b[day] ?: 0f
            abs(va - vb)
        }
    }


    private fun shouldRecomputeStructure(input: PlanInput, runsForPlanning: List<Run>, today: LocalDate): Boolean {
        val prev = input.previousPlan ?: return true // No previous plan, must compute

        // 1. Risk Phase Changed: If we moved from Normal to Deload, structure usually changes.
        if (input.recentData.riskPhase != prev.riskPhase) return true

        // 2. Preferences Changed (Structural): e.g. User changed preferred long run day.
        val newPrefs = input.userPreferences
        val oldPrefs = prev.userPreferences ?: return true

        if (newPrefs.preferredLongRunDays != oldPrefs.preferredLongRunDays) return true
        if (newPrefs.forbiddenRunDays != oldPrefs.forbiddenRunDays) return true
        if (newPrefs.maxRunsPerWeek != oldPrefs.maxRunsPerWeek) return true

        // 3. Adherence Check
        // If the user skipped a run or ran on a rest day yesterday, we should recompute to adapt.
        val yesterday = today.minusDays(1)
        val prevPlanDay = prev.days.find { it.date == yesterday }

        if (prevPlanDay != null) {
            val runHappened = runsForPlanning.any {
                OffsetDateTime.parse(it.startDateLocal).toLocalDate() == yesterday
            }

            if (prevPlanDay.runType != RunType.REST && !runHappened) return true // Missed a run yesterday
            if (prevPlanDay.runType == RunType.REST && runHappened) return true // Ran on a rest day yesterday
        }

        return false
    }

    private fun createStructure(input: PlanInput): Map<DayOfWeek, RunType> {
        val runDays = determineRunDays(input)
        val runTypes = assignRunTypes(runDays, input)
        return runTypes
    }


    private fun rebalanceVolume(
        distances: MutableMap<DayOfWeek, Float>,
        targetVolume: Float,
        runTypes: Map<DayOfWeek, RunType>,
        safeRanges: Map<DayOfWeek, Pair<Float, Float>>
    ): Map<DayOfWeek, Float> {

        var discrepancy = targetVolume - distances.values.sum()
        if (discrepancy == 0f) return distances

        // Group days by run type to prioritize volume adjustments
        val shortDays = runTypes.filter { it.value == RunType.SHORT }.keys.toList()
        val moderateDays = runTypes.filter { it.value == RunType.MODERATE }.keys.toList()
        val longDays = runTypes.filter { it.value == RunType.LONG }.keys.toList()

        val add = discrepancy > 0
        var remaining = abs(discrepancy)

        // Helper to get current, min, max
        fun current(day: DayOfWeek) = distances[day] ?: 0f
        fun maxSafe(day: DayOfWeek) = safeRanges[day]?.second ?: Float.MAX_VALUE
        fun minSafe(day: DayOfWeek) = safeRanges[day]?.first ?: 0f

        if (add) {
            // Priority for ADDING volume:
            // 1. Short runs (fill them up first as they are easiest to recover from)
            // 2. Moderate runs
            // 3. Long run (last resort to avoid making it too long)
            val targets = listOf(
                shortDays to { day: DayOfWeek -> moderateDays.mapNotNull { current(it) }.minOrNull() ?: current(day) },
                moderateDays to { day: DayOfWeek -> longDays.mapNotNull { current(it) }.minOrNull() ?: current(day) },
                shortDays + moderateDays + longDays to { day: DayOfWeek -> maxSafe(day) }
            )

            for ((days, targetFn) in targets) {
                for (day in days) {
                    if (remaining <= 0f) break
                    val cap = min(targetFn(day), maxSafe(day))
                    val toAdd = minOf(cap - current(day), remaining)
                    if (toAdd > 0f) {
                        distances[day] = current(day) + toAdd
                        remaining -= toAdd
                    }
                }
            }
        } else {
            // Priority for CUTTING volume:
            // 1. Long run (reduce risk of longest effort first)
            // 2. Moderate runs
            // 3. Short runs (keep frequency if possible)
            val targets = listOf(
                longDays to { day: DayOfWeek -> moderateDays.mapNotNull { current(it) }.maxOrNull() ?: current(day) },
                moderateDays to { day: DayOfWeek -> shortDays.mapNotNull { current(it) }.maxOrNull() ?: current(day) },
                longDays + moderateDays + shortDays to { day: DayOfWeek -> minSafe(day) }
            )

            for ((days, targetFn) in targets) {
                for (day in days) {
                    if (remaining <= 0f) break
                    val cap = max(targetFn(day), minSafe(day))
                    val toRemove = minOf(current(day) - cap, remaining)
                    if (toRemove > 0f) {
                        distances[day] = current(day) - toRemove
                        remaining -= toRemove
                    }
                }
            }
        }

        return distances
    }



    private fun validateAndAdjustPlan(
        distances: MutableMap<DayOfWeek, Float>,
        analyzeRunData: AnalyzeRunData,
        cachedAnalysis: CachedAnalysis
    ): Pair<Map<DayOfWeek, Float>, Map<DayOfWeek, Pair<Float, Float>>> {
        val today = LocalDate.now()
        val dayValues = DayOfWeek.entries.toTypedArray()
        val todayIndex = today.dayOfWeek.value - 1
        val validationOrder = (0..6).map { dayValues[(todayIndex + it) % 7] }
        val safeRanges = mutableMapOf<DayOfWeek, Pair<Float, Float>>()

        // Accumulator for simulated runs. As we step through the week, we add the *adjusted* runs
        // to this list so subsequent days "know" about the load from earlier in the week.
        val simulatedRuns = mutableListOf<Run>()
        for (dayToValidate in validationOrder) {
            val plannedDistance = distances[dayToValidate] ?: 0f

            val simulatedDate = today.plusDays(validationOrder.indexOf(dayToValidate).toLong())

            // "What-if" analysis: What is the load state on this day given the history + simulated week so far?
            val analysisForDay = analyzeRunData.getAnalysisForFutureDate(cachedAnalysis, simulatedRuns, simulatedDate).runAnalysis ?: continue

            var adjustedDistance = 0f
            if (plannedDistance != 0f) {
                // Get safe bounds from the analysis (derived from ACWR and chronic load)
                val minSafe = analysisForDay.minRecommendedTodaysRun
                val maxSafe = analysisForDay.recommendedTodaysRun
                safeRanges[dayToValidate] = minSafe to maxSafe

                // Clamp the planned distance to the safe range
                adjustedDistance = plannedDistance.coerceIn(minSafe, maxSafe)
                distances[dayToValidate] = adjustedDistance
            }
            val movingTimes = (adjustedDistance * 5).toInt() // raw estimate, just to have some moving time

            // Add this finalized run for this day to the simulation context for the next day's check
            simulatedRuns.add(
                Run(
                    id = dayToValidate.value.toLong() * -1, // Dummy ID
                    distance = adjustedDistance,
                    startDateLocal = simulatedDate.atStartOfDay().atOffset(ZoneOffset.UTC)
                        .toString(),
                    movingTime = movingTimes
                )
            )
        }
        return distances to safeRanges
    }

    private fun determineRunDays(input: PlanInput): Set<DayOfWeek> {
        return DayOfWeek.entries.toSet() - input.userPreferences.forbiddenRunDays
    }


    private fun assignRunTypes(
        availableDays: Set<DayOfWeek>,
        input: PlanInput
    ): Map<DayOfWeek, RunType> {

        val runTypes = mutableMapOf<DayOfWeek, RunType>()

        val targetRunCount = if (input.recentData.riskPhase == RiskPhase.DELOAD) {
            min(input.userPreferences.maxRunsPerWeek, input.historicalData.typicalRunsPerWeek - 1)
        } else min(
            input.userPreferences.maxRunsPerWeek,
            input.historicalData.typicalRunsPerWeek + 1
        )

        if (input.historicalData.hasClearWeeklyStructure)
            input.historicalData.typicalRunDays
        else
            emptySet()

        // ---- 1. LONG run ----
        val longRunDay = selectLongRunDay(availableDays, input)
        if (longRunDay != null) {
            runTypes[longRunDay] = RunType.LONG
        }

        // ---- 2. MODERATE runs ----
        val moderateRunCount = when {
            targetRunCount >= 5 -> 2
            targetRunCount >= 3 -> 1
            else -> 0
        }

        if (moderateRunCount > 0) {
            val moderateCandidates = availableDays
                .filter { it !in runTypes.keys }
                .filter { day -> longRunDay == null || !day.isAdjacentTo(longRunDay) }
                .toMutableSet()

            val assignedModerate = runTypes.filter { it.value == RunType.MODERATE }.keys.toMutableList()
            val selectedModerates = mutableListOf<DayOfWeek>()

            repeat(moderateRunCount) {
                if (moderateCandidates.isEmpty()) return@repeat

                // Combine already assigned MODERATE runs + LONG runs for gap calculation
                val sortedAssigned = (assignedModerate + runTypes.keys.filter { it !in assignedModerate }).sortedBy { it.value }

                // Compute gaps
                val gaps = sortedAssigned.zipWithNext { a, b ->
                    val gap = (b.value - a.value - 1 + 7) % 7
                    Pair(a, gap)
                } + Pair(sortedAssigned.last(), (sortedAssigned.first().value + 7 - sortedAssigned.last().value - 1) % 7) // wrap-around

                // Find largest gap
                val largestGap = gaps.maxByOrNull { it.second }!!
                val (gapStart, gapSize) = largestGap

                // Target the middle of the gap
                val midValue = (gapStart.value + (gapSize + 1) / 2) % 7
                val midDay = DayOfWeek.of(if (midValue == 0) 7 else midValue)

                // Pick candidate closest to mid
                val candidate = moderateCandidates.minByOrNull { abs(it.value - midDay.value) }!!
                selectedModerates.add(candidate)
                moderateCandidates.remove(candidate)
                assignedModerate.add(candidate)
            }

            selectedModerates.forEach { runTypes[it] = RunType.MODERATE }
        }

        // ---- 3. SHORT runs ----
        val remainingSlots = targetRunCount - runTypes.size
        if (remainingSlots > 0) {
            val easyCandidates = availableDays.filter { it !in runTypes.keys }.toMutableSet()
            val selectedEasy = mutableListOf<DayOfWeek>()

            // Start with already assigned SHORT runs only
            val assignedShort = runTypes.filter { it.value == RunType.SHORT }.keys.toMutableList()

            repeat(remainingSlots) {
                if (easyCandidates.isEmpty()) return@repeat

                // Compute gaps between all assigned days (for placement reference)
                val sortedAssigned = (assignedShort + runTypes.keys.filter { it !in assignedShort }).sortedBy { it.value }

                val gaps = sortedAssigned.zipWithNext { a, b ->
                    val gap = (b.value - a.value - 1 + 7) % 7
                    Pair(a, gap)
                } + Pair(sortedAssigned.last(), (sortedAssigned.first().value + 7 - sortedAssigned.last().value - 1) % 7) // wrap-around

                // Find largest gap
                val largestGap = gaps.maxByOrNull { it.second }!!
                val (gapStart, gapSize) = largestGap

                // Target the middle of the gap
                val midValue = (gapStart.value + (gapSize + 1) / 2) % 7
                val midDay = DayOfWeek.of(if (midValue == 0) 7 else midValue)

                // Pick candidate closest to mid that is not adjacent to any already assigned SHORT run
                val candidate = easyCandidates
                    .filter { cand ->
                        assignedShort.none { a ->
                            val diff = abs(a.value - cand.value)
                            diff == 1 || diff == 6
                        }
                    }
                    .minByOrNull { abs(it.value - midDay.value) }
                    ?: easyCandidates.first() // fallback if no candidate is non-adjacent

                selectedEasy.add(candidate)
                easyCandidates.remove(candidate)
                assignedShort.add(candidate)
            }

            selectedEasy.forEach { runTypes[it] = RunType.SHORT }
        }

        return runTypes
    }




    private fun selectLongRunDay(availableDays: Set<DayOfWeek>, input: PlanInput): DayOfWeek? {
        val preferred = input.userPreferences.preferredLongRunDays
            .filter { it in availableDays }

        val historicalDays =
            if (input.historicalData.hasClearWeeklyStructure)
                input.historicalData.typicalRunDays
            else
                emptySet()

        // 1. Preferred ∩ historical
        preferred.firstOrNull { it in historicalDays }
            ?.let { return it }

        // 2. Preferred only
        preferred.firstOrNull()
            ?.let { return it }

        // 3. Historical long-run day
        input.historicalData.typicalLongRunDay
            ?.takeIf { it in availableDays }
            ?.let { return it }

        // 4. Fallback: latest available day
        return availableDays.maxByOrNull { it.value }
    }

    private fun calculateWeeklyVolume(input: PlanInput): Float {
        val baseVolume = input.recentData.baseWeeklyVolume
        // Adjust the base volume based on the user-selected progression rate
        return when (input.userPreferences.progressionRate) {
            ProgressionRate.RETAIN -> baseVolume
            ProgressionRate.SLOW -> baseVolume * 1.1f    // A slight increase
            ProgressionRate.FAST -> baseVolume * 1.3f    // Use the max safe volume
        }
    }

    private fun distributeLoad(runTypes: Map<DayOfWeek, RunType>, weeklyVolume: Float, input: PlanInput): Map<DayOfWeek, Float> {
        val distances = mutableMapOf<DayOfWeek, Float>()
        var remainingVolume = weeklyVolume
        val minDaily = input.recentData.minDailyVolume

        runTypes.keys.forEach {
            distances[it] = minDaily
            remainingVolume -= minDaily
        }

        val longRunDay = runTypes.entries.find { it.value == RunType.LONG }?.key
        val longRunShare = (weeklyVolume * 0.38f).coerceAtMost(input.recentData.maxSafeLongRun)
        if (longRunDay != null) {
            val longRunVolume = max(minDaily, longRunShare)
            distances[longRunDay] = longRunVolume
            remainingVolume -= (longRunVolume - minDaily)
        }

        val moderateDays = runTypes.entries.filter { it.value == RunType.MODERATE }.map { it.key }
        val moderateShare = (longRunShare * 0.6f)
        if (moderateDays.isNotEmpty()) {
            val moderateVolume = max(minDaily, moderateShare)
            moderateDays.forEach {
                distances[it] = moderateVolume
                remainingVolume -= (moderateVolume - minDaily)
            }
        }

        val shortDays = runTypes.entries.filter { it.value == RunType.SHORT }.map { it.key }
        if (shortDays.isNotEmpty() && remainingVolume > 0) {
            val shortShare = remainingVolume / shortDays.size
            val maxShortVolume = moderateShare * 0.9f
            val maxShortIncrement = max(0f, maxShortVolume - minDaily * shortDays.size)
            val shortIncrement = shortShare.coerceAtMost(maxShortIncrement)
            shortDays.forEach {
                distances[it] = (distances[it] ?: minDaily) + shortIncrement
            }
        }

        return distances
    }

    private fun DayOfWeek.isAdjacentTo(other: DayOfWeek): Boolean {
        val diff = abs(this.value - other.value)
        return diff == 1 || diff == 6 // handles wrap-around (Sunday ↔ Monday)
    }

}