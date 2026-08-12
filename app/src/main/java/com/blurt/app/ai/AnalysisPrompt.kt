package com.blurt.app.ai

import com.blurt.app.data.model.CaptureCategory
import com.blurt.app.data.model.CaptureIntent
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * The one prompt every analyzer provider (Gemini, Groq, …) sends. Keeping it
 * shared means the classification behavior stays identical no matter which
 * backend answers — only the transport differs.
 */
object AnalysisPrompt {

    fun build(content: String, nowEpochMillis: Long): String {
        val now = OffsetDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(nowEpochMillis),
            ZoneId.systemDefault(),
        )
        val categories = CaptureCategory.entries.joinToString(", ") { it.name }
        val intents = CaptureIntent.entries.joinToString(", ") { it.name }
        return buildString {
            append("You are the Blurt capture analyzer. Read a user's blurt and decide what it is. ")
            append("Pick exactly one intent from this fixed list: $intents. ")
            append("NOTE is a passing thought or note to remember; TASK is something to do; ")
            append("IDEA is a thought worth keeping like a suggestion or concept; REMINDER is an ")
            append("explicit \"remind me\" type request (keep REMINDER even when no exact time is given). ")
            append("Also pick exactly one category from this fixed list: $categories. ")
            append("The current date and time is ${now} (the user's local time). ")
            append("If the blurt mentions a specific date or time (for example \"tomorrow at 3pm\", ")
            append("\"next Monday morning\", \"in 2 hours\", \"Friday at 6pm\"), return it as an ")
            append("ISO-8601 timestamp with timezone offset, resolved against the current time. ")
            append("Otherwise return null for reminderAt. Ignore vague references like \"later\" or ")
            append("\"someday\" — only concrete times count. Never invent a time that isn't mentioned. ")
            append("Set important to true only when the user emphasizes importance (\"don't forget\", ")
            append("\"important\", \"make sure\", \"remember this\", \"priority\"); otherwise false. ")
            append("Respond with a single JSON object: {\"intent\": one of $intents, ")
            append("\"category\": one of $categories, \"reminderAt\": ISO-8601 string or null, ")
            append("\"important\": true or false}. ")
            append("Blurt: \"$content\"")
        }
    }
}
