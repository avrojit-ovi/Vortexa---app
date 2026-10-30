package com.vortexa.feature.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserHistoryPolicyTest {

    @Test
    fun `web pages are recorded`() {
        assertTrue(BrowserHistoryPolicy.shouldRecord("https://example.com", incognito = false))
        assertTrue(BrowserHistoryPolicy.shouldRecord("http://example.com", incognito = false))
    }

    @Test
    fun `incognito visits are never recorded`() {
        assertFalse(BrowserHistoryPolicy.shouldRecord("https://example.com", incognito = true))
    }

    @Test
    fun `blank and non-web urls are not recorded`() {
        assertFalse(BrowserHistoryPolicy.shouldRecord(null, incognito = false))
        assertFalse(BrowserHistoryPolicy.shouldRecord("", incognito = false))
        assertFalse(BrowserHistoryPolicy.shouldRecord("about:blank", incognito = false))
        assertFalse(BrowserHistoryPolicy.shouldRecord("file:///sdcard/a.html", incognito = false))
    }
}
