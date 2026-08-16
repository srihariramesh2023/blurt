package com.blurt.app.ai

import com.blurt.app.data.model.CaptureCategory
import com.blurt.app.data.model.CaptureIntent
import com.blurt.app.data.model.Recurrence
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException
import org.json.JSONObject

/**
 * Parses the model's JSON analysis — shared by every [CaptureAnalyzer]
 * implementation (Gemini and Groq produce the same JSON shape: a `blurts`
 * array, each entry with content, intent, category, reminderAt, important
 * and recurrence). Pure and unit-tested; the network and the parsing stay
 * separate.
 */
object CaptureAnalysisParser {

    /**
     * Parses the inner JSON object a model returned. Returns null for any
     * malformed or incomplete output — never throws. A null result means
     * "no analysis", and the caller saves the blurt unclassified. The legacy
     * single-object shape (no `blurts` array) is still accepted so cached
     * responses and old tests keep working.
     */
    fun parse(jsonText: String, now: Long = System.currentTimeMillis()): List<CaptureAnalysis>? {
        return try {
            val root = JSONObject(jsonText)
            val raw = root.optJSONArray("blurts")
            val results = if (raw != null && raw.length() > 0) {
                (0 until raw.length()).mapNotNull { i -> parseOne(raw.optJSONObject(i), now) }
            } else {
                // Pre-split shape: the object itself is the one analysis.
                listOfNotNull(parseOne(root, now))
            }
            results.ifEmpty { null }
        } catch (_: Exception) {
            // Malformed/garbage responses are "no analysis" — never a crash.
            null
        }
    }

    /**
     * Parses the companion-mode JSON — `{"reply": "...", "save": bool,
     * "blurts": [...]}`. The legacy shape (no reply/save fields) still
     * parses: reply is null, save is true, blurts are the one analysis. An
     * explicitly empty `blurts` array is a valid result — that is the
     * "just listening / don't save" outcome. Returns null only for malformed
     * or unparseable output.
     */
    fun parseWithReply(jsonText: String, now: Long = System.currentTimeMillis()): ConversationResult? {
        return try {
            val root = JSONObject(jsonText)
            val reply = root.optString("reply").takeIf { it.isNotBlank() }
            val save = if (root.has("save")) root.optBoolean("save", true) else true
            val raw = root.optJSONArray("blurts")
            val analyses = if (raw != null) {
                (0 until raw.length()).mapNotNull { i -> parseOne(raw.optJSONObject(i), now) }
            } else {
                // Pre-split / legacy shape: the object itself is the analysis.
                listOfNotNull(parseOne(root, now))
            }
            if (raw == null && analyses.isEmpty()) {
                // The legacy shape with nothing parseable is "no analysis".
                null
            } else {
                ConversationResult(reply = reply, save = save, analyses = analyses)
            }
        } catch (_: Exception) {
            // Malformed/garbage responses are "no analysis" — never a crash.
            null
        }
    }

    private fun parseOne(obj: JSONObject?, now: Long): CaptureAnalysis? {
        if (obj == null) return null
        val intent = runCatching { CaptureIntent.valueOf(obj.optString("intent", "")) }
            .getOrNull() ?: return null
        val category = runCatching { CaptureCategory.valueOf(obj.optString("category", "")) }
            .getOrNull() ?: return null
        val reminder = obj.optString("reminderAt", "null")
        val reminderAt = if (reminder.isBlank() || reminder == "null") null
        // A mangled timestamp shouldn't sink the whole analysis — fall back
        // to the text, then to no reminder.
        else parseIsoTime(reminder) ?: null
        val content = obj.optString("content").takeIf { it.isNotBlank() }
        return CaptureAnalysis(
            intent = intent,
            category = category,
            // When the model dropped or mangled the time, the blurt's own
            // words decide — "tomorrow at 9pm" must never silently become a
            // note with no ask.
            reminderAt = reminderAt ?: inferReminderAtFromText(content, now),
            important = obj.optBoolean("important", false),
            content = content,
            recurrence = parseRecurrence(obj, content),
        )
    }

    /**
     * Reads the recurrence, tolerating models that mangle the enum:
     * lowercase ("daily"), spelled out ("every day"), or missing entirely.
     * When the field says nothing useful, the blurt text itself decides —
     * "every morning", "every day", "each Monday", … → DAILY/WEEKLY. A
     * deterministic fallback beats a silent "one-shot" when the user
     * clearly asked for a repeat.
     */
    private fun parseRecurrence(obj: JSONObject, content: String?): Recurrence {
        val field = obj.optString("recurrence", "NONE").trim()
        val fromField = when (field.lowercase()) {
            "daily", "every day", "everyday", "each day", "daily reminder" -> Recurrence.DAILY
            "weekly", "every week", "each week", "weekly reminder" -> Recurrence.WEEKLY
            else -> runCatching { Recurrence.valueOf(field) }.getOrDefault(Recurrence.NONE)
        }
        if (fromField != Recurrence.NONE) return fromField
        return inferRecurrenceFromText(content)
    }

    /** Pattern-matches a blurt for clear recurrence phrasing. */
    fun inferRecurrenceFromText(text: String?): Recurrence {
        val t = text?.lowercase().orEmpty()
        val dailyPhrases = listOf(
            "every day", "everyday", "every morning", "every evening", "every night",
            "every afternoon", "daily", "each day", "every single day",
        )
        if (dailyPhrases.any { t.contains(it) }) return Recurrence.DAILY
        val weekdays = listOf(
            "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday",
        )
        if (weekdays.any { day -> t.contains("every $day") || t.contains("each $day") }) {
            return Recurrence.WEEKLY
        }
        if (t.contains("every week") || t.contains("weekly")) return Recurrence.WEEKLY
        return Recurrence.NONE
    }

