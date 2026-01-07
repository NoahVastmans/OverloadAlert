package kth.nova.overloadalert.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GoogleCalendarEvent(
    @field:Json(name = "id") val id: String? = null,
    @field:Json(name = "status") val status: String? = null, // Added status field
    @field:Json(name = "summary") val summary: String? = null,
    @field:Json(name = "description") val description: String? = null,
    @field:Json(name = "start") val start: EventDateTime? = null,
    @field:Json(name = "end") val end: EventDateTime? = null,
    @field:Json(name = "reminders") val reminders: Reminders? = null
)

@JsonClass(generateAdapter = true)
data class EventDateTime(
    @field:Json(name = "date") val date: String? = null, // "YYYY-MM-DD" for all-day
    @field:Json(name = "dateTime") val dateTime: String? = null, // RFC3339 for timed
    @field:Json(name = "timeZone") val timeZone: String? = null
)

@JsonClass(generateAdapter = true)
data class Reminders(
    @field:Json(name = "useDefault") val useDefault: Boolean = true,
    @field:Json(name = "overrides") val overrides: List<ReminderOverride>? = null
)

@JsonClass(generateAdapter = true)
data class ReminderOverride(
    @field:Json(name = "method") val method: String,
    @field:Json(name = "minutes") val minutes: Int
)