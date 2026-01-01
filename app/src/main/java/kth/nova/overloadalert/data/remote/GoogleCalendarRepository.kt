package kth.nova.overloadalert.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GoogleCalendarRepository(
    private val googleCalendarApiService: GoogleCalendarApiService
) {

    suspend fun createEvent(event: GoogleCalendarEvent): GoogleCalendarEvent = withContext(Dispatchers.IO) {
        googleCalendarApiService.createEvent(event)
    }

    suspend fun patchEvent(eventId: String, event: GoogleCalendarEvent): GoogleCalendarEvent = withContext(Dispatchers.IO) {
        googleCalendarApiService.patchEvent(eventId, event)
    }

    suspend fun getEvent(eventId: String): GoogleCalendarEvent = withContext(Dispatchers.IO) {
        googleCalendarApiService.getEvent(eventId)
    }

    suspend fun deleteEvent(eventId: String) = withContext(Dispatchers.IO) {
        googleCalendarApiService.deleteEvent(eventId)
    }
}