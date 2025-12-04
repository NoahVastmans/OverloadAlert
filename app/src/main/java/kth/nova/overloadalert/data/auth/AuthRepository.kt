package kth.nova.overloadalert.data.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "auth")

@Singleton
class AuthRepository @Inject constructor(@ApplicationContext private val context: Context) {

    private object PreferencesKeys {
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val ACCESS_TOKEN_EXPIRATION_TIME = longPreferencesKey("access_token_expiration_time")
    }

    val tokenData: Flow<TokenData?> = context.dataStore.data.map {
        val accessToken = it[PreferencesKeys.ACCESS_TOKEN]
        val refreshToken = it[PreferencesKeys.REFRESH_TOKEN]
        val expirationTime = it[PreferencesKeys.ACCESS_TOKEN_EXPIRATION_TIME]

        if (accessToken != null && refreshToken != null && expirationTime != null) {
            TokenData(accessToken, refreshToken, expirationTime)
        } else {
            null
        }
    }

    suspend fun saveTokens(tokenData: TokenData) {
        context.dataStore.edit {
            it[PreferencesKeys.ACCESS_TOKEN] = tokenData.accessToken
            it[PreferencesKeys.REFRESH_TOKEN] = tokenData.refreshToken
            it[PreferencesKeys.ACCESS_TOKEN_EXPIRATION_TIME] = tokenData.accessTokenExpirationTime
        }
    }

    suspend fun clearTokens() {
        context.dataStore.edit {
            it.clear()
        }
    }
}