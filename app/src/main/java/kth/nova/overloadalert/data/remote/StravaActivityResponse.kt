package kth.nova.overloadalert.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class StravaActivityResponse(
    @field:Json(name = "id") val id: Long,
    @field:Json(name = "distance") val distance: Float, // in meters
    @field:Json(name = "start_date_local") val startDateLocal: String, // ISO 8601 format
    @field:Json(name = "moving_time") val movingTime: Int, // in seconds
    @field:Json(name = "type") val type: String
)