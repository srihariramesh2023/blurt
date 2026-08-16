package com.blurt.app.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FollowUpAnswerParserTest {

    @Test
    fun `plain yes accepts`() {
        assertTrue(FollowUpAnswerParser.parse("yes"))
        assertTrue(FollowUpAnswerParser.parse("yeah"))
        assertTrue(FollowUpAnswerParser.parse("yep"))
    }

    @Test
    fun `enthusiastic and polite yes accepts`() {
        assertTrue(FollowUpAnswerParser.parse("yes please"))
        assertTrue(FollowUpAnswerParser.parse("sure, go ahead"))
        assertTrue(FollowUpAnswerParser.parse("of course"))
        assertTrue(FollowUpAnswerParser.parse("okay"))
        assertTrue(FollowUpAnswerParser.parse("do it"))
        assertTrue(FollowUpAnswerParser.parse("sounds good"))
    }

    @Test
    fun `plain no declines`() {
        assertFalse(FollowUpAnswerParser.parse("no"))
        assertFalse(FollowUpAnswerParser.parse("nah"))
        assertFalse(FollowUpAnswerParser.parse("nope"))
    }

    @Test
    fun `polite no declines even with trailing yes-words`() {
        // "no thanks" contains neither; "no, but sure" is still a no.
        assertFalse(FollowUpAnswerParser.parse("no thanks"))
        assertFalse(FollowUpAnswerParser.parse("no, but sure"))
        assertFalse(FollowUpAnswerParser.parse("not really"))
        assertFalse(FollowUpAnswerParser.parse("skip it"))
        assertFalse(FollowUpAnswerParser.parse("no need"))
    }

    @Test
    fun `case insensitive`() {
        assertTrue(FollowUpAnswerParser.parse("YES"))
        assertTrue(FollowUpAnswerParser.parse("Yeah Sure"))
        assertFalse(FollowUpAnswerParser.parse("NO"))
        assertFalse(FollowUpAnswerParser.parse("Nope"))
    }

    @Test
    fun `blank and garbage decline`() {
        assertFalse(FollowUpAnswerParser.parse(""))
        assertFalse(FollowUpAnswerParser.parse("   "))
        assertFalse(FollowUpAnswerParser.parse(null))
        assertFalse(FollowUpAnswerParser.parse("maybe later"))
        assertFalse(FollowUpAnswerParser.parse("i don't know"))
        assertFalse(FollowUpAnswerParser.parse("what time is it"))
    }

    @Test
    fun `decline wins when both appear`() {
        // "yes" inside "no, not really yes" — the decline decides.
        assertFalse(FollowUpAnswerParser.parse("no not really"))
        assertFalse(FollowUpAnswerParser.parse("nah forget it"))
    }
}
