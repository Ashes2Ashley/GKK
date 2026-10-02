package com.example.data.api.adapter

import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonQualifier
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.ToJson
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Custom Moshi Adapter for ISO-8601 UTC date string serialization & deserialization.
 */
class IsoDateAdapter {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private val fallbackFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    @FromJson
    @Synchronized
    fun fromJson(json: String?): Date? {
        if (json.isNullOrBlank()) return null
        return try {
            dateFormat.parse(json)
        } catch (_: Exception) {
            try {
                fallbackFormat.parse(json)
            } catch (_: Exception) {
                null
            }
        }
    }

    @ToJson
    @Synchronized
    fun toJson(date: Date?): String? {
        if (date == null) return null
        return dateFormat.format(date)
    }
}

/**
 * Qualifier annotation for Color hex strings (#RRGGBB or #AARRGGBB).
 */
@JsonQualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class HexColor

/**
 * Moshi Adapter converting between 0xRRGGBB Long and "#RRGGBB" String.
 */
class HexColorAdapter {
    @ToJson
    fun toJson(@HexColor colorLong: Long): String {
        return String.format("#%06X", 0xFFFFFF and colorLong.toInt())
    }

    @FromJson
    @HexColor
    fun fromJson(hex: String): Long {
        return try {
            val clean = hex.removePrefix("#")
            clean.toLong(16)
        } catch (_: Exception) {
            0L
        }
    }
}

/**
 * Moshi Adapter that tolerates strings containing numeric values or nulls for Int fields.
 */
class SafeIntAdapter {
    @FromJson
    fun fromJson(reader: JsonReader): Int {
        return when (reader.peek()) {
            JsonReader.Token.NUMBER -> reader.nextInt()
            JsonReader.Token.STRING -> {
                val str = reader.nextString()
                str.toIntOrNull() ?: 0
            }
            JsonReader.Token.NULL -> {
                reader.nextNull<Unit>()
                0
            }
            else -> {
                reader.skipValue()
                0
            }
        }
    }

    @ToJson
    fun toJson(writer: JsonWriter, value: Int) {
        writer.value(value)
    }
}
