package com.blurt.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LinksTest {

    // --- normalizedHttpUrl ---------------------------------------------------

    @Test
    fun normalizedHttpUrl_prependsHttpsWhenSchemeMissing() {
        assertEquals("https://google.com", "google.com".normalizedHttpUrl())
        // the path is preserved, only the scheme is added
        assertEquals("https://voiceos.com/path", "voiceos.com/path".normalizedHttpUrl())
    }

    @Test
    fun normalizedHttpUrl_trimsSurroundingWhitespace() {
        assertEquals("https://google.com", "  google.com  ".normalizedHttpUrl())
    }

    @Test
    fun normalizedHttpUrl_keepsExistingScheme() {
        assertEquals("https://voiceos.com", "https://voiceos.com".normalizedHttpUrl())
        assertEquals("http://example.com", "http://example.com".normalizedHttpUrl())
        assertEquals("https://sub.domain.com/x", "https://sub.domain.com/x".normalizedHttpUrl())
    }

    @Test
    fun normalizedHttpUrl_emptyStaysEmpty() {
        assertEquals("", "".normalizedHttpUrl())
        assertEquals("", "   ".normalizedHttpUrl())
    }

    // --- isHttpUrl -----------------------------------------------------------

    @Test
    fun isHttpUrl_acceptsHttpAndHttps() {
        assertTrue("https://voiceos.com".isHttpUrl())
        assertTrue("http://example.com".isHttpUrl())
        assertTrue("https://sub.domain.com/path?q=1".isHttpUrl())
    }

    @Test
    fun isHttpUrl_rejectsMissingScheme() {
        assertFalse("voiceos.com".isHttpUrl())
        assertFalse("www.example.com".isHttpUrl())
    }

    @Test
    fun isHttpUrl_rejectsOtherSchemes() {
        assertFalse("ftp://example.com".isHttpUrl())
        assertFalse("mailto:someone@example.com".isHttpUrl())
    }

    @Test
    fun isHttpUrl_rejectsWhitespaceInHost() {
        assertFalse("https://foo bar".isHttpUrl())
        assertFalse("https://foo bar.com".isHttpUrl())
    }

    @Test
    fun isHttpUrl_rejectsBlankInput() {
        assertFalse("".isHttpUrl())
        assertFalse("   ".isHttpUrl())
    }

    @Test
    fun isHttpUrl_rejectsSchemeOnlyOrMissingHost() {
        assertFalse("https://".isHttpUrl())
        assertFalse("https:///path".isHttpUrl())
    }

    // --- urlDomain -----------------------------------------------------------

    @Test
    fun urlDomain_stripsWwwPrefix() {
        assertEquals("voiceos.com", "https://www.voiceos.com".urlDomain())
    }

    @Test
    fun urlDomain_keepsOtherSubdomains() {
        assertEquals("sub.domain.com", "https://sub.domain.com".urlDomain())
    }

    @Test
    fun urlDomain_ignoresPathAndQuery() {
        assertEquals("voiceos.com", "https://voiceos.com/path?q=1".urlDomain())
    }

    @Test
    fun urlDomain_fallsBackToInputWhenUnparsable() {
        assertEquals("not a url", "not a url".urlDomain())
    }
}
