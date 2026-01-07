package kth.nova.overloadalert.data.repository

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kth.nova.overloadalert.data.remote.GoogleTokenManager
import kth.nova.overloadalert.domain.plan.ProgressionRate
import kth.nova.overloadalert.domain.plan.UserPreferences
import kth.nova.overloadalert.domain.repository.PreferencesRepository
import java.time.DayOfWeek

/**
 * Repository responsible for managing and persisting user preferences for the training plan.
 *
 * This class handles reading from and writing to the Android [SharedPreferences]. It provides
 * a reactive [kotlinx.coroutines.flow.StateFlow] of [UserPreferences] to allow observers to react to changes in settings
 * (e.g., maximum runs per week, forbidden days, premium status).
 *
 * Additionally, it combines user preferences with the Google Token Manager state to determine
 * the effective Google connection status (only considered connected if the user is also Premium).
 *
 * @property context The Android application context used to access SharedPreferences.
 * @property googleTokenManager Manager for handling Google authentication tokens.
 * @property coroutineScope The scope used for collecting flows and sharing state, defaults to [kotlinx.coroutines.Dispatchers.Default].
 */
class PreferencesRepositoryImpl(
    context: Context,
    googleTokenManager: GoogleTokenManager,
    coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) : PreferencesRepository {

    private val sharedPreferences = context.getSharedPreferences("user_preferences", Context.MODE_PRIVATE)

    // In-memory cache of preferences to allow instant access and reactive updates.
    // Initialized by reading from disk on creation.
    private val _preferencesFlow = MutableStateFlow(loadPreferences())
    override val preferencesFlow: StateFlow<UserPreferences> = _preferencesFlow.asStateFlow()

    /**
     * A derived state that is true only if the user is premium AND has connected their Google account.
     * This is the single source of truth for Google connection status in the UI.
     */
    override val isGoogleConnected: StateFlow<Boolean> = combine(
        preferencesFlow,
        googleTokenManager.isConnected
    ) { prefs, isConnected ->
        // Google Calendar sync is a premium-only feature. Even if the token exists,
        // we treat it as disconnected if the user doesn't have premium.
        prefs.isPremium && isConnected
    }.stateIn(coroutineScope, SharingStarted.Lazily, false)

    private fun loadPreferences(): UserPreferences {
        // Load primitive types and collections from SharedPreferences with safe defaults.
        val maxRuns = sharedPreferences.getInt(KEY_MAX_RUNS_PER_WEEK, 5)
        
        // Enum and complex types require manual parsing and error handling.
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

    override fun savePreferences(preferences: UserPreferences) {
        // Commit changes to disk asynchronously via apply()
        with(sharedPreferences.edit()) {
            putInt(KEY_MAX_RUNS_PER_WEEK, preferences.maxRunsPerWeek)
            putStringSet(KEY_PREFERRED_LONG_RUN_DAYS, preferences.preferredLongRunDays.map { it.name }.toSet())
            putStringSet(KEY_FORBIDDEN_RUN_DAYS, preferences.forbiddenRunDays.map { it.name }.toSet())
            putString(KEY_PROGRESSION_RATE, preferences.progressionRate.name)
            putBoolean(KEY_IS_PREMIUM, preferences.isPremium)
            
            apply()
        }
        // Immediately update the flow so UI updates without waiting for disk write
        _preferencesFlow.update { preferences }
    }

    override fun isPlanValid(preferences: UserPreferences): Boolean {
        // Validation logic to ensure the plan generator doesn't crash or produce impossible schedules.
        
        // 1. Ensure there are enough allowed days to fit the requested number of runs.
        val availableDays = DayOfWeek.entries.size - preferences.forbiddenRunDays.size
        if (availableDays < preferences.maxRunsPerWeek) {
            return false
        }
        
        // 2. If the user specified preferred long run days, ensure at least one of them is not forbidden.
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
