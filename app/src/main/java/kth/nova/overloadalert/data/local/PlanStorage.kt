package kth.nova.overloadalert.data.local

import android.content.Context
import com.squareup.moshi.Moshi
import kth.nova.overloadalert.domain.plan.RiskOverride
import kth.nova.overloadalert.domain.plan.WeeklyTrainingPlan
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class PlanStorage(context: Context, moshi: Moshi) {

    private val sharedPreferences = context.getSharedPreferences("plan_storage", Context.MODE_PRIVATE)
    private val planAdapter = moshi.adapter(WeeklyTrainingPlan::class.java)
    private val overrideAdapter = moshi.adapter(RiskOverride::class.java)

    private val _riskOverrideFlow = MutableStateFlow(loadRiskOverride())
    val riskOverrideFlow = _riskOverrideFlow.asStateFlow()

    fun savePlan(plan: WeeklyTrainingPlan) {
        sharedPreferences.edit().putString(KEY_PLAN, planAdapter.toJson(plan)).apply()
    }

    fun loadPlan(): WeeklyTrainingPlan? {
        val json = sharedPreferences.getString(KEY_PLAN, null) ?: return null
        return try {
            planAdapter.fromJson(json)
        } catch (e: Exception) {
            null
        }
    }

    fun saveRiskOverride(override: RiskOverride?) {
        val json = if (override != null) overrideAdapter.toJson(override) else null
        sharedPreferences.edit().putString(KEY_OVERRIDE, json).apply()
        _riskOverrideFlow.update { override }
    }

    private fun loadRiskOverride(): RiskOverride? {
        val json = sharedPreferences.getString(KEY_OVERRIDE, null) ?: return null
        return try {
            overrideAdapter.fromJson(json)
        } catch (e: Exception) {
            null
        }
    }

    // --- Dedicated Calendar Storage ---

    fun saveCalendarId(id: String) {
        sharedPreferences.edit().putString(KEY_CALENDAR_ID, id).apply()
    }

    fun loadCalendarId(): String? {
        return sharedPreferences.getString(KEY_CALENDAR_ID, null)
    }

    companion object {
        private const val KEY_PLAN = "weekly_training_plan"
        private const val KEY_OVERRIDE = "risk_override"
        private const val KEY_CALENDAR_ID = "google_calendar_id"
    }
}
