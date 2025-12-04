package kth.nova.overloadalert.data.remote

import kth.nova.overloadalert.data.remote.dto.SummaryActivity
import retrofit2.http.GET
import retrofit2.http.Query

interface StravaApi {

    @GET("athlete/activities")
    suspend fun getActivities(
        @Query("before") before: Long? = null,
        @Query("after") after: Long? = null,
        @Query("page") page: Int? = null,
        @Query("per_page") perPage: Int? = null
    ): List<SummaryActivity>

    companion object {
        const val BASE_URL = "https://www.strava.com/api/v3/"
    }
}