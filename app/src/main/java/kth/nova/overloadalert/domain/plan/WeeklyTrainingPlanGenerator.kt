package kth.nova.overloadalert.domain.plan

import android.util.Log
import kth.nova.overloadalert.data.local.Run
import kth.nova.overloadalert.domain.usecases.AnalyzeRunData
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.math.max
import kotlin.math.min

class WeeklyTrainingPlanGenerator {

    fun generate(input: PlanInput, allRuns: List<Run>, analyzeRunData: AnalyzeRunData): WeeklyTrainingPlan {
        val runDays = determineRunDays(input)
        val runTypes = assignRunTypes(runDays, input)
        val weeklyVolume = calculateWeeklyVolume(input)

        var dailyDistances = distributeLoad(runTypes, weeklyVolume, input)

        // Validate and adjust the plan based on daily safety metrics
        dailyDistances = validateAndAdjustPlan(dailyDistances.toMutableMap(), allRuns, analyzeRunData)

        val dailyPlans = DayOfWeek.values().map {
            DailyPlan(
                dayOfWeek = it,
                runType = runTypes[it] ?: RunType.REST,
                plannedDistance = dailyDistances[it] ?: 0f
            )
        }

        return WeeklyTrainingPlan(days = dailyPlans)
    }

    private fun validateAndAdjustPlan(
        distances: MutableMap<DayOfWeek, Float>,
        allRuns: List<Run>,
        analyzeRunData: AnalyzeRunData
    ): Map<DayOfWeek, Float> {
        val today = LocalDate.now()
        val dayValues = DayOfWeek.values()
        val todayIndex = today.dayOfWeek.value - 1
        val validationOrder = (0..6).map { dayValues[(todayIndex + it) % 7] }
        val historicalRuns = allRuns.filter {
            OffsetDateTime.parse(it.startDateLocal).toLocalDate().isBefore(today)
        }

        val simulatedRuns = mutableListOf<Run>()
        for (dayToValidate in validationOrder) {
            val plannedDistance = distances[dayToValidate] ?: 0f
            if (plannedDistance == 0f) continue

            val futureHistory = historicalRuns + simulatedRuns
            val simulatedDate = today.plusDays(validationOrder.indexOf(dayToValidate).toLong())
            // Pass the simulated date to the pure analysis function
            val analysisForDay =
                analyzeRunData(futureHistory, simulatedDate).runAnalysis ?: continue

            val minSafe = analysisForDay.minRecommendedTodaysRun
            val maxSafe = analysisForDay.recommendedTodaysRun

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
        return distances
    }

    private fun determineRunDays(input: PlanInput): Set<DayOfWeek> {
        val targetRunCount = min(input.userPreferences.maxRunsPerWeek, input.historicalData.typicalRunsPerWeek)
        val availableDays = DayOfWeek.values().toSet() - input.userPreferences.forbiddenRunDays

        if (input.historicalData.hasClearWeeklyStructure) {
            val structuredDays = input.historicalData.typicalRunDays.intersect(availableDays)
            return if (structuredDays.size > targetRunCount) {
                structuredDays.take(targetRunCount).toSet()
            } else {
                structuredDays
            }
        }

        // If no clear structure, distribute `targetRunCount` days as evenly as possible.
        // For simplicity now, just take the first `targetRunCount` available days.
        // TODO: A more sophisticated approach could space them out (e.g., avoid back-to-back runs).
        return availableDays.take(targetRunCount).toSet()
    }

    private fun assignRunTypes(runDays: Set<DayOfWeek>, input: PlanInput): Map<DayOfWeek, RunType> {
        val runTypes = mutableMapOf<DayOfWeek, RunType>()

        val longRunDay = findLongRunDay(runDays, input)
        if (longRunDay != null) {
            runTypes[longRunDay] = if (input.recentData.restWeekRequired) RunType.EASY else RunType.LONG
        }

        val remainingRunDays = runDays - runTypes.keys
        val moderateRunCount = when {
            input.recentData.restWeekRequired -> 0
            runDays.size >= 5 -> 2
            runDays.size >= 3 -> 1
            else -> 0
        }
        remainingRunDays.take(moderateRunCount).forEach {
            runTypes[it] = RunType.MODERATE
        }

        (runDays - runTypes.keys).forEach {
            runTypes[it] = RunType.EASY
        }

        return runTypes
    }

    private fun findLongRunDay(runDays: Set<DayOfWeek>, input: PlanInput): DayOfWeek? {
        val userPreferredDay = input.userPreferences.preferredLongRunDays.firstOrNull { it in runDays }
        if (userPreferredDay != null) return userPreferredDay

        val historicalDay = input.historicalData.typicalLongRunDay
        if (historicalDay != null && historicalDay in runDays) return historicalDay

        val weekendDay = runDays.find { it == DayOfWeek.SUNDAY } ?: runDays.find { it == DayOfWeek.SATURDAY }
        if (weekendDay != null) return weekendDay
        
        return runDays.maxByOrNull { it.value }
    }

    private fun calculateWeeklyVolume(input: PlanInput): Float {
        val baseVolume = input.recentData.maxWeeklyVolume
        return if (input.recentData.complianceScore < 0.6f) {
            baseVolume * 0.9f
        } else {
            baseVolume
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
            val moderateShare = (longRunShare * 0.65f)
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
}