package kth.nova.overloadalert.domain.repository

import android.content.Context
import kth.nova.overloadalert.domain.plan.ProgressionRate
import kth.nova.overloadalert.domain.plan.RiskOverride
import kth.nova.overloadalert.domain.plan.UserPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.DayOfWeek
import java.time.LocalDate

class PreferencesRepository(context: Context) {

    private val sharedPreferences = context.getSharedPreferences("user_preferences", Context.MODE_PRIVATE)

    private val _preferencesFlow = MutableStateFlow(loadPreferences())
    val preferencesFlow: StateFlow<UserPreferences> = _preferencesFlow.asStateFlow()

    private fun loadPreferences(): UserPreferences {
        val maxRuns = sharedPreferences.getInt(KEY_MAX_RUNS_PER_WEEK, 5)
        val preferredDays = sharedPreferences.getStringSet(KEY_PREFERRED_LONG_RUN_DAYS, emptySet())?.mapNotNull {
            try { DayOfWeek.valueOf(it) } catch (e: IllegalArgumentException) { null }
        }?.toSet() ?: emptySet()
        val forbiddenDays = sharedPreferences.getStringSet(KEY_FORBIDDEN_RUN_DAYS, emptySet())?.mapNotNull {
            try { DayOfWeek.valueOf(it) } catch (e: IllegalArgumentException) { null }
        }?.toSet() ?: emptySet()
        val progressionRate = try {
            ProgressionRate.valueOf(sharedPreferences.getString(KEY_PROGRESSION_RATE, ProgressionRate.SLOW.name) ?: ProgressionRate.SLOW.name)
        } catch (e: IllegalArgumentException) {
            ProgressionRate.SLOW
        }

        val overrideStartDateStr = sharedPreferences.getString(KEY_OVERRIDE_START_DATE, null)
        val riskOverride = if (overrideStartDateStr != null) {
            RiskOverride(
                startDate = LocalDate.parse(overrideStartDateStr),
                acwrMultiplier = sharedPreferences.getFloat(KEY_OVERRIDE_ACWR_MULTIPLIER, 1.0f),
                longRunMultiplier = sharedPreferences.getFloat(KEY_OVERRIDE_LONGRUN_MULTIPLIER, 1.0f)
            )
        } else {
            null
        }

        return UserPreferences(
            maxRunsPerWeek = maxRuns,
            preferredLongRunDays = preferredDays,
            forbiddenRunDays = forbiddenDays,
            progressionRate = progressionRate,
            riskOverride = riskOverride
        )
    }

    fun savePreferences(preferences: UserPreferences) {
        with(sharedPreferences.edit()) {
            putInt(KEY_MAX_RUNS_PER_WEEK, preferences.maxRunsPerWeek)
            putStringSet(KEY_PREFERRED_LONG_RUN_DAYS, preferences.preferredLongRunDays.map { it.name }.toSet())
            putStringSet(KEY_FORBIDDEN_RUN_DAYS, preferences.forbiddenRunDays.map { it.name }.toSet())
            putString(KEY_PROGRESSION_RATE, preferences.progressionRate.name)

            preferences.riskOverride?.let {
                putString(KEY_OVERRIDE_START_DATE, it.startDate.toString())
                putFloat(KEY_OVERRIDE_ACWR_MULTIPLIER, it.acwrMultiplier)
                putFloat(KEY_OVERRIDE_LONGRUN_MULTIPLIER, it.longRunMultiplier)
            } ?: run {
                remove(KEY_OVERRIDE_START_DATE)
                remove(KEY_OVERRIDE_ACWR_MULTIPLIER)
                remove(KEY_OVERRIDE_LONGRUN_MULTIPLIER)
            }
            
            apply()
        }
        _preferencesFlow.update { preferences }
    }

    companion object {
        private const val KEY_MAX_RUNS_PER_WEEK = "max_runs_per_week"
        private const val KEY_PREFERRED_LONG_RUN_DAYS = "preferred_long_run_days"
        private const val KEY_FORBIDDEN_RUN_DAYS = "forbidden_run_days"
        private const val KEY_PROGRESSION_RATE = "progression_rate"
        private const val KEY_OVERRIDE_START_DATE = "override_start_date"
        private const val KEY_OVERRIDE_ACWR_MULTIPLIER = "override_acwr_multiplier"
        private const val KEY_OVERRIDE_LONGRUN_MULTIPLIER = "override_longrun_multiplier"
    }
}