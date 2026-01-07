package kth.nova.overloadalert.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Represents a specific event retrieved from the Google Calendar API.
 *
 * This data class models the structure of an event resource within the Google Calendar API JSON response.
 * It is intended for serialization and deserialization using Moshi.
 *
 * @property id The unique identifier for the event.
 * @property status The status of the event (e.g., "confirmed", "tentative", "cancelled").
 * @property summary The title or summary of the event.
 * @property description A description of the event.
 * @property start The start time or date of the event.
 * @property end The end time or date of the event.
 * @property reminders Information about the event's reminders (notifications).
 */
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

/**
 * Represents the timing information for a Google Calendar event.
 *
 * This class handles both all-day events (specified by [date]) and timed events
 * (specified by [dateTime]). It mirrors the structure of the "start" and "end"
 * resources in the Google Calendar API.
 *
 * @property date The date of the event in "YYYY-MM-DD" format. This is present only for all-day events.
 * @property dateTime The time of the event in RFC3339 format (e.g., "2023-10-25T14:00:00Z"). This is present only for timed events.
 * @property timeZone The time zone in which the time is specified (e.g., "Europe/Stockholm"). This is optional and usually accompanies [dateTime].
 */
@JsonClass(generateAdapter = true)
data class EventDateTime(
    @field:Json(name = "date") val date: String? = null, // "YYYY-MM-DD" for all-day
    @field:Json(name = "dateTime") val dateTime: String? = null, // RFC3339 for timed
    @field:Json(name = "timeZone") val timeZone: String? = null
)

/**
 * Represents the reminder configuration for a Google Calendar event.
 *
 * This data class mirrors the structure of the "reminders" object in the Google Calendar API resource.
 * It specifies whether default reminders should be used or if specific overrides are defined.
 *
 * @property useDefault Whether the default reminders of the calendar list entry apply to the event.
 * @property overrides A list of specific reminder overrides. If [useDefault] is true, this field is typically ignored or null.
 */
@JsonClass(generateAdapter = true)
data class Reminders(
    @field:Json(name = "useDefault") val useDefault: Boolean = true,
    @field:Json(name = "overrides") val overrides: List<ReminderOverride>? = null
)

/**
 * Represents a specific reminder override configuration for a Google Calendar event.
 *
 * This data class defines a custom reminder method (e.g., "popup" or "email") and the
 * timing trigger relative to the event's start time. It corresponds to the `overrides`
 * object within the Google Calendar API's `reminders` resource.
 *
 * @property method The method used to remind the user (e.g., "email", "popup").
 * @property minutes The number of minutes before the start of the event when the reminder should trigger.
 */
@JsonClass(generateAdapter = true)
data class ReminderOverride(
    @field:Json(name = "method") val method: String,
    @field:Json(name = "minutes") val minutes: Int
)