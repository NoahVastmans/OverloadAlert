package kth.nova.overloadalert.data.remote

import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

/**
 * Retrofit service interface for handling Strava OAuth2 authentication.
 *
 * This interface defines the endpoints required to obtain and refresh access tokens
 * from the Strava API. It is used to authenticate the user and maintain a valid session.
 *
 * @see [Strava Authentication Documentation](https://developers.strava.com/docs/authentication/)
 */
interface StravaAuthService {

    @FormUrlEncoded
    @POST("oauth/token")
    suspend fun getAccessToken(
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String,
        @Field("code") code: String,
        @Field("grant_type") grantType: String = "authorization_code"
    ): StravaTokenResponse

    @FormUrlEncoded
    @POST("oauth/token")
    fun refreshToken(
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String,
        @Field("refresh_token") refreshToken: String,
        @Field("grant_type") grantType: String = "refresh_token"
    ): retrofit2.Call<StravaTokenResponse>
}