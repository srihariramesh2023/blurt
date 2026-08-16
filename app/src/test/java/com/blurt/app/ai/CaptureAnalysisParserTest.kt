package com.blurt.app.ai

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric so org.json is real (the plain JVM stub throws); the clock is
 * pinned: 2026-08-15 (a Saturday) at 10:00 AM local.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CaptureAnalysisParserTest {

/** Pins the parser's clock: 2026-08-15 (a Saturday) at 10:00 AM local. */
private val NOW: Long = LocalDateTime.of(2026, 8, 15, 10, 0)
    .atZone(ZoneId.systemDefault())
    .toInstant()
    .toEpochMilli()

private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int): Long =
    LocalDateTime.of(y, mo, d, h, mi).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

// ---- inferReminderAtFromText — the deterministic time fallback ----

    @Test
    fun `tomorrow at 9pm resolves to tomorrow 21 00`() {
        val got = CaptureAnalysisParser.inferReminderAtFromText("school tomorrow at 9pm", NOW)
        assertEquals(at(2026, 8, 16, 21, 0), got)
    }

    @Test
    fun `tomorrow morning resolves to 9 am`() {
        val got = CaptureAnalysisParser.inferReminderAtFromText("dentist tomorrow morning", NOW)
        assertEquals(at(2026, 8, 16, 9, 0), got)
    }

    @Test
    fun `in 2 hours resolves relative to now`() {
        val got = CaptureAnalysisParser.inferReminderAtFromText("call mom in 2 hours", NOW)
        assertEquals(at(2026, 8, 15, 12, 0), got)
    }

    @Test
    fun `at 9pm said at 10am means today at 21 00`() {
        val got = CaptureAnalysisParser.inferReminderAtFromText("remind me at 9pm", NOW)
        assertEquals(at(2026, 8, 15, 21, 0), got)
    }

    @Test
    fun `past at 8am rolls to tomorrow`() {
        // Said at 10:00 AM — 8 AM today already passed.
        val got = CaptureAnalysisParser.inferReminderAtFromText("wake up at 8am", NOW)
        assertEquals(at(2026, 8, 16, 8, 0), got)
    }

    @Test
    fun `on friday at 6pm resolves to the next friday`() {
        // 2026-08-15 is a Saturday — next Friday is the 21st.
        val got = CaptureAnalysisParser.inferReminderAtFromText("gym on friday at 6pm", NOW)
        assertEquals(at(2026, 8, 21, 18, 0), got)
    }

    @Test
    fun `friday said on friday rolls to next week`() {
        val fridayNow = LocalDateTime.of(2026, 8, 21, 10, 0)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val got = CaptureAnalysisParser.inferReminderAtFromText("party friday at 6pm", fridayNow)
        assertEquals(at(2026, 8, 28, 18, 0), got)
    }

    @Test
    fun `every morning at 8 stays a valid next occurrence`() {
        val got = CaptureAnalysisParser.inferReminderAtFromText("water plants every morning at 8", NOW)
        // 8 AM today has passed (it's 10 AM) → tomorrow 8:00.
        assertEquals(at(2026, 8, 16, 8, 0), got)
    }

    @Test
    fun `12 30 pm parses as midday`() {
        val got = CaptureAnalysisParser.inferReminderAtFromText("lunch at 12:30pm", NOW)
        assertEquals(at(2026, 8, 15, 12, 30), got)
    }

    @Test
    fun `bare hour needs a qualifier`() {
        assertNull(CaptureAnalysisParser.inferReminderAtFromText("meet 5 people for coffee", NOW))
        assertNull(CaptureAnalysisParser.inferReminderAtFromText("call 911", NOW))
        assertNull(CaptureAnalysisParser.inferReminderAtFromText("read chapter 3 tonight", NOW))
    }

    @Test
    fun `vague references never count`() {
        assertNull(CaptureAnalysisParser.inferReminderAtFromText("clean the garage someday", NOW))
        assertNull(CaptureAnalysisParser.inferReminderAtFromText("we should talk later", NOW))
        assertNull(CaptureAnalysisParser.inferReminderAtFromText("no time mentioned at all", NOW))
    }

    // ---- parse() wiring — the model dropped reminderAt, the text saves it ----

    @Test
    fun `parse fills a missing reminderAt from the blurt text`() {
        val json = """
            {"blurts":[{
                "content": "school tomorrow at 9pm",
                "intent": "REMINDER",
                "category": "LEARNING",
                "reminderAt": null,
                "important": false,
                "recurrence": "NONE"
            }]}
        """.trimIndent()
        val analyses = CaptureAnalysisParser.parse(json, NOW)!!
        assertEquals(1, analyses.size)
        assertEquals(at(2026, 8, 16, 21, 0), analyses[0].reminderAt)
    }

    @Test
    fun `parse keeps an explicit model time over the text`() {
        val json = """
            {"blurts":[{
                "content": "school tomorrow at 9pm",
                "intent": "REMINDER",
                "category": "LEARNING",
                "reminderAt": "2026-08-16T19:30:00",
                "important": false,
                "recurrence": "NONE"
            }]}
        """.trimIndent()
        val analyses = CaptureAnalysisParser.parse(json, NOW)!!
        assertEquals(1, analyses.size)
        // 19:30 local — the model's explicit answer wins.
        assertEquals(at(2026, 8, 16, 19, 30), analyses[0].reminderAt)
    }

    @Test
    fun `parse recovers from a mangled timestamp`() {
        val json = """
            {"blurts":[{
                "content": "call mom tomorrow at 9am",
                "intent": "REMINDER",
                "category": "PERSONAL",
                "reminderAt": "not-a-time",
                "important": false,
                "recurrence": "NONE"
            }]}
        """.trimIndent()
        val analyses = CaptureAnalysisParser.parse(json, NOW)!!
        assertEquals(at(2026, 8, 16, 9, 0), analyses[0].reminderAt)
    }

    @Test
    fun `no time anywhere means no reminder`() {
        val json = """
            {"blurts":[{
                "content": "buy groceries",
                "intent": "TASK",
                "category": "SHOPPING",
                "reminderAt": null,
                "important": false,
                "recurrence": "NONE"
            }]}
        """.trimIndent()
        val analyses = CaptureAnalysisParser.parse(json, NOW)!!
        assertNull(analyses[0].reminderAt)
    }

    @Test
    fun `multi blurt split keeps per-blurt times`() {
        val json = """
            {"blurts":[
                {"content": "call mom tomorrow at 9am", "intent": "TASK", "category": "PERSONAL",
                 "reminderAt": null, "important": false, "recurrence": "NONE"},
                {"content": "read the design book", "intent": "TASK", "category": "LEARNING",
                 "reminderAt": null, "important": false, "recurrence": "NONE"}
            ]}
        """.trimIndent()
        val analyses = CaptureAnalysisParser.parse(json, NOW)!!
        assertEquals(2, analyses.size)
        assertEquals(at(2026, 8, 16, 9, 0), analyses[0].reminderAt)
        assertNull(analyses[1].reminderAt)
    }

    @Test
    fun `returned time is always in the future`() {
        assertTrue(CaptureAnalysisParser.inferReminderAtFromText("at 11:45pm", NOW)!! > NOW)
        assertTrue(CaptureAnalysisParser.inferReminderAtFromText("tomorrow at 1am", NOW)!! > NOW)
        assertNotNull(CaptureAnalysisParser.inferReminderAtFromText("in 30 minutes", NOW))
    }

    // ---- localFallback — reminder detection with zero AI ----

    @Test
    fun `local fallback builds a reminder analysis without ai`() {
        val a = CaptureAnalysisParser.localFallback("school tomorrow at 9pm", NOW)!!
        // Unclassified by design — the reminder is the point, not the label.
        assertEquals(com.blurt.app.data.model.CaptureIntent.NOTE, a.intent)
        assertEquals(com.blurt.app.data.model.CaptureCategory.OTHER, a.category)
        assertEquals(at(2026, 8, 16, 21, 0), a.reminderAt)
        assertEquals(com.blurt.app.data.model.Recurrence.NONE, a.recurrence)
    }

    @Test
    fun `local fallback keeps recurrence for every morning`() {
        val a = CaptureAnalysisParser.localFallback("water plants every morning at 8", NOW)!!
        assertEquals(com.blurt.app.data.model.Recurrence.DAILY, a.recurrence)
        assertEquals(at(2026, 8, 16, 8, 0), a.reminderAt)
    }

    @Test
    fun `local fallback is null without a concrete time`() {
        assertNull(CaptureAnalysisParser.localFallback("buy groceries", NOW))
        assertNull(CaptureAnalysisParser.localFallback("we should talk later", NOW))
    }

    // ---- parseWithReply — the companion contract ----

    @Test
    fun `companion parses the reply and saves the blurts`() {
        val json = """
            {
                "reply": "Got it — Sarah, tomorrow at 3pm, saved with a reminder.",
                "save": true,
                "blurts": [{
                    "content": "meeting with Sarah tomorrow at 3pm",
                    "intent": "REMINDER",
                    "category": "WORK",
                    "reminderAt": "2026-08-16T15:00:00",
                    "important": false,
                    "recurrence": "NONE"
                }]
            }
        """.trimIndent()
        val result = CaptureAnalysisParser.parseWithReply(json, NOW)!!
        assertEquals("Got it — Sarah, tomorrow at 3pm, saved with a reminder.", result.reply)
        assertTrue(result.save)
        assertEquals(1, result.analyses.size)
        assertEquals(at(2026, 8, 16, 15, 0), result.analyses[0].reminderAt)
    }

    @Test
    fun `companion empty blurts with save false is a valid discard`() {
        val json = """
            {
                "reply": "That sounds rough. I won't save this one.",
                "save": false,
                "blurts": []
            }
        """.trimIndent()
        val result = CaptureAnalysisParser.parseWithReply(json, NOW)!!
        assertEquals("That sounds rough. I won't save this one.", result.reply)
        assertEquals(false, result.save)
        assertEquals(0, result.analyses.size)
    }

    @Test
    fun `companion legacy shape has no reply and saves everything`() {
        val json = """
            {"blurts":[{
                "content": "buy groceries",
                "intent": "TASK",
                "category": "SHOPPING",
                "reminderAt": null,
                "important": false,
                "recurrence": "NONE"
            }]}
        """.trimIndent()
        val result = CaptureAnalysisParser.parseWithReply(json, NOW)!!
        assertNull(result.reply)
        assertTrue(result.save)
        assertEquals(1, result.analyses.size)
        assertEquals(com.blurt.app.data.model.CaptureIntent.TASK, result.analyses[0].intent)
    }

    @Test
    fun `companion malformed json is null`() {
        assertNull(CaptureAnalysisParser.parseWithReply("not json at all", NOW))
        assertNull(CaptureAnalysisParser.parseWithReply("""{"reply": "only a reply"}""", NOW))
    }
}
