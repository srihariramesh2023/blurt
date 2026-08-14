package com.blurt.app.ai

import com.blurt.app.data.model.CaptureCategory
import com.blurt.app.data.model.CaptureIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Exercises the pure parsing of the Groq chat-completions envelope. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GroqCaptureAnalyzerTest {

    private fun chatCompletionResponse(content: String) =
        """{"choices":[{"message":{"role":"assistant","content":"$content"}}]}"""

    @Test
    fun extractsInnerJsonAndParsesAnalysis() {
        val raw = chatCompletionResponse(
            """{\"intent\":\"REMINDER\",\"category\":\"HEALTH\",\"reminderAt\":\"2026-08-13T15:00:00+05:30\",\"important\":true}"""
        )
        val jsonText = GroqCaptureAnalyzer.extractContent(raw)!!
        val analysis = CaptureAnalysisParser.parse(jsonText)!!
        assertEquals(CaptureIntent.REMINDER, analysis.intent)
        assertEquals(CaptureCategory.HEALTH, analysis.category)
        assertEquals(
            java.time.OffsetDateTime.parse("2026-08-13T15:00:00+05:30").toInstant().toEpochMilli(),
            analysis.reminderAt,
        )
        assertTrue(analysis.important)
    }

    @Test
    fun parsesNullReminderAndNoImportant() {
        val raw = chatCompletionResponse(
            """{\"intent\":\"NOTE\",\"category\":\"PERSONAL\",\"reminderAt\":null}"""
        )
        val analysis = CaptureAnalysisParser.parse(GroqCaptureAnalyzer.extractContent(raw)!!)!!
        assertEquals(CaptureIntent.NOTE, analysis.intent)
        assertEquals(CaptureCategory.PERSONAL, analysis.category)
        assertNull(analysis.reminderAt)
    }

    @Test
    fun garbageEnvelopeIsRejected() {
        assertNull(GroqCaptureAnalyzer.extractContent("not json"))
        assertNull(GroqCaptureAnalyzer.extractContent("""{"choices":[]}"""))
        assertNull(GroqCaptureAnalyzer.extractContent("""{"choices":[{"message":{}}]}"""))
        assertNull(GroqCaptureAnalyzer.extractContent(""))
    }

    @Test
    fun emptyInnerJsonIsRejected() {
        val raw = chatCompletionResponse("   ")
        assertNull(GroqCaptureAnalyzer.extractContent(raw))
    }

    @Test
    fun noKeyMeansNoAnalysisWithoutAnyNetworkCall() = kotlinx.coroutines.test.runTest {
        val analyzer = GroqCaptureAnalyzer(apiKeyProvider = { null }, model = "test-model")
        assertNull(analyzer.analyze("buy milk", 0L))
    }
}
