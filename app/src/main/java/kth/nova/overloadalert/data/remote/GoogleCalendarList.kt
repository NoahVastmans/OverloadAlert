package kth.nova.overloadalert.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Represents the response from the Google CalendarList API.
 */
@JsonClass(generateAdapter = true)
data class GoogleCalendarList(
    @field:Json(name = "items") val items: List<GoogleCalendarListEntry>
)

@JsonClass(generateAdapter = true)
data class GoogleCalendarListEntry(
    @field:Json(name = "id") val id: String,
    @field:Json(name = "summary") val summary: String
)
