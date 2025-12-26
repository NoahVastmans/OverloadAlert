package kth.nova.overloadalert.domain.repository

import android.content.Context
import kth.nova.overloadalert.domain.plan.UserPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.DayOfWeek

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

        return UserPreferences(
            maxRunsPerWeek = maxRuns,
            preferredLongRunDays = preferredDays,
            forbiddenRunDays = forbiddenDays
        )
    }

    fun savePreferences(preferences: UserPreferences) {
        with(sharedPreferences.edit()) {
            putInt(KEY_MAX_RUNS_PER_WEEK, preferences.maxRunsPerWeek)
            putStringSet(KEY_PREFERRED_LONG_RUN_DAYS, preferences.preferredLongRunDays.map { it.name }.toSet())
            putStringSet(KEY_FORBIDDEN_RUN_DAYS, preferences.forbiddenRunDays.map { it.name }.toSet())
            apply()
        }
        _preferencesFlow.update { preferences }
    }

    companion object {
        private const val KEY_MAX_RUNS_PER_WEEK = "max_runs_per_week"
        private const val KEY_PREFERRED_LONG_RUN_DAYS = "preferred_long_run_days"
        private const val KEY_FORBIDDEN_RUN_DAYS = "forbidden_run_days"
    }
}