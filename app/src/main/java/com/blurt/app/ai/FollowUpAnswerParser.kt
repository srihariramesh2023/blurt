package com.blurt.app.ai

/**
 * Parses the user's spoken answer to Blurt's follow-up question
 * ("Want me to remind you 15 minutes before?") into a plain yes/no.
 *
 * Deliberately deterministic — no AI call, no latency, no quota. The
 * follow-up must never cost a second API round-trip or a model that might
 * waffle; a few honest phrases decide it. Anything unclear reads as "no"
 * (the safe default: never surprise the user with an extra alarm).
 */
object FollowUpAnswerParser {

    /** True when the user clearly said they want the heads-up reminder. */
    fun parse(text: String?): Boolean {
        val t = text?.lowercase()?.trim().orEmpty()
        if (t.isBlank()) return false

        // Decline first — "no" inside "not needed" must win over a trailing
        // "sure" ("no thanks" is a no, not a yes).
        val declines = listOf(
            "no", "nah", "nope", "don't", "dont", "not needed", "no thanks",
            "skip", "not really", "never mind", "unnecessary", "no need",
        )
        if (declines.any { t.contains(it) }) return false

        val accepts = listOf(
            "yes", "yeah", "yep", "sure", "okay", "ok", "please", "go ahead",
            "do it", "of course", "definitely", "sounds good", "fine",
        )
        return accepts.any { t.contains(it) }
    }
}
