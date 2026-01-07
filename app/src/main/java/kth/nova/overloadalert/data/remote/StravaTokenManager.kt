package kth.nova.overloadalert.data.remote

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages the storage and retrieval of Strava authentication tokens and synchronization state.
 *
 * This class uses [androidx.security.crypto.EncryptedSharedPreferences] to securely persist sensitive data such as the
 * OAuth access token, refresh token, and token expiration time. It ensures that credentials
 * remain encrypted at rest.
 *
 * Additionally, it tracks the last successful synchronization timestamp using a reactive
 * [kotlinx.coroutines.flow.MutableStateFlow], allowing UI components or observers to react to changes in sync status.
 *
 * @property context The application context used to initialize the encrypted shared preferences and master key.
 */
class StravaTokenManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "strava_tokens",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    // --- Reactive Last Sync Timestamp ---
    private val _lastSyncTimestamp =
        MutableStateFlow(sharedPreferences.getLong("last_sync_timestamp", 0L))
    val lastSyncTimestamp = _lastSyncTimestamp.asStateFlow()

    fun saveLastSyncTimestamp(timestamp: Long) {
        sharedPreferences.edit().putLong("last_sync_timestamp", timestamp).apply()
        _lastSyncTimestamp.value = timestamp
    }
    // --------------------------------

    fun saveAccessToken(token: String) {
        sharedPreferences.edit().putString("access_token", token).apply()
    }

    fun getAccessToken(): String? {
        return sharedPreferences.getString("access_token", null)
    }

    fun saveRefreshToken(token: String) {
        sharedPreferences.edit().putString("refresh_token", token).apply()
    }

    fun getRefreshToken(): String? {
        return sharedPreferences.getString("refresh_token", null)
    }

    fun saveTokenExpiry(expiresAt: Long) {
        sharedPreferences.edit().putLong("expires_at", expiresAt).apply()
    }

    fun getTokenExpiry(): Long {
        return sharedPreferences.getLong("expires_at", 0L)
    }

    fun clearTokens() {
        sharedPreferences.edit().clear().apply()
        _lastSyncTimestamp.value = 0L // Also clear the timestamp
    }
}