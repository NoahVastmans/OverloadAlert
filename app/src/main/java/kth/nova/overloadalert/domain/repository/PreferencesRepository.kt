package kth.nova.overloadalert.domain.repository

import android.content.Context
import kth.nova.overloadalert.data.remote.GoogleTokenManager
import kth.nova.overloadalert.domain.plan.ProgressionRate
import kth.nova.overloadalert.domain.plan.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.DayOfWeek

/**
 * Repository responsible for managing and persisting user preferences for the training plan.
 *
 * This class handles reading from and writing to the Android [SharedPreferences]. It provides
 * a reactive [StateFlow] of [UserPreferences] to allow observers to react to changes in settings
 * (e.g., maximum runs per week, forbidden days, premium status).
 *
 * Additionally, it combines user preferences with the Google Token Manager state to determine
 * the effective Google connection status (only considered connected if the user is also Premium).
 *
 * @property context The Android application context used to access SharedPreferences.
 * @property googleTokenManager Manager for handling Google authentication tokens.
 * @property coroutineScope The scope used for collecting flows and sharing state, defaults to [Dispatchers.Default].
 */
class PreferencesRepository(
    context: Context,
    googleTokenManager: GoogleTokenManager,
    coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {

    private val sharedPreferences = context.getSharedPreferences("user_preferences", Context.MODE_PRIVATE)

    private val _preferencesFlow = MutableStateFlow(loadPreferences())
    val preferencesFlow: StateFlow<UserPreferences> = _preferencesFlow.asStateFlow()

    /**
     * A derived state that is true only if the user is premium AND has connected their Google account.
     * This is the single source of truth for Google connection status in the UI.
     */
    val isGoogleConnected: StateFlow<Boolean> = combine(
        preferencesFlow,
        googleTokenManager.isConnected
    ) { prefs, isConnected ->
        prefs.isPremium && isConnected
    }.stateIn(coroutineScope, SharingStarted.Lazily, false)

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
        val isPremium = sharedPreferences.getBoolean(KEY_IS_PREMIUM, false)

        return UserPreferences(
            maxRunsPerWeek = maxRuns,
            preferredLongRunDays = preferredDays,
            forbiddenRunDays = forbiddenDays,
            progressionRate = progressionRate,
            isPremium = isPremium
        )
    }

    fun savePreferences(preferences: UserPreferences) {
        with(sharedPreferences.edit()) {
            putInt(KEY_MAX_RUNS_PER_WEEK, preferences.maxRunsPerWeek)
            putStringSet(KEY_PREFERRED_LONG_RUN_DAYS, preferences.preferredLongRunDays.map { it.name }.toSet())
            putStringSet(KEY_FORBIDDEN_RUN_DAYS, preferences.forbiddenRunDays.map { it.name }.toSet())
            putString(KEY_PROGRESSION_RATE, preferences.progressionRate.name)
            putBoolean(KEY_IS_PREMIUM, preferences.isPremium)
            
            apply()
        }
        _preferencesFlow.update { preferences }
    }

    fun isPlanValid(preferences: UserPreferences): Boolean {
        val availableDays = DayOfWeek.entries.size - preferences.forbiddenRunDays.size
        if (availableDays < preferences.maxRunsPerWeek) {
            return false
        }
        
        if (preferences.preferredLongRunDays.isNotEmpty()) {
            val possibleLongRunDays = preferences.preferredLongRunDays.filter { it !in preferences.forbiddenRunDays }
            if (possibleLongRunDays.isEmpty()) {
                return false
            }
        }
        
        return true
    }

    companion object {
        private const val KEY_MAX_RUNS_PER_WEEK = "max_runs_per_week"
        private const val KEY_PREFERRED_LONG_RUN_DAYS = "preferred_long_run_days"
        private const val KEY_FORBIDDEN_RUN_DAYS = "forbidden_run_days"
        private const val KEY_PROGRESSION_RATE = "progression_rate"
        private const val KEY_IS_PREMIUM = "is_premium"
    }
}