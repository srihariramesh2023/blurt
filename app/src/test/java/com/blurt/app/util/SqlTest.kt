package com.blurt.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Verifies escapeLikePattern produces patterns safe for SQL `LIKE ... ESCAPE '\'`.
 * The behavior is exercised end-to-end against real SQLite in CaptureRepositoryTest.
 */
class SqlTest {

    @Test
    fun plainTextPassesThroughUnchanged() {
        assertEquals("hello world", "hello world".escapeLikePattern())
        assertEquals("", "".escapeLikePattern())
    }

    @Test
    fun percentSignIsEscaped() {
        assertEquals("100\\%", "100%".escapeLikePattern())
        assertEquals("\\%\\%", "%%".escapeLikePattern())
    }

    @Test
    fun underscoreIsEscaped() {
        assertEquals("snake\\_case", "snake_case".escapeLikePattern())
    }

    @Test
    fun backslashIsDoubled() {
        assertEquals("a\\\\b", "a\\b".escapeLikePattern())
    }

    @Test
    fun mixedWildcardsAreAllEscaped() {
        assertEquals("a\\%b\\_c\\\\d", "a%b_c\\d".escapeLikePattern())
    }
}
