package com.blurt.app.ai

import com.blurt.app.data.model.CaptureCategory
import com.blurt.app.data.model.CaptureIntent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The fallback chain: Groq answers normally, and any failure (null or
 * thrown) rolls silently to Gemini — the caller only ever sees analysis or
 * null, never a provider error.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FallbackCaptureAnalyzerTest {

    private fun analysis(intent: CaptureIntent) = listOf(
        CaptureAnalysis(
            intent = intent,
            category = CaptureCategory.OTHER,
            reminderAt = null,
        )
    )

    private class FakeAnalyzer(
        private val result: List<CaptureAnalysis>?,
        private val throws: Boolean = false,
    ) : CaptureAnalyzer {
        override suspend fun analyze(content: String, nowEpochMillis: Long): List<CaptureAnalysis>? {
            if (throws) throw IllegalStateException("provider down")
            return result
        }
    }

    @Test
    fun primaryAnswerWins() = runTest {
        val chain = FallbackCaptureAnalyzer(
            primary = FakeAnalyzer(analysis(CaptureIntent.TASK)),
            secondary = FakeAnalyzer(analysis(CaptureIntent.NOTE)),
        )
        assertEquals(CaptureIntent.TASK, chain.analyze("do the dishes", 0L)?.firstOrNull()?.intent)
    }

    @Test
    fun nullPrimaryFallsBackToSecondary() = runTest {
        val chain = FallbackCaptureAnalyzer(
            primary = FakeAnalyzer(null),
            secondary = FakeAnalyzer(analysis(CaptureIntent.REMINDER)),
        )
        assertEquals(CaptureIntent.REMINDER, chain.analyze("remind me", 0L)?.firstOrNull()?.intent)
    }

    @Test
    fun throwingPrimaryFallsBackToSecondary() = runTest {
        val chain = FallbackCaptureAnalyzer(
            primary = FakeAnalyzer(null, throws = true),
            secondary = FakeAnalyzer(analysis(CaptureIntent.IDEA)),
        )
        assertEquals(CaptureIntent.IDEA, chain.analyze("idea time", 0L)?.firstOrNull()?.intent)
    }

    @Test
    fun bothFailingReturnsNull() = runTest {
        val chain = FallbackCaptureAnalyzer(
            primary = FakeAnalyzer(null, throws = true),
            secondary = FakeAnalyzer(null),
        )
        assertNull(chain.analyze("offline", 0L))
    }

    @Test
    fun secondaryFailureNeverEscapes() = runTest {
        val chain = FallbackCaptureAnalyzer(
            primary = FakeAnalyzer(null),
            secondary = FakeAnalyzer(null, throws = true),
        )
        assertNull(chain.analyze("everything down", 0L))
    }

    @Test
    fun primaryExceptionIsSwallowedWhenSecondarySucceeds() = runTest {
        val chain = FallbackCaptureAnalyzer(
            primary = FakeAnalyzer(null, throws = true),
            secondary = FakeAnalyzer(analysis(CaptureIntent.TASK)),
        )
        val result = chain.analyze("grocery run", 0L)
        assertTrue(result != null)
        assertEquals(CaptureIntent.TASK, result!!.firstOrNull()!!.intent)
    }
}
