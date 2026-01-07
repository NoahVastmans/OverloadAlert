package kth.nova.overloadalert.data.adapter

import androidx.compose.ui.graphics.Color
import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.ToJson
import kth.nova.overloadalert.domain.model.CombinedRisk
import java.time.LocalDate

/**
 * A Moshi adapter for serializing and deserializing [java.time.LocalDate] objects.
 *
 * This adapter handles the conversion between [java.time.LocalDate] instances and their String representation
 * (ISO-8601 format, e.g., "2023-10-27") for JSON processing.
 */
class LocalDateAdapter {
    @ToJson
    fun toJson(value: LocalDate): String {
        return value.toString()
    }

    @FromJson
    fun fromJson(value: String): LocalDate {
        return LocalDate.parse(value)
    }
}

/**
 * A custom Moshi adapter for serializing and deserializing [kth.nova.overloadalert.domain.model.CombinedRisk] objects.
 *
 * This adapter handles the conversion between the `CombinedRisk` domain model and its JSON representation.
 * It specifically manages the [androidx.compose.ui.graphics.Color] property by converting its ARGB value to a `Long` for storage
 * and reconstructing the [androidx.compose.ui.graphics.Color] object from that `Long` value upon retrieval.
 */
class CombinedRiskAdapter {

    @ToJson
    fun toJson(writer: JsonWriter, value: CombinedRisk?) {
        if (value == null) {
            writer.nullValue()
            return
        }

        writer.beginObject()
        writer.name("title").value(value.title)
        writer.name("message").value(value.message)
        writer.name("color").value(value.color.value.toLong())
        writer.endObject()
    }

    @FromJson
    fun fromJson(reader: JsonReader): CombinedRisk? {
        var title: String? = null
        var message: String? = null
        var color: Color? = null

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "title" -> title = reader.nextString()
                "message" -> message = reader.nextString()
                "color" -> color = Color(reader.nextLong().toULong())
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        return if (title != null && message != null && color != null) {
            CombinedRisk(title, message, color)
        } else null
    }
}