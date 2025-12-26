package kth.nova.overloadalert.domain.plan

import java.time.DayOfWeek
import kotlin.math.min

class WeeklyTrainingPlanGenerator {

    fun generate(input: PlanInput): WeeklyTrainingPlan {
        val runDays = determineRunDays(input)
        val runTypes = assignRunTypes(runDays, input)
        val weeklyVolume = calculateWeeklyVolume(input)
        val dailyDistances = distributeLoad(runTypes, weeklyVolume, input)

        val dailyPlans = DayOfWeek.entries.map {
            DailyPlan(
                dayOfWeek = it,
                runType = runTypes[it] ?: RunType.REST,
                plannedDistance = dailyDistances[it] ?: 0f
            )
        }

        return WeeklyTrainingPlan(days = dailyPlans)
    }

    private fun determineRunDays(input: PlanInput): Set<DayOfWeek> {
        val targetRunCount = min(input.userPreferences.maxRunsPerWeek, input.historicalData.typicalRunsPerWeek)
        val availableDays = DayOfWeek.entries.toSet() - input.userPreferences.forbiddenRunDays

        // If a clear structure exists, prioritize those days.
        if (input.historicalData.hasClearWeeklyStructure) {
            val structuredDays = input.historicalData.typicalRunDays.intersect(availableDays)
            // If the historical structure has more days than needed, trim them.
            // A simple take() is okay for now as per the existing TODO.
            return if (structuredDays.size > targetRunCount) {
                structuredDays.take(targetRunCount).toSet()
            } else {
                // If there aren't enough structured days (e.g., user forbids one), that's fine.
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

        // 1. Assign Long Run using a prioritized list of candidates
        val longRunDay = findLongRunDay(runDays, input)

        if (longRunDay != null) {
            runTypes[longRunDay] = if (input.recentData.restWeekRequired) RunType.EASY else RunType.LONG
        }

        // 2. Assign Moderate Runs
        val remainingRunDays = runDays - runTypes.keys
        val moderateRunCount = when {
            input.recentData.restWeekRequired -> 0 // No moderate runs on a rest week
            runDays.size >= 5 -> 2
            runDays.size >= 3 -> 1
            else -> 0
        }

        remainingRunDays.take(moderateRunCount).forEach {
            runTypes[it] = RunType.MODERATE
        }

        // 3. Assign Easy Runs to all other remaining run days
        (runDays - runTypes.keys).forEach {
            runTypes[it] = RunType.EASY
        }

        return runTypes
    }

    private fun findLongRunDay(runDays: Set<DayOfWeek>, input: PlanInput): DayOfWeek? {
        // Find the first available day from the user's preferences
        val userPreferredDay = input.userPreferences.preferredLongRunDays.firstOrNull { it in runDays }
        if (userPreferredDay != null) return userPreferredDay

        // If no user preference matches, check the historical preference
        val historicalDay = input.historicalData.typicalLongRunDay
        if (historicalDay != null && historicalDay in runDays) return historicalDay

        // As a fallback, check for a weekend day
        val weekendDay = runDays.find { it == DayOfWeek.SUNDAY } ?: runDays.find { it == DayOfWeek.SATURDAY }
        if (weekendDay != null) return weekendDay
        
        // As a final fallback, use the last run day of the week
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

    // TODO: check this logic (especially remaining volume and minDaily)
    private fun distributeLoad(runTypes: Map<DayOfWeek, RunType>, weeklyVolume: Float, input: PlanInput): Map<DayOfWeek, Float> {
        val distances = mutableMapOf<DayOfWeek, Float>()
        var remainingVolume = weeklyVolume

        val minDaily = input.recentData.minDailyVolume

        // Assign minimums first to ensure all run days have at least some volume
        runTypes.keys.forEach {
            distances[it] = minDaily
            remainingVolume -= minDaily
        }

        // Distribute Long Run volume
        val longRunDay = runTypes.entries.find { it.value == RunType.LONG }?.key
        if (longRunDay != null) {
            val longRunShare = (weeklyVolume * 0.38f).coerceAtMost(input.recentData.maxSafeLongRun)
            val longRunVolume = maxOf(minDaily, longRunShare)
            distances[longRunDay] = longRunVolume
            remainingVolume -= (longRunVolume - minDaily) // Only subtract the amount added *above* the minimum
        }

        // Distribute Moderate Run volume
        val moderateDays = runTypes.entries.filter { it.value == RunType.MODERATE }.map { it.key }
        if (moderateDays.isNotEmpty()) {
            val moderateShare = (weeklyVolume * 0.25f) / moderateDays.size
            val moderateVolume = maxOf(minDaily, moderateShare)
            moderateDays.forEach {
                distances[it] = moderateVolume
                remainingVolume -= (moderateVolume - minDaily)
            }
        }

        // Distribute remaining volume to Easy Runs
        val easyDays = runTypes.entries.filter { it.value == RunType.EASY }.map { it.key }
        if (easyDays.isNotEmpty() && remainingVolume > 0) {
            val easyShare = remainingVolume / easyDays.size
            easyDays.forEach { 
                // Add the remaining share to the minimum already assigned
                distances[it] = (distances[it] ?: minDaily) + easyShare
            }
        }

        return distances
    }
}