package kth.nova.overloadalert.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

interface StravaApiService {

    @GET("athlete/activities")
    suspend fun getActivities(
        @Query("before") before: Long, // Epoch seconds
        @Query("after") after: Long,   // Epoch seconds
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 30
    ): List<StravaActivityResponse>
}