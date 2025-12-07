package kth.nova.overloadalert.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class StravaAthlete(
    @field:Json(name = "id") val id: Long?,
    @field:Json(name = "firstname") val firstname: String?,
    @field:Json(name = "lastname") val lastname: String?
)