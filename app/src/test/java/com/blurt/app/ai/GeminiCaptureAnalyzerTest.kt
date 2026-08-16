package com.blurt.app.ai

import com.blurt.app.data.model.CaptureCategory
import com.blurt.app.data.model.CaptureIntent
import com.blurt.app.data.model.Recurrence
import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Exercises the pure JSON parsing of the Gemini analysis response. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GeminiCaptureAnalyzerTest {

    private fun generateContentResponse(jsonText: String) =
        """{"candidates":[{"content":{"parts":[{"text":"$jsonText"}]}}]}"""

    private fun blurtsResponse(vararg blurtObjects: String) =
        generateContentResponse("""{\"blurts\":[${blurtObjects.joinToString(",")}]}""")

    @Test
    fun parsesIntentCategoryAndIsoReminderWithOffset() {
        val raw = blurtsResponse(
            """{\"content\":\"remember the dentist\",\"intent\":\"REMINDER\",\"category\":\"HEALTH\",\"reminderAt\":\"2026-08-12T15:00:00+05:30\",\"important\":false,\"recurrence\":\"NONE\"}"""
        )
        val analysis = GeminiCaptureAnalyzer.parseAnalysis(raw)!!.single()
        assertEquals(CaptureIntent.REMINDER, analysis.intent)
        assertEquals(CaptureCategory.HEALTH, analysis.category)
        assertEquals(
            OffsetDateTime.parse("2026-08-12T15:00:00+05:30").toInstant().toEpochMilli(),
            analysis.reminderAt,
        )
        assertFalse(analysis.important)
    }

    @Test
    fun parsesNaiveReminderAsDeviceLocalTime() {
        val raw = blurtsResponse(
            """{\"content\":\"flight to paris\",\"intent\":\"TASK\",\"category\":\"TRAVEL\",\"reminderAt\":\"2026-08-12T15:00:00\",\"important\":false,\"recurrence\":\"NONE\"}"""
        )
        val analysis = GeminiCaptureAnalyzer.parseAnalysis(raw)!!.single()
        assertEquals(CaptureIntent.TASK, analysis.intent)
        assertEquals(CaptureCategory.TRAVEL, analysis.category)
        // A timestamp without an offset is resolved in the device's zone.
        assertEquals(
            java.time.LocalDateTime.parse("2026-08-12T15:00:00")
                .atZone(java.time.ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli(),
            analysis.reminderAt,
        )
    }

    @Test
    fun nullReminderStaysNull() {
        val raw = blurtsResponse(
            """{\"content\":\"app idea\",\"intent\":\"IDEA\",\"category\":\"IDEAS\",\"reminderAt\":null,\"important\":true,\"recurrence\":\"NONE\"}"""
        )
        val analysis = GeminiCaptureAnalyzer.parseAnalysis(raw)!!.single()
        assertEquals(CaptureIntent.IDEA, analysis.intent)
        assertEquals(CaptureCategory.IDEAS, analysis.category)
        assertNull(analysis.reminderAt)
        assertTrue(analysis.important)
    }

    @Test
    fun missingReminderFieldStaysNull() {
        val raw = blurtsResponse(
            """{\"content\":\"note it\",\"intent\":\"NOTE\",\"category\":\"WORK\",\"important\":false,\"recurrence\":\"NONE\"}"""
        )
        val analysis = GeminiCaptureAnalyzer.parseAnalysis(raw)!!.single()
        assertEquals(CaptureIntent.NOTE, analysis.intent)
        assertEquals(CaptureCategory.WORK, analysis.category)
        assertNull(analysis.reminderAt)
    }

    @Test
    fun parsesRecurringDailyReminder() {
        val raw = blurtsResponse(
            """{\"content\":\"remind me every day at 7am to take vitamins\",\"intent\":\"REMINDER\",\"category\":\"HEALTH\",\"reminderAt\":\"2026-08-13T07:00:00+05:30\",\"important\":true,\"recurrence\":\"DAILY\"}"""
        )
        val analysis = GeminiCaptureAnalyzer.parseAnalysis(raw)!!.single()
        assertEquals(Recurrence.DAILY, analysis.recurrence)
        assertEquals(
            OffsetDateTime.parse("2026-08-13T07:00:00+05:30").toInstant().toEpochMilli(),
            analysis.reminderAt,
        )
    }

    @Test
    fun parsesRecurringWeeklyReminderAndPerBlurtContent() {
        val raw = blurtsResponse(
            """{\"content\":\"gym every Wednesday at 6pm\",\"intent\":\"REMINDER\",\"category\":\"FITNESS\",\"reminderAt\":\"2026-08-19T18:00:00+05:30\",\"important\":false,\"recurrence\":\"WEEKLY\"}"""
        )
        val analysis = GeminiCaptureAnalyzer.parseAnalysis(raw)!!.single()
        assertEquals(Recurrence.WEEKLY, analysis.recurrence)
        assertEquals("gym every Wednesday at 6pm", analysis.content)
        assertEquals(CaptureIntent.REMINDER, analysis.intent)
    }

    @Test
    fun splitsMultipleBlurtsEachWithTheirOwnAnalysis() {
        val raw = blurtsResponse(
            """{\"content\":\"call mom tomorrow\",\"intent\":\"TASK\",\"category\":\"PERSONAL\",\"reminderAt\":\"2026-08-13T10:00:00+05:30\",\"important\":true,\"recurrence\":\"NONE\"}""",
            """{\"content\":\"water the plants every morning\",\"intent\":\"REMINDER\",\"category\":\"HOME\",\"reminderAt\":\"2026-08-13T08:00:00+05:30\",\"important\":false,\"recurrence\":\"DAILY\"}""",
            """{\"content\":\"read that book on design\",\"intent\":\"NOTE\",\"category\":\"IDEAS\",\"reminderAt\":null,\"important\":false,\"recurrence\":\"NONE\"}""",
        )
        val analyses = GeminiCaptureAnalyzer.parseAnalysis(raw)!!
        assertEquals(3, analyses.size)
        assertEquals("call mom tomorrow", analyses[0].content)
        assertEquals(CaptureIntent.TASK, analyses[0].intent)
        assertEquals(Recurrence.DAILY, analyses[1].recurrence)
        assertEquals(CaptureCategory.IDEAS, analyses[2].category)
        assertNull(analyses[2].reminderAt)
    }

    @Test
    fun unknownRecurrenceDegradesToNone() {
        val raw = blurtsResponse(
            """{\"content\":\"note it\",\"intent\":\"NOTE\",\"category\":\"WORK\",\"reminderAt\":null,\"important\":false,\"recurrence\":\"HOURLY\"}"""
        )
        val analysis = GeminiCaptureAnalyzer.parseAnalysis(raw)!!.single()
        assertEquals(Recurrence.NONE, analysis.recurrence)
    }

    @Test
    fun lowercaseRecurrenceFieldIsAccepted() {
        // Some models mangle the enum — lowercase must still be understood.
        val raw = blurtsResponse(
            """{\"content\":\"water plants every morning\",\"intent\":\"REMINDER\",\"category\":\"HOME\",\"reminderAt\":\"2026-08-13T08:00:00+05:30\",\"important\":false,\"recurrence\":\"daily\"}"""
        )
        val analysis = GeminiCaptureAnalyzer.parseAnalysis(raw)!!.single()
        assertEquals(Recurrence.DAILY, analysis.recurrence)
    }

    @Test
    fun missingRecurrenceFallsBackToText() {
        // The field is missing entirely — "every morning" in the text must
        // still mean DAILY, not a silent one-shot.
        val raw = blurtsResponse(
            """{\"content\":\"water the plants every morning\",\"intent\":\"REMINDER\",\"category\":\"HOME\",\"reminderAt\":\"2026-08-13T08:00:00+05:30\",\"important\":false,\"recurrence\":\"NONE\"}"""
        )
        val analysis = GeminiCaptureAnalyzer.parseAnalysis(raw)!!.single()
        assertEquals(Recurrence.DAILY, analysis.recurrence)
    }

    @Test
    fun missingRecurrenceFallsBackToWeekdayText() {
        val raw = blurtsResponse(
            """{\"content\":\"gym every Wednesday at 6pm\",\"intent\":\"REMINDER\",\"category\":\"FITNESS\",\"reminderAt\":\"2026-08-19T18:00:00+05:30\",\"important\":false,\"recurrence\":\"NONE\"}"""
        )
        val analysis = GeminiCaptureAnalyzer.parseAnalysis(raw)!!.single()
        assertEquals(Recurrence.WEEKLY, analysis.recurrence)
    }

    @Test
    fun noRecurrencePhraseStaysNone() {
        val raw = blurtsResponse(
            """{\"content\":\"call the dentist\",\"intent\":\"TASK\",\"category\":\"HEALTH\",\"reminderAt\":null,\"important\":false,\"recurrence\":\"NONE\"}"""
        )
        val analysis = GeminiCaptureAnalyzer.parseAnalysis(raw)!!.single()
        assertEquals(Recurrence.NONE, analysis.recurrence)
    }

    @Test
    fun legacySingleObjectShapeStillParses() {
        // Pre-split responses (no `blurts` array) stay accepted.
        val raw = generateContentResponse(
            """{\"intent\":\"REMINDER\",\"category\":\"HEALTH\",\"reminderAt\":\"2026-08-12T15:00:00+05:30\",\"important\":false}"""
        )
        val analyses = GeminiCaptureAnalyzer.parseAnalysis(raw)!!
        assertEquals(1, analyses.size)
        assertEquals(CaptureIntent.REMINDER, analyses.single().intent)
    }

    @Test
    fun emptyBlurtsArrayIsRejected() {
        val raw = generateContentResponse("""{\"blurts\":[]}""")
        assertNull(GeminiCaptureAnalyzer.parseAnalysis(raw))
    }

    @Test
    fun unknownIntentIsRejected() {
        val raw = blurtsResponse(
            """{\"content\":\"x\",\"intent\":\"NOT_REAL\",\"category\":\"WORK\",\"reminderAt\":null,\"important\":false,\"recurrence\":\"NONE\"}"""
        )
        assertNull(GeminiCaptureAnalyzer.parseAnalysis(raw))
    }

    @Test
    fun unknownCategoryIsRejected() {
        val raw = blurtsResponse(
            """{\"content\":\"x\",\"intent\":\"NOTE\",\"category\":\"NOT_A_REAL_CATEGORY\",\"reminderAt\":null,\"important\":false,\"recurrence\":\"NONE\"}"""
        )
        assertNull(GeminiCaptureAnalyzer.parseAnalysis(raw))
    }

    @Test
    fun garbageResponseIsRejected() {
        assertNull(GeminiCaptureAnalyzer.parseAnalysis("not json at all"))
        assertNull(GeminiCaptureAnalyzer.parseAnalysis("""{"candidates":[]}"""))
        assertNull(GeminiCaptureAnalyzer.parseAnalysis("""{"candidates":[{"content":{}}]}"""))
        assertNull(GeminiCaptureAnalyzer.parseAnalysis(""))
    }

    @Test
    fun invalidTimeStringNoLongerKillsTheAnalysis() {
        val raw = blurtsResponse(
            """{\"content\":\"x\",\"intent\":\"REMINDER\",\"category\":\"FITNESS\",\"reminderAt\":\"someday\",\"important\":false,\"recurrence\":\"NONE\"}"""
        )
        // The mangled time used to sink the whole analysis; now the text
        // fallback decides — "x" has no time, so the blurt survives with no
        // reminder instead of being discarded.
        val analysis = GeminiCaptureAnalyzer.parseAnalysis(raw)!!.single()
        assertNull(analysis.reminderAt)
    }

    @Test
    fun missingReminderAtFallsBackToTheBlurtText() {
        val raw = blurtsResponse(
            """{\"content\":\"school tomorrow at 9pm\",\"intent\":\"REMINDER\",\"category\":\"LEARNING\",\"reminderAt\":null,\"important\":false,\"recurrence\":\"NONE\"}"""
        )
        val analysis = GeminiCaptureAnalyzer.parseAnalysis(raw)!!.single()
        // The text fallback resolves "tomorrow at 9pm" — roughly 24h out,
        // whatever the real clock says when the test runs.
        assertNotNull(analysis.reminderAt)
        val deltaHours = (analysis.reminderAt!! - System.currentTimeMillis()) / 3_600_000.0
        assertTrue("unexpected delta: $deltaHours h", deltaHours in 18.0..40.0)
    }

    @Test
    fun noKeyMeansNoAnalysisWithoutAnyNetworkCall() = kotlinx.coroutines.test.runTest {
        val analyzer = GeminiCaptureAnalyzer(
            apiKeyProvider = { null },
            packageName = "com.blurt.app",
            certSha1 = "",
        )
        assertNull(analyzer.analyze("buy milk", 0L))
    }
}
