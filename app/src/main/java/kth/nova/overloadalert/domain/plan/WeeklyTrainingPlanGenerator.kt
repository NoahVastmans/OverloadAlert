package kth.nova.overloadalert.domain.plan

import kth.nova.overloadalert.data.local.Run
import kth.nova.overloadalert.domain.usecases.AnalyzeRunData
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters
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
        val currentVolume = distances.values.sum()
        var discrepancy = targetVolume - currentVolume

        if (discrepancy == 0f) return distances

        val easyDays = runTypes.filter { it.value == RunType.EASY }.keys
        if (easyDays.isEmpty()) return distances // Cannot rebalance without easy days

        if (discrepancy > 0) { // Need to add volume
            val totalHeadroom = easyDays.sumOf { day ->
                val (min, max) = safeRanges[day] ?: (0f to Float.MAX_VALUE)
                val current = distances[day] ?: 0f
                max(0.0, (max - current).toDouble())
            }.toFloat()

            if (totalHeadroom > 0) {
                for (day in easyDays) {
                    val (_, max) = safeRanges[day] ?: (0f to Float.MAX_VALUE)
                    val current = distances[day] ?: 0f
                    val headroom = max(0f, max - current)
                    val proportion = headroom / totalHeadroom
                    val volumeToAdd = discrepancy * proportion
                    distances[day] = current + volumeToAdd
                }
            }
        } else { // Need to remove volume
            discrepancy = -discrepancy // Make it a positive value to subtract
            val totalRemovable = easyDays.sumOf { day ->
                val (min, _) = safeRanges[day] ?: (0f to 0f)
                val current = distances[day] ?: 0f
                max(0.0, (current - min).toDouble())
            }.toFloat()

            if (totalRemovable > 0) {
                for (day in easyDays) {
                    val (min, _) = safeRanges[day] ?: (0f to 0f)
                    val current = distances[day] ?: 0f
                    val removable = max(0f, current - min)
                    val proportion = removable / totalRemovable
                    val volumeToRemove = discrepancy * proportion
                    distances[day] = current - volumeToRemove
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
                .toMutableList()

            val historicalCandidates = moderateCandidates.filter { it in historicalDays }
                .sortedBy { it.value } // early -> late
                .toMutableList()
            val nonHistoricalCandidates = moderateCandidates.filter { it !in historicalDays }
                .sortedBy { it.value } // early -> late
                .toMutableList()

            val selectedModerates = mutableListOf<DayOfWeek>()

            // 1. Pick historical candidates alternating early/late to spread
            var left = 0
            var right = historicalCandidates.size - 1
            var pickLeft = true
            while (selectedModerates.size < moderateRunCount && left <= right && historicalCandidates.isNotEmpty()) {
                val candidate = if (pickLeft) historicalCandidates[left++] else historicalCandidates[right--]
                val conflicts = selectedModerates.any { it.isAdjacentTo(candidate) }
                if (!conflicts) selectedModerates.add(candidate)
                pickLeft = !pickLeft
            }

            // 2. Fill remaining slots with non-historical candidates alternating early/late
            left = 0
            right = nonHistoricalCandidates.size - 1
            while (selectedModerates.size < moderateRunCount && left <= right) {
                val candidate = if (pickLeft) nonHistoricalCandidates[left++] else nonHistoricalCandidates[right--]
                val conflicts = selectedModerates.any { it.isAdjacentTo(candidate) }
                if (!conflicts) selectedModerates.add(candidate)
                pickLeft = !pickLeft
            }

            selectedModerates.forEach { runTypes[it] = RunType.MODERATE }
        }

        // ---- 3. EASY runs ----
        val remainingSlots = targetRunCount - runTypes.size
        if (remainingSlots > 0) {
            val easyCandidates = availableDays.filter { it !in runTypes.keys }.toMutableSet()
            val selectedEasy = mutableListOf<DayOfWeek>()

            while (selectedEasy.size < remainingSlots && easyCandidates.isNotEmpty()) {
                val assignedDays = runTypes.keys + selectedEasy

                // Compute min distance to assigned days for each candidate
                val candidateGaps = easyCandidates.groupBy { candidate ->
                    assignedDays.minOfOrNull { assigned -> distanceInDays(candidate, assigned) } ?: 7
                }

                // Max gap
                val maxGap = candidateGaps.keys.maxOrNull() ?: break

                // Candidates with this gap
                val bestCandidates = candidateGaps[maxGap]!!

                // Tie-breaker: alternate early/late selection
                val pick = if (selectedEasy.size % 2 == 0) {
                    bestCandidates.minByOrNull { it.value }!! // early
                } else {
                    bestCandidates.maxByOrNull { it.value }!! // late
                }

                selectedEasy.add(pick)
                easyCandidates.remove(pick)
            }

            selectedEasy.forEach { runTypes[it] = RunType.EASY }
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
        if (moderateDays.isNotEmpty()) {
            val moderateShare = (longRunShare * 0.6f)
            val moderateVolume = max(minDaily, moderateShare)
            moderateDays.forEach {
                distances[it] = moderateVolume
                remainingVolume -= (moderateVolume - minDaily)
            }
        }

        val easyDays = runTypes.entries.filter { it.value == RunType.EASY }.map { it.key }
        if (easyDays.isNotEmpty() && remainingVolume > 0) {
            val easyShare = remainingVolume / easyDays.size
            easyDays.forEach { 
                distances[it] = (distances[it] ?: minDaily) + easyShare
            }
        }

        easyDays.forEach { easy ->
            moderateDays.forEach { mod ->
                if (distances[easy]!! > distances[mod]!!) {
                    val delta = distances[easy]!! - distances[mod]!!
                    distances[easy] = distances[mod]!!
                    remainingVolume += delta
                }
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