    /**
     * The no-AI path: when no key is set (or the analyzer failed), the text
     * alone can still recognize a concrete time and recurrence — so
     * "school tomorrow at 9pm" asks for a reminder even with zero AI.
     * Intent and category stay null (unclassified); the reminder is real.
     */
    fun localFallback(content: String?, now: Long = System.currentTimeMillis()): CaptureAnalysis? {
        val reminderAt = inferReminderAtFromText(content, now) ?: return null
        return CaptureAnalysis(
            reminderAt = reminderAt,
            content = content,
            recurrence = inferRecurrenceFromText(content),
        )
    }

    /**
     * The safety net for reminder times: when the model returns no (or an
     * unusable) `reminderAt`, the blurt's own phrasing decides. Deterministic
     * patterns — "tomorrow at 9pm", "in 2 hours", "tonight at 8", "on
     * friday at 6pm", "this evening" — resolve against [now] (the device's
     * clock, so tests can pin it). Vague references ("someday", "later")
     * never count. A user who clearly stated a time can't silently lose it.
     */
    fun inferReminderAtFromText(text: String?, now: Long = System.currentTimeMillis()): Long? {
        val t = text?.lowercase()
            ?.replace(Regex("[^a-z0-9: .]+"), " ")
            ?.trim().orEmpty()
        if (t.isBlank()) return null
        val vague = listOf("someday", "some day", "later", "eventually", "one day", "in a while", "whenever", "soon")
        if (vague.any { t.contains(it) }) return null

        val zone = ZoneId.systemDefault()
        val base = LocalDateTime.ofInstant(Instant.ofEpochMilli(now), zone)
        val today = base.toLocalDate()

        // "in N minutes/hours/days/weeks" — an exact relative offset.
        Regex("in (\\d+|an|a) (minute|minutes|hour|hours|day|days|week|weeks)\\b")
            .find(t)?.let { m ->
                val n = when (val g = m.groupValues[1]) {
                    "an", "a" -> 1
                    else -> g.toIntOrNull() ?: return@let
                }
                val minutes = when (m.groupValues[2]) {
                    "minute", "minutes" -> 1L
                    "hour", "hours" -> 60L
                    "day", "days" -> 1440L
                    else -> 10080L
                }
                return base.plusMinutes(n * minutes).atZone(zone).toInstant().toEpochMilli()
            }

        // The clock — 9pm, 9:30 pm, 17:00, or a bare hour only when it's
        // clearly a time ("at 9", "by 8"). Numbers like "911" or "chapter 3"
        // never match.
        val clockMatch = Regex("\\b(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?\\b")
            .findAll(t)
            .firstOrNull { m ->
                val h = m.groupValues[1].toIntOrNull() ?: return@firstOrNull false
                val min = m.groupValues[2].ifBlank { "0" }.toIntOrNull() ?: return@firstOrNull false
                val ap = m.groupValues[3]
                if (h !in 0..23 || min !in 0..59) return@firstOrNull false
                // A bare hour (no am/pm, no colon) needs a time qualifier
                // before it, so "meet 5 people" never becomes 5:00.
                if (ap.isEmpty() && m.groupValues[2].isEmpty()) {
                    val before = t.substring(0, m.range.first).trim()
                    val qualifiers = listOf("at", "by", "until", "before", "for", "around")
                    qualifiers.any { before.endsWith(it) }
                } else {
                    true
                }
            }

        // Time-of-day words give a sensible default when no clock is stated.
        val todDefault = when {
            Regex("\\bmorning\\b").containsMatchIn(t) -> 9 to 0
            Regex("\\bafternoon\\b").containsMatchIn(t) -> 13 to 0
            Regex("\\bevening\\b").containsMatchIn(t) -> 18 to 0
            Regex("\\bnight\\b").containsMatchIn(t) -> 21 to 0
            else -> null
        }
        val clock = clockMatch?.let { m ->
            var h = m.groupValues[1].toInt()
            val min = m.groupValues[2].ifBlank { "0" }.toInt()
            when (m.groupValues[3]) {
                "pm" -> if (h < 12) h += 12
                "am" -> if (h == 12) h = 0
            }
            h to min
        } ?: todDefault ?: return null

        // Which day the phrase points at — defaulting to "the next one".
        val weekdays = listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")
        var day: LocalDate = when {
            Regex("\\btomorrow\\b").containsMatchIn(t) -> today.plusDays(1)
            Regex("\\btonight\\b").containsMatchIn(t) || Regex("\\btoday\\b").containsMatchIn(t) -> today
            else -> {
                val hit = weekdays.firstOrNull { Regex("\\b$it\\b").containsMatchIn(t) }
                if (hit != null) {
                    val target = weekdays.indexOf(hit) + 1 // Monday=1 … Sunday=7
                    var days = (target - base.dayOfWeek.value + 7) % 7
                    if (days == 0) days = 7 // "friday" said on Friday means next Friday
                    today.plusDays(days.toLong())
                } else {
                    today
                }
            }
        }
        var at = LocalDateTime.of(day, java.time.LocalTime.of(clock.first, clock.second))
        // A stated time that already passed ("at 9pm" said at 10pm) rolls to
        // the next occurrence instead of dying silently.
        if (!at.isAfter(base)) {
            at = at.plusDays(1)
        }
        return at.atZone(zone).toInstant().toEpochMilli()
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
