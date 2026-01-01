package kth.nova.overloadalert.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Represents a Google Calendar resource.
 */
@JsonClass(generateAdapter = true)
data class GoogleCalendar(
    @field:Json(name = "id") val id: String? = null,
    @field:Json(name = "summary") val summary: String,
    @field:Json(name = "description") val description: String? = null,
    @field:Json(name = "timeZone") val timeZone: String
)
