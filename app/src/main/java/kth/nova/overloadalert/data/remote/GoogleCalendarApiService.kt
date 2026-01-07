package kth.nova.overloadalert.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

interface GoogleCalendarApiService {

    // --- Calendar Methods ---

    @GET("users/me/calendarList")
    suspend fun getCalendarList(): GoogleCalendarList

    @POST("calendars")
    suspend fun createCalendar(
        @Body calendar: GoogleCalendar
    ): GoogleCalendar

    // --- Event Methods ---

    @POST("calendars/{calendarId}/events")
    suspend fun createEvent(
        @Path("calendarId") calendarId: String,
        @Body event: GoogleCalendarEvent
    ): GoogleCalendarEvent

    @PATCH("calendars/{calendarId}/events/{eventId}")
    suspend fun patchEvent(
        @Path("calendarId") calendarId: String,
        @Path("eventId") eventId: String,
        @Body event: GoogleCalendarEvent
    ): GoogleCalendarEvent

    @GET("calendars/{calendarId}/events/{eventId}")
    suspend fun getEvent(
        @Path("calendarId") calendarId: String,
        @Path("eventId") eventId: String
    ): GoogleCalendarEvent

    @DELETE("calendars/{calendarId}/events/{eventId}")
    suspend fun deleteEvent(
        @Path("calendarId") calendarId: String,
        @Path("eventId") eventId: String
    ): Response<Unit>
}