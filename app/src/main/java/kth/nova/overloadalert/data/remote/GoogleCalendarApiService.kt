package kth.nova.overloadalert.data.remote

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface GoogleCalendarApiService {

    @POST("calendars/primary/events")
    suspend fun createEvent(
        @Body event: GoogleCalendarEvent
    ): GoogleCalendarEvent

    @PATCH("calendars/primary/events/{eventId}")
    suspend fun patchEvent(
        @Path("eventId") eventId: String,
        @Body event: GoogleCalendarEvent
    ): GoogleCalendarEvent

    @GET("calendars/primary/events/{eventId}")
    suspend fun getEvent(
        @Path("eventId") eventId: String
    ): GoogleCalendarEvent

    @DELETE("calendars/primary/events/{eventId}")
    suspend fun deleteEvent(
        @Path("eventId") eventId: String
    )
}