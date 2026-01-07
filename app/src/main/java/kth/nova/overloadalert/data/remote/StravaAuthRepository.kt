package kth.nova.overloadalert.data.remote

import android.net.Uri
import kth.nova.overloadalert.BuildConfig
import kth.nova.overloadalert.data.remote.StravaTokenManager

/**
 * Repository responsible for handling Strava authentication flows.
 *
 * This class serves as the central point for managing the OAuth2 authentication process with the Strava API.
 * It coordinates between the remote authentication service ([StravaAuthService]) and the local token storage ([StravaTokenManager]).
 *
 * Key responsibilities include:
 * - Generating the authorization URL for user login.
 * - Exchanging the authorization code for access and refresh tokens.
 * - Persisting tokens and managing the user's login state.
 * - Handling user logout by clearing stored credentials.
 *
 * @property stravaAuthService The remote service used to communicate with Strava's OAuth endpoints.
 * @property stravaTokenManager The local manager used to securely store and retrieve authentication tokens.
 */
class StravaAuthRepository(
    private val stravaAuthService: StravaAuthService,
    private val stravaTokenManager: StravaTokenManager
) {

    suspend fun exchangeCodeForToken(code: String) {
        val tokenResponse = stravaAuthService.getAccessToken(
            clientId = BuildConfig.STRAVA_CLIENT_ID,
            clientSecret = BuildConfig.STRAVA_CLIENT_SECRET,
            code = code
        )

        if (tokenResponse.accessToken == null || tokenResponse.refreshToken == null || tokenResponse.expiresAt == null) {
            throw Exception("Failed to get valid token from Strava. The authorization code may be invalid or expired.")
        }

        stravaTokenManager.saveAccessToken(tokenResponse.accessToken)
        stravaTokenManager.saveRefreshToken(tokenResponse.refreshToken)
        stravaTokenManager.saveTokenExpiry(tokenResponse.expiresAt)
    }

    fun getAuthUrl(): String {
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
        stravaTokenManager.clearTokens()
    }
}