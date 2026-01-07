package kth.nova.overloadalert.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit service interface for interacting with the Strava API.
 * This service handles HTTP requests to retrieve athlete data and activities.
 */
interface StravaApiService {

    @GET("athlete/activities")
    suspend fun getActivities(
        @Query("before") before: Long, // Epoch seconds
        @Query("after") after: Long,   // Epoch seconds
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 30
    ): List<StravaActivityResponse>
}

/**
 * Represents a Strava athlete's profile information retrieved from the Strava API.
 *
 * This data class maps to the JSON object returned by athlete-related endpoints.
 * It uses Moshi for JSON parsing.
 *
 * @property id The unique identifier for the athlete in the Strava system.
 * @property firstname The athlete's first name.
 * @property lastname The athlete's last name.
 */
@JsonClass(generateAdapter = true)
data class StravaAthlete(
    @field:Json(name = "id") val id: Long?,
    @field:Json(name = "firstname") val firstname: String?,
    @field:Json(name = "lastname") val lastname: String?
)