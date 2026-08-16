package com.blurt.app.ai

import com.blurt.app.data.model.CaptureCategory
import com.blurt.app.data.model.CaptureIntent
import com.blurt.app.data.model.Recurrence
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
            """{\"blurts\":[{\"content\":\"remember the dentist\",\"intent\":\"REMINDER\",\"category\":\"HEALTH\",\"reminderAt\":\"2026-08-13T15:00:00+05:30\",\"important\":true,\"recurrence\":\"NONE\"}]}"""
        )
        val jsonText = GroqCaptureAnalyzer.extractContent(raw)!!
        val analysis = CaptureAnalysisParser.parse(jsonText)!!.single()
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
            """{\"blurts\":[{\"content\":\"note it\",\"intent\":\"NOTE\",\"category\":\"PERSONAL\",\"reminderAt\":null,\"important\":false,\"recurrence\":\"NONE\"}]}"""
        )
        val analysis = CaptureAnalysisParser.parse(GroqCaptureAnalyzer.extractContent(raw)!!)!!.single()
        assertEquals(CaptureIntent.NOTE, analysis.intent)
        assertEquals(CaptureCategory.PERSONAL, analysis.category)
        assertNull(analysis.reminderAt)
    }

    @Test
    fun parsesRecurringBlurts() {
        val raw = chatCompletionResponse(
            """{\"blurts\":[{\"content\":\"gym every Wednesday at 6pm\",\"intent\":\"REMINDER\",\"category\":\"FITNESS\",\"reminderAt\":\"2026-08-19T18:00:00+05:30\",\"important\":false,\"recurrence\":\"WEEKLY\"}]}"""
        )
        val analysis = CaptureAnalysisParser.parse(GroqCaptureAnalyzer.extractContent(raw)!!)!!.single()
        assertEquals(Recurrence.WEEKLY, analysis.recurrence)
        assertEquals("gym every Wednesday at 6pm", analysis.content)
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
