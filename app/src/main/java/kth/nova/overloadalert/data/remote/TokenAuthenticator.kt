package kth.nova.overloadalert.data.remote

import kth.nova.overloadalert.BuildConfig
import kth.nova.overloadalert.data.TokenManager
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

class TokenAuthenticator(
    private val tokenManager: TokenManager,
    private val stravaAuthService: StravaAuthService
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        // We need to have a refresh token to continue
        val refreshToken = tokenManager.getRefreshToken() ?: return null

        synchronized(this) {
            val currentAccessToken = tokenManager.getAccessToken()

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
                    tokenManager.saveAccessToken(newTokens.accessToken)
                    tokenManager.saveRefreshToken(newTokens.refreshToken)
                    tokenManager.saveTokenExpiry(newTokens.expiresAt)

                    // Retry the original request with the new token
                    return response.request().newBuilder()
                        .header("Authorization", "Bearer ${newTokens.accessToken}")
                        .build()
                }
            }

            // If the refresh call failed for any reason, log the user out by clearing tokens
            tokenManager.clearTokens()
            return null // This will cancel the request chain
        }
    }
}