package kth.nova.overloadalert.domain.plan

import android.util.Log
import kth.nova.overloadalert.data.local.Run
import kth.nova.overloadalert.domain.usecases.AnalyzeRunData
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class WeeklyTrainingPlanGenerator {

    fun generate(input: PlanInput, allRuns: List<Run>, analyzeRunData: AnalyzeRunData): WeeklyTrainingPlan {
        val shouldRecompute = shouldRecomputeStructure(input, allRuns)
        
        val runTypes = if (shouldRecompute) {
            createStructure(input)
        } else {
            input.previousPlan!!.runTypesStructure
        }
        
        val weeklyVolume = calculateWeeklyVolume(input)

        var dailyDistances = distributeLoad(runTypes, weeklyVolume, input)

        if (input.recentData.riskPhase != RiskPhase.DELOAD) {
            val (validatedDistances, safeRanges) = validateAndAdjustPlan(dailyDistances.toMutableMap(), allRuns, analyzeRunData)
            dailyDistances = rebalanceVolume(validatedDistances.toMutableMap(), weeklyVolume, runTypes, safeRanges)
        }

        val dailyPlans = DayOfWeek.entries.map {
            DailyPlan(
                dayOfWeek = it,
                runType = runTypes[it] ?: RunType.REST,
                plannedDistance = dailyDistances[it] ?: 0f,
                isRestWeek = input.recentData.riskPhase == RiskPhase.DELOAD
            )
        }

        val currentWeekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

        return WeeklyTrainingPlan(
            startDate = currentWeekStart,
            days = dailyPlans, 
            riskPhase = input.recentData.riskPhase, 
            progressionRate = input.userPreferences.progressionRate, 
            runTypesStructure = runTypes,
            userPreferences = input.userPreferences
        )
    }

    private fun shouldRecomputeStructure(input: PlanInput, runsForPlanning: List<Run>): Boolean {
        val prev = input.previousPlan ?: return true // No previous plan, must compute

        // 1. Risk Phase Changed
        if (input.recentData.riskPhase != prev.riskPhase) return true

        // 2. Preferences Changed (Structural)
        val newPrefs = input.userPreferences
        val oldPrefs = prev.userPreferences ?: return true 

        if (newPrefs.preferredLongRunDays != oldPrefs.preferredLongRunDays) return true
        if (newPrefs.forbiddenRunDays != oldPrefs.forbiddenRunDays) return true
        if (newPrefs.maxRunsPerWeek != oldPrefs.maxRunsPerWeek) return true

        // 3. Adherence Check
        val planStart = prev.startDate ?: return true
        val firstDayType = prev.runTypesStructure[planStart.dayOfWeek] ?: RunType.REST
        val runHappened = runsForPlanning.any {
            OffsetDateTime.parse(it.startDateLocal).toLocalDate() == planStart
        }

        if (firstDayType != RunType.REST && !runHappened) return true // missed a run
        if (firstDayType == RunType.REST && runHappened) return true // ran on rest day

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

        // Group days by run type
        val shortDays = runTypes.filter { it.value == RunType.SHORT }.keys.toList()
        val moderateDays = runTypes.filter { it.value == RunType.MODERATE }.keys.toList()
        val longDays = runTypes.filter { it.value == RunType.LONG }.keys.toList()

        // Determine order and min/max targets
        val add = discrepancy > 0
        var remaining = kotlin.math.abs(discrepancy)

        val processOrder = if (add) listOf(shortDays, moderateDays, longDays)
        else listOf(longDays, moderateDays, shortDays)

        // Helper to get current, min, max
        fun current(day: DayOfWeek) = distances[day] ?: 0f
        fun maxSafe(day: DayOfWeek) = safeRanges[day]?.second ?: Float.MAX_VALUE
        fun minSafe(day: DayOfWeek) = safeRanges[day]?.first ?: 0f

        if (add) {
            // Stepwise leveling: Short → Moderate → Long → All
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
            // Stepwise lowering: Long → Moderate → Short → All
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
        allRuns: List<Run>,
        analyzeRunData: AnalyzeRunData
    ): Pair<Map<DayOfWeek, Float>, Map<DayOfWeek, Pair<Float, Float>>> {
        val today = LocalDate.now()
        val dayValues = DayOfWeek.entries.toTypedArray()
        val todayIndex = today.dayOfWeek.value - 1
        val validationOrder = (0..6).map { dayValues[(todayIndex + it) % 7] }
        val historicalRuns = allRuns.filter {
            OffsetDateTime.parse(it.startDateLocal).toLocalDate().isBefore(today)
        }
        val safeRanges = mutableMapOf<DayOfWeek, Pair<Float, Float>>()

        val simulatedRuns = mutableListOf<Run>()
        for (dayToValidate in validationOrder) {
            val plannedDistance = distances[dayToValidate] ?: 0f
            if (plannedDistance == 0f) continue

            val futureHistory = historicalRuns + simulatedRuns
            val simulatedDate = today.plusDays(validationOrder.indexOf(dayToValidate).toLong())

            val analysisForDay =
                analyzeRunData(futureHistory, simulatedDate).runAnalysis ?: continue

            val minSafe = analysisForDay.minRecommendedTodaysRun
            val maxSafe = analysisForDay.recommendedTodaysRun
            safeRanges[dayToValidate] = minSafe to maxSafe

            val adjustedDistance = plannedDistance.coerceIn(minSafe, maxSafe)
            Log.d("WeeklyTrainingPlanGenerator", "Adjusted distance for $dayToValidate: $adjustedDistance, planned: $plannedDistance, min: $minSafe, max: $maxSafe")
            distances[dayToValidate] = adjustedDistance
            val movingTimes = (adjustedDistance * 5).toInt()

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

        val historicalDays =
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
                val candidate = moderateCandidates.minByOrNull { kotlin.math.abs(it.value - midDay.value) }!!
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
                            val diff = kotlin.math.abs(a.value - cand.value)
                            diff == 1 || diff == 6
                        }
                    }
                    .minByOrNull { kotlin.math.abs(it.value - midDay.value) }
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
        val diff = kotlin.math.abs(this.value - other.value)
        return diff == 1 || diff == 6 // handles wrap-around (Sunday ↔ Monday)
    }

    private fun distanceInDays(a: DayOfWeek, b: DayOfWeek): Int {
        val diff = kotlin.math.abs(a.value - b.value)
        return min(diff, 7 - diff)
    }

}