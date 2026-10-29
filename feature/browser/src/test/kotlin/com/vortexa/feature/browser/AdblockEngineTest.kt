package com.vortexa.feature.browser

import com.vortexa.feature.browser.adblock.AdblockEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdblockEngineTest {

    private fun engine(vararg lines: String): AdblockEngine =
        AdblockEngine.fromLines(lines.toList()).first

    @Test
    fun `domain anchors block the host and its subdomains`() {
        val adblock = engine("||doubleclick.net")
        assertTrue(adblock.shouldBlock("https://doubleclick.net/pixel.gif"))
        assertTrue(adblock.shouldBlock("https://ad.doubleclick.net/x.js"))
        assertFalse(adblock.shouldBlock("https://example.com/doubleclick.net"))
    }

    @Test
    fun `plain patterns match anywhere in the url`() {
        val adblock = engine("/pagead/", "/adserver/")
        assertTrue(adblock.shouldBlock("https://site.example/pagead/banner.js"))
        assertFalse(adblock.shouldBlock("https://site.example/article"))
    }

    @Test
    fun `exceptions win over block rules`() {
        val adblock = AdblockEngine.fromLines(
            listOf("||googlesyndication.com", "@@||googlesyndication.com/pagead/js/loader.js"),
        ).first
        assertTrue(adblock.shouldBlock("https://pagead2.googlesyndication.com/x.js"))
        assertFalse(adblock.shouldBlock("https://googlesyndication.com/pagead/js/loader.js"))
    }

    @Test
    fun `comments and cosmetic rules are skipped, not half applied`() {
        val result = AdblockEngine.fromLines(
            listOf("!comment", "[Adblock Plus 2.0]", "example.com##.banner", "/ad[0-9]+/", "||ads.example"),
        )
        assertEquals(2, result.second.skippedLines)
        assertEquals(1, result.second.loadedRules)
    }

    @Test
    fun `uppercase urls and rules are compared case insensitively`() {
        val adblock = engine("||ADS.example.com")
        assertTrue(adblock.shouldBlock("HTTPS://ADS.EXAMPLE.COM/x.js"))
    }

    @Test
    fun `wildcards span any path`() {
        val adblock = engine("||example.com/ads/*/banner.js")
        assertTrue(adblock.shouldBlock("https://example.com/ads/728/banner.js"))
        assertFalse(adblock.shouldBlock("https://example.com/content/page.html"))
    }

    @Test
    fun `the bundled list blocks known ad hosts and keeps the allow rule`() {
        val adblock = AdblockEngine.fromLines(AdblockEngine.bundledRules).first
        assertTrue(adblock.shouldBlock("https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js"))
        assertFalse(adblock.shouldBlock("https://googlesyndication.com/pagead/js/loader.js"))
        assertFalse(adblock.shouldBlock("https://example.com/index.html"))
    }

    @Test
    fun `an empty engine blocks nothing`() {
        assertFalse(AdblockEngine.empty().shouldBlock("https://example.com"))
        assertFalse(AdblockEngine.empty().shouldBlock(""))
    }
}
