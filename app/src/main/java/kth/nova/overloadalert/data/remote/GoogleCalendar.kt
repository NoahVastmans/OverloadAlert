package kth.nova.overloadalert.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Represents a remote Google Calendar resource fetched from the Google Calendar API.
 *
 * This data class is designed for parsing JSON responses via Moshi.
 *
 * @property id The unique identifier of the calendar.
 * @property summary The title or summary of the calendar.
 * @property description The description of the calendar (optional).
 * @property timeZone The time zone of the calendar (e.g., "Europe/Stockholm").
 */
@JsonClass(generateAdapter = true)
data class GoogleCalendar(
    @field:Json(name = "id") val id: String? = null,
    @field:Json(name = "summary") val summary: String,
    @field:Json(name = "description") val description: String? = null,
    @field:Json(name = "timeZone") val timeZone: String
)
