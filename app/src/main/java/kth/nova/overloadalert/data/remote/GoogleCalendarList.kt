package kth.nova.overloadalert.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Represents the response from the Google Calendar API's calendarList.list method.
 *
 * This data class models the JSON structure returned when fetching the list of calendars
 * present on a user's calendar list. It contains a list of [GoogleCalendarListEntry] items.
 *
 * @property items A list of calendar entries found in the user's calendar list.
 */
@JsonClass(generateAdapter = true)
data class GoogleCalendarList(
    @field:Json(name = "items") val items: List<GoogleCalendarListEntry>
)

/**
 * Represents a single calendar entry within a Google Calendar list.
 *
 * This data class maps to the JSON object returned for an individual calendar
 * in the "items" array of the Google Calendar List API response.
 *
 * @property id The unique identifier of the calendar.
 * @property summary The title or summary of the calendar.
 */
@JsonClass(generateAdapter = true)
data class GoogleCalendarListEntry(
    @field:Json(name = "id") val id: String,
    @field:Json(name = "summary") val summary: String
)
