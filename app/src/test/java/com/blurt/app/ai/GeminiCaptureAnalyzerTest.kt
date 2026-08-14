package com.blurt.app.ai

import com.blurt.app.data.model.CaptureCategory
import com.blurt.app.data.model.CaptureIntent
import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun parsesIntentCategoryAndIsoReminderWithOffset() {
        val raw = generateContentResponse(
            """{\"intent\":\"REMINDER\",\"category\":\"HEALTH\",\"reminderAt\":\"2026-08-12T15:00:00+05:30\",\"important\":false}"""
        )
        val analysis = GeminiCaptureAnalyzer.parseAnalysis(raw)!!
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
        val raw = generateContentResponse(
            """{\"intent\":\"TASK\",\"category\":\"TRAVEL\",\"reminderAt\":\"2026-08-12T15:00:00\",\"important\":false}"""
        )
        val analysis = GeminiCaptureAnalyzer.parseAnalysis(raw)!!
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
        val raw = generateContentResponse(
            """{\"intent\":\"IDEA\",\"category\":\"IDEAS\",\"reminderAt\":null,\"important\":true}"""
        )
        val analysis = GeminiCaptureAnalyzer.parseAnalysis(raw)!!
        assertEquals(CaptureIntent.IDEA, analysis.intent)
        assertEquals(CaptureCategory.IDEAS, analysis.category)
        assertNull(analysis.reminderAt)
        assertTrue(analysis.important)
    }

    @Test
    fun missingReminderFieldStaysNull() {
        val raw = generateContentResponse(
            """{\"intent\":\"NOTE\",\"category\":\"WORK\",\"important\":false}"""
        )
        val analysis = GeminiCaptureAnalyzer.parseAnalysis(raw)!!
        assertEquals(CaptureIntent.NOTE, analysis.intent)
        assertEquals(CaptureCategory.WORK, analysis.category)
        assertNull(analysis.reminderAt)
    }

    @Test
    fun missingImportantDefaultsFalse() {
        val raw = generateContentResponse(
            """{\"intent\":\"NOTE\",\"category\":\"PERSONAL\",\"reminderAt\":null}"""
        )
        val analysis = GeminiCaptureAnalyzer.parseAnalysis(raw)!!
        assertFalse(analysis.important)
    }

    @Test
    fun unknownIntentIsRejected() {
        val raw = generateContentResponse(
            """{\"intent\":\"NOT_REAL\",\"category\":\"WORK\",\"reminderAt\":null,\"important\":false}"""
        )
        assertNull(GeminiCaptureAnalyzer.parseAnalysis(raw))
    }

    @Test
    fun unknownCategoryIsRejected() {
        val raw = generateContentResponse(
            """{\"intent\":\"NOTE\",\"category\":\"NOT_A_REAL_CATEGORY\",\"reminderAt\":null,\"important\":false}"""
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
    fun invalidTimeStringIsRejected() {
        val raw = generateContentResponse(
            """{\"intent\":\"REMINDER\",\"category\":\"FITNESS\",\"reminderAt\":\"someday\",\"important\":false}"""
        )
        assertNull(GeminiCaptureAnalyzer.parseAnalysis(raw))
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
