package kth.nova.overloadalert.data

import androidx.compose.ui.graphics.Color
import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.ToJson
import kth.nova.overloadalert.domain.model.CombinedRisk
import java.time.LocalDate

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