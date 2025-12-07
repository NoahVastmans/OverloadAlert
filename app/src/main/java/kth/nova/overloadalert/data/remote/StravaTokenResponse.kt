package kth.nova.overloadalert.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class StravaTokenResponse(
    @field:Json(name = "token_type") val tokenType: String?,
    @field:Json(name = "expires_at") val expiresAt: Long?,
    @field:Json(name = "expires_in") val expiresIn: Long?,
    @field:Json(name = "refresh_token") val refreshToken: String?,
    @field:Json(name = "access_token") val accessToken: String?,
    @field:Json(name = "athlete") val athlete: StravaAthlete?
)