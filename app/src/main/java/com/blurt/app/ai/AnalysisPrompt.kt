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

    fun build(content: String, nowEpochMillis: Long): String = buildString {
        append(classificationRules(content, nowEpochMillis))
        append("Respond with a single JSON object: {\"blurts\": [{\"content\": \"the blurt text\", ")
        append("\"intent\": one of ${intents()}, \"category\": one of ${categories()}, ")
        append("\"reminderAt\": ISO-8601 string or null, \"important\": true or false, ")
        append("\"recurrence\": \"NONE\" or \"DAILY\" or \"WEEKLY\"}]}. ")
        append("Blurt: \"$content\"")
    }

    /**
     * The companion-mode prompt: the same classification rules, plus a short
     * spoken [reply] and a [save] decision. Blurts may be an empty array —
     * that is the \"just listening / don't save\" outcome, and the reply
     * acknowledges it.
     */
    fun buildWithReply(content: String, nowEpochMillis: Long): String = buildString {
        append(classificationRules(content, nowEpochMillis))
        append("Also respond with a reply: one short, warm, natural sentence or two that ")
        append("acknowledges what the user said and states what Blurt did — for example ")
        append("\"Got it — Sarah, tomorrow at 3pm, saved with a reminder.\" or \"That sounds rough. ")
        append("I won't save this one.\". Speak like a friend, never robotic, never a list. ")
        append("And decide save: true normally — Blurt auto-saves what the user says. ")
        append("Set save to false ONLY when the user explicitly says not to save (\"don't save this\", ")
        append("\"forget it\", \"keep this between us\", \"just venting\") or when there is genuinely ")
        append("nothing worth keeping — pure venting or complaints with no commitments, facts, ")
        append("requests, or ideas. When save is false, blurts must be an empty array. ")
        append("Respond with a single JSON object: {\"reply\": \"...\", \"save\": true or false, ")
        append("\"blurts\": [{\"content\": \"the blurt text\", \"intent\": one of ${intents()}, ")
        append("\"category\": one of ${categories()}, ")
        append("\"reminderAt\": ISO-8601 string or null, \"important\": true or false, ")
        append("\"recurrence\": \"NONE\" or \"DAILY\" or \"WEEKLY\"}]}. ")
        append("Blurt: \"$content\"")
    }

    /** The classification instructions shared by every prompt variant. */
    private fun classificationRules(content: String, nowEpochMillis: Long): String = buildString {
        val now = OffsetDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(nowEpochMillis),
            ZoneId.systemDefault(),
        )
        append("You are the Blurt capture analyzer. Read a user's blurt and decide what it is. ")
        append("The text may contain several distinct ideas — if it does, split it into ")
        append("separate blurts, one per idea, each a complete standalone sentence. ")
        append("If it is a single thought, return exactly one blurt. ")
        append("For each blurt pick exactly one intent from this fixed list: ${intents()}. ")
        append("NOTE is a passing thought or note to remember; TASK is something to do; ")
        append("IDEA is a thought worth keeping like a suggestion or concept; REMINDER is an ")
        append("explicit \"remind me\" type request (keep REMINDER even when no exact time is given). ")
        append("Also pick exactly one category from this fixed list: ${categories()}. ")
        append("The current date and time is ${now} (the user's local time). ")
        append("If a blurt mentions a specific date or time (for example \"tomorrow at 3pm\", ")
        append("\"next Monday morning\", \"in 2 hours\", \"Friday at 6pm\"), return it as an ")
        append("ISO-8601 timestamp with timezone offset, resolved against the current time. ")
        append("Otherwise return null for reminderAt. Ignore vague references like \"later\" or ")
        append("\"someday\" — only concrete times count. Never invent a time that isn't mentioned. ")
        append("Detect recurring schedules too: the recurrence field must be exactly one ")
        append("uppercase string: \"NONE\", \"DAILY\" or \"WEEKLY\". ")
        append("\"every day\", \"daily\", \"every morning\", \"every evening\", \"every night\" -> DAILY. ")
        append("\"every Wednesday\", \"each Monday\", \"every week on Friday\", \"weekly\" -> WEEKLY. ")
        append("Anything else -> NONE. ")
        append("When a blurt repeats, reminderAt must be the next single occurrence (for DAILY, ")
        append("today or tomorrow at the mentioned time — or the current time if none is given; ")
        append("for WEEKLY, the upcoming weekday at the mentioned time — or the current time ")
        append("if none is given). ")
        append("Set important to true only when the user emphasizes importance (\"don't forget\", ")
        append("\"important\", \"make sure\", \"remember this\", \"priority\"); otherwise false. ")
    }

    private fun intents(): String = CaptureIntent.entries.joinToString(", ") { it.name }

    private fun categories(): String = CaptureCategory.entries.joinToString(", ") { it.name }
}
