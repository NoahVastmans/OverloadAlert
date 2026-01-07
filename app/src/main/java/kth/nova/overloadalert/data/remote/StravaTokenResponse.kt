package kth.nova.overloadalert.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Represents the response received from the Strava API during the OAuth token exchange or refresh process.
 *
 * This data class maps the JSON response containing access tokens, refresh tokens, and token expiration details,
 * as well as information about the authenticated athlete.
 *
 * @property tokenType The type of token returned (typically "Bearer").
 * @property expiresAt The timestamp (in seconds since the epoch) when the access token expires.
 * @property expiresIn The number of seconds until the access token expires.
 * @property refreshToken The token used to obtain a new access token when the current one expires.
 * @property accessToken The short-lived token used to authenticate API requests on behalf of the athlete.
 * @property athlete The athlete profile associated with the token. Note that this may be null during a token refresh.
 */
@JsonClass(generateAdapter = true)
data class StravaTokenResponse(
    @field:Json(name = "token_type") val tokenType: String?,
    @field:Json(name = "expires_at") val expiresAt: Long?,
    @field:Json(name = "expires_in") val expiresIn: Long?,
    @field:Json(name = "refresh_token") val refreshToken: String?,
    @field:Json(name = "access_token") val accessToken: String?,
    @field:Json(name = "athlete") val athlete: StravaAthlete?
)