package com.blurt.app.ai

import com.blurt.app.data.model.CaptureCategory
import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun parsesCategoryAndIsoReminderWithOffset() {
        val raw = generateContentResponse(
            """{\"category\":\"HEALTH\",\"reminderAt\":\"2026-08-12T15:00:00+05:30\"}"""
        )
        val analysis = GeminiCaptureAnalyzer.parseAnalysis(raw)!!
        assertEquals(CaptureCategory.HEALTH, analysis.category)
        assertEquals(
            OffsetDateTime.parse("2026-08-12T15:00:00+05:30").toInstant().toEpochMilli(),
            analysis.reminderAt,
        )
    }

    @Test
    fun parsesNaiveReminderAsDeviceLocalTime() {
        val raw = generateContentResponse(
            """{\"category\":\"TRAVEL\",\"reminderAt\":\"2026-08-12T15:00:00\"}"""
        )
        val analysis = GeminiCaptureAnalyzer.parseAnalysis(raw)!!
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
        val raw = generateContentResponse("""{\"category\":\"IDEAS\",\"reminderAt\":null}""")
        val analysis = GeminiCaptureAnalyzer.parseAnalysis(raw)!!
        assertEquals(CaptureCategory.IDEAS, analysis.category)
        assertNull(analysis.reminderAt)
    }

    @Test
    fun missingReminderFieldStaysNull() {
        val raw = generateContentResponse("""{\"category\":\"WORK\"}""")
        val analysis = GeminiCaptureAnalyzer.parseAnalysis(raw)!!
        assertEquals(CaptureCategory.WORK, analysis.category)
        assertNull(analysis.reminderAt)
    }

    @Test
    fun unknownCategoryIsRejected() {
        val raw = generateContentResponse("""{\"category\":\"NOT_A_REAL_CATEGORY\",\"reminderAt\":null}""")
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
        val raw = generateContentResponse("""{\"category\":\"FITNESS\",\"reminderAt\":\"someday\"}""")
        assertNull(GeminiCaptureAnalyzer.parseAnalysis(raw))
    }
}
