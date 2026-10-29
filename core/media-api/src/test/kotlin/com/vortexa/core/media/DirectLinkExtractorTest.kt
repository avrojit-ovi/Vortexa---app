package com.vortexa.core.media

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectLinkExtractorTest {

    @Test
    fun `audio and video files are playable links`() {
        val link = DirectLinkParser.parse("https://cdn.example.com/music/song%20one.flac")!!
        assertEquals("https://cdn.example.com/music/song%20one.flac", link.url)
        assertEquals("cdn.example.com", link.host)
        assertEquals("song one.flac", link.displayName)
        assertFalse(link.isHls)
    }

    @Test
    fun `hls and dash manifests are marked adaptive`() {
        assertTrue(DirectLinkParser.parse("https://radio.example/live/index.m3u8")!!.isHls)
        assertTrue(DirectLinkParser.parse("https://video.example/movie/manifest.mpd")!!.isHls)
        assertTrue(DirectLinkParser.parse("https://radio.example/live.m3u")!!.isHls)
    }

    @Test
    fun `query strings and fragments do not confuse the extension`() {
        val link = DirectLinkParser.parse("https://cdn.example/a.mp3?token=abc&expires=1#t=10")!!
        assertEquals("https://cdn.example/a.mp3?token=abc&expires=1", link.url)
        assertFalse(link.isHls)
    }

    @Test
    fun `non media and non http text is rejected`() {
        assertNull(DirectLinkParser.parse("https://example.com/page.html"))
        assertNull(DirectLinkParser.parse("https://example.com/"))
        assertNull(DirectLinkParser.parse("just some words"))
        assertNull(DirectLinkParser.parse("ftp://example.com/a.mp3"))
        assertNull(DirectLinkParser.parse(""))
    }

    @Test
    fun `the built in source answers with one item`() = runTest {
        val extractor = DirectLinkExtractor()
        val items = extractor.search(MediaQuery("https://cdn.example/song.m4a"))

        assertEquals(1, items.size)
        assertEquals(DirectLinkExtractor.ID, items.single().extractorId)
        assertEquals("song.m4a", items.single().title)
        assertEquals("cdn.example", items.single().artist)
    }

    @Test
    fun `the built in source ignores text that is not a link`() = runTest {
        assertTrue(DirectLinkExtractor().search(MediaQuery("lofi beats")).isEmpty())
    }

    @Test
    fun `resolving returns the same url`() = runTest {
        val extractor = DirectLinkExtractor()
        val track = extractor.search(MediaQuery("https://cdn.example/live.m3u8")).single()

        val link = extractor.resolve(track)

        assertEquals("https://cdn.example/live.m3u8", link.url)
        assertTrue(link.isHls)
    }

    @Test
    fun `expiry is reported honestly`() {
        val link = StreamLink(url = "https://x/a.mp3", expiresAtEpochMs = 1_000L)
        assertFalse(link.isExpired(999))
        assertTrue(link.isExpired(1_000))
        assertFalse(StreamLink(url = "https://x/a.mp3").isExpired(Long.MAX_VALUE))
    }
}
