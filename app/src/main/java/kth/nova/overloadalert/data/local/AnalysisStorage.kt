package kth.nova.overloadalert.data.local

import android.content.Context
import com.squareup.moshi.Moshi
import kth.nova.overloadalert.domain.model.CachedAnalysis

/**
 * A local storage repository for persisting analysis results using SharedPreferences.
 *
 * This class handles the serialization and deserialization of [CachedAnalysis] objects
 * to and from a JSON string representation, allowing the most recent analysis to be
 * preserved across app sessions.
 *
 * @property context The application context used to access SharedPreferences.
 * @property moshi The Moshi instance used for JSON adaptation.
 */
class AnalysisStorage(context: Context, moshi: Moshi) {

    private val sharedPreferences = context.getSharedPreferences("analysis_storage", Context.MODE_PRIVATE)
    private val adapter = moshi.adapter(CachedAnalysis::class.java)

    fun save(cachedAnalysis: CachedAnalysis) {
        sharedPreferences.edit().putString(KEY_CACHED_ANALYSIS, adapter.toJson(cachedAnalysis)).apply()
    }

    fun load(): CachedAnalysis? {
        val json = sharedPreferences.getString(KEY_CACHED_ANALYSIS, null) ?: return null
        return try {
            adapter.fromJson(json)
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private const val KEY_CACHED_ANALYSIS = "cached_analysis"
    }
}