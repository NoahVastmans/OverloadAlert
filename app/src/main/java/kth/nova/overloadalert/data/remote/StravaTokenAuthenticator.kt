package kth.nova.overloadalert.data.remote

import kth.nova.overloadalert.BuildConfig
import kth.nova.overloadalert.data.remote.StravaTokenManager
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * An [Authenticator] implementation responsible for automatically refreshing expired Strava access tokens.
 *
 * This class intercepts HTTP 401 Unauthorized responses from the Strava API. When such a response occurs,
 * it attempts to obtain a new access token using the stored refresh token. If successful, the new
 * tokens are persisted via [StravaTokenManager] and the original request is retried with the new credentials.
 *
 * This process handles concurrency by synchronizing token refresh operations, ensuring that multiple
 * concurrent requests do not trigger multiple refresh calls simultaneously.
 *
 * @property stravaTokenManager The manager responsible for storing and retrieving auth tokens.
 * @property stravaAuthService The service used to execute the token refresh API call.
 */
class StravaTokenAuthenticator(
    private val stravaTokenManager: StravaTokenManager,
    private val stravaAuthService: StravaAuthService
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        // We need to have a refresh token to continue
        val refreshToken = stravaTokenManager.getRefreshToken() ?: return null

        synchronized(this) {
            val currentAccessToken = stravaTokenManager.getAccessToken()

            // If the token was refreshed by another request while this one was waiting, use the new token
            if (response.request().header("Authorization") != "Bearer $currentAccessToken") {
                return response.request().newBuilder()
                    .header("Authorization", "Bearer $currentAccessToken")
                    .build()
            }

            // Execute the refresh token call synchronously
            val tokenResponse = stravaAuthService.refreshToken(
                clientId = BuildConfig.STRAVA_CLIENT_ID,
                clientSecret = BuildConfig.STRAVA_CLIENT_SECRET,
                refreshToken = refreshToken
            ).execute()

            if (tokenResponse.isSuccessful && tokenResponse.body() != null) {
                val newTokens = tokenResponse.body()!!
                if (newTokens.accessToken != null && newTokens.refreshToken != null && newTokens.expiresAt != null) {
                    // Persist the new tokens
                    stravaTokenManager.saveAccessToken(newTokens.accessToken)
                    stravaTokenManager.saveRefreshToken(newTokens.refreshToken)
                    stravaTokenManager.saveTokenExpiry(newTokens.expiresAt)

                    // Retry the original request with the new token
                    return response.request().newBuilder()
                        .header("Authorization", "Bearer ${newTokens.accessToken}")
                        .build()
                }
            }

            // If the refresh call failed for any reason, log the user out by clearing tokens
            stravaTokenManager.clearTokens()
            return null // This will cancel the request chain
        }
    }
}