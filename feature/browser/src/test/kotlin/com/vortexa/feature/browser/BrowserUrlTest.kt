package com.vortexa.feature.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrowserUrlTest {

    @Test
    fun `full urls are passed through untouched`() {
        assertEquals("https://example.com/a?b=1", BrowserUrl.toLoadUrl("https://example.com/a?b=1"))
        assertEquals("http://example.com/", BrowserUrl.toLoadUrl("  http://example.com/  "))
    }

    @Test
    fun `a bare host gets https`() {
        assertEquals("https://example.com", BrowserUrl.toLoadUrl("example.com"))
        assertEquals("https://example.com/path", BrowserUrl.toLoadUrl("example.com/path"))
    }

    @Test
    fun `localhost stays on plain http`() {
        assertEquals("http://localhost:8080", BrowserUrl.toLoadUrl("localhost:8080"))
    }

    @Test
    fun `words become a search`() {
        assertEquals(
            "https://duckduckgo.com/?q=cat+videos",
            BrowserUrl.toLoadUrl("cat videos"),
        )
        assertEquals("https://duckduckgo.com/?q=weather", BrowserUrl.toLoadUrl("weather"))
    }

    @Test
    fun `non web schemes are refused`() {
        assertNull(BrowserUrl.toLoadUrl("intent://scan#Intent;scheme=zxing;end"))
        assertNull(BrowserUrl.toLoadUrl("file:///etc/hosts"))
        assertNull(BrowserUrl.toLoadUrl("javascript:alert(1)"))
    }

    @Test
    fun `blank input is refused`() {
        assertNull(BrowserUrl.toLoadUrl(""))
        assertNull(BrowserUrl.toLoadUrl("   "))
    }

    @Test
    fun `the address bar hides the scheme and the trailing slash`() {
        assertEquals("example.com", BrowserUrl.display("https://example.com/"))
        assertEquals("example.com/a", BrowserUrl.display("http://example.com/a"))
    }
}
