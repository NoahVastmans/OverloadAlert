package kth.nova.overloadalert.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Data transfer object representing a single activity fetched from the Strava API.
 *
 * This class is used to deserialize JSON responses from the Strava `GET /athlete/activities` endpoint.
 * It uses Moshi for JSON parsing.
 *
 * @property id The unique identifier for the activity.
 * @property distance The total distance covered during the activity, in meters.
 * @property startDateLocal The time at which the activity was started, in local time, formatted as an ISO 8601 string (e.g., "2018-02-16T06:52:16Z").
 * @property movingTime The active moving time during the activity, in seconds.
 * @property type The type of the activity (e.g., "Run", "Ride", "Swim").
 */
@JsonClass(generateAdapter = true)
data class StravaActivityResponse(
    @field:Json(name = "id") val id: Long,
    @field:Json(name = "distance") val distance: Float, // in meters
    @field:Json(name = "start_date_local") val startDateLocal: String, // ISO 8601 format
    @field:Json(name = "moving_time") val movingTime: Int, // in seconds
    @field:Json(name = "type") val type: String
)