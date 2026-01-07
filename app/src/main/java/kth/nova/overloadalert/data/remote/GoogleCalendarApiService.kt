package kth.nova.overloadalert.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Retrofit service interface for interacting with the Google Calendar API.
 *
 * This service provides methods to manage calendars (list, create) and calendar events
 * (create, retrieve, update, delete). It handles network requests to the underlying
 * Google Calendar REST endpoints.
 *
 * All functions are suspending functions, designed to be called from a coroutine scope.
 *
 * @see <a href="https://developers.google.com/calendar/api/v3/reference">Google Calendar API Reference</a>
 */
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