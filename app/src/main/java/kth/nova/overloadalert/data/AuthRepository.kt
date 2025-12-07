package kth.nova.overloadalert.data

import android.net.Uri
import kth.nova.overloadalert.BuildConfig
import kth.nova.overloadalert.data.remote.StravaAuthService

class AuthRepository(
    private val stravaAuthService: StravaAuthService,
    private val tokenManager: TokenManager
) {

    suspend fun exchangeCodeForToken(code: String) {
        val tokenResponse = stravaAuthService.getAccessToken(
            clientId = BuildConfig.STRAVA_CLIENT_ID,
            clientSecret = BuildConfig.STRAVA_CLIENT_SECRET,
            code = code
        )

        // Validate the response from Strava
        if (tokenResponse.accessToken == null || tokenResponse.refreshToken == null || tokenResponse.expiresAt == null) {
            throw Exception("Failed to get valid token from Strava. The authorization code may be invalid or expired.")
        }

        tokenManager.saveAccessToken(tokenResponse.accessToken)
        tokenManager.saveRefreshToken(tokenResponse.refreshToken)
        tokenManager.saveTokenExpiry(tokenResponse.expiresAt)
    }

    fun getIsAuthenticated(): Boolean {
        // A real app would also check if the token is expired and use the refresh token.
        val token = tokenManager.getAccessToken()
        val expiry = tokenManager.getTokenExpiry()
        return token != null && expiry > (System.currentTimeMillis() / 1000)
    }

    fun getAuthUrl(): String {
        // This redirect URI must have a host that matches the "Authorization Callback Domain" on Strava.
        val redirectUri = "overloadalert://localhost"

        return Uri.parse("https://www.strava.com/oauth/authorize")
            .buildUpon()
            .appendQueryParameter("client_id", BuildConfig.STRAVA_CLIENT_ID)
            .appendQueryParameter("redirect_uri", redirectUri)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("approval_prompt", "auto")
            .appendQueryParameter("scope", "activity:read_all")
            .build()
            .toString()
    }

    fun logout() {
        tokenManager.clearTokens()
    }
}