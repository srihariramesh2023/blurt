package com.blurt.app.ai

import com.blurt.app.data.model.CaptureCategory
import com.blurt.app.data.model.CaptureIntent
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException
import org.json.JSONObject

/**
 * Parses the model's JSON analysis — shared by every [CaptureAnalyzer]
 * implementation (Gemini and Groq produce the same JSON shape: intent,
 * category, reminderAt, important). Pure and unit-tested; the network and
 * the parsing stay separate.
 */
object CaptureAnalysisParser {

    /**
     * Parses the inner JSON object a model returned. Returns null for any
     * malformed or incomplete output — never throws. A null result means
     * "no analysis", and the caller saves the blurt unclassified.
     */
    fun parse(jsonText: String): CaptureAnalysis? {
        return try {
            val analysis = JSONObject(jsonText)
            val intent = runCatching { CaptureIntent.valueOf(analysis.optString("intent", "")) }
                .getOrNull() ?: return null
            val category = runCatching { CaptureCategory.valueOf(analysis.optString("category", "")) }
                .getOrNull() ?: return null
            val reminder = analysis.optString("reminderAt", "null")
            val reminderAt = if (reminder.isBlank() || reminder == "null") null
            else parseIsoTime(reminder) ?: return null
            CaptureAnalysis(
                intent = intent,
                category = category,
                reminderAt = reminderAt,
                important = analysis.optBoolean("important", false),
            )
        } catch (_: Exception) {
            // Malformed/garbage responses are "no analysis" — never a crash.
            null
        }
    }

    private fun parseIsoTime(value: String): Long? {
        val text = value.trim()
        return try {
            OffsetDateTime.parse(text).toInstant().toEpochMilli()
        } catch (_: DateTimeParseException) {
            // A naive timestamp ("2026-08-12T15:00:00") is assumed to be
            // the device's local time.
            try {
                java.time.LocalDateTime.parse(text)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
            } catch (_: DateTimeParseException) {
                null
            }
        }
    }
}
