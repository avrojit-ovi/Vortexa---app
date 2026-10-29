package com.vortexa.feature.browser

import com.vortexa.feature.browser.adblock.Kind
import com.vortexa.feature.browser.adblock.MediaSniffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaSnifferTest {

    @Test
    fun `media extensions are classified`() {
        assertEquals(Kind.VIDEO, MediaSniffer.classify("https://cdn.example/movie.MP4")?.kind)
        assertEquals(Kind.AUDIO, MediaSniffer.classify("https://cdn.example/song.flac")?.kind)
        assertEquals(Kind.HLS, MediaSniffer.classify("https://cdn.example/live/index.m3u8")?.kind)
        assertEquals(Kind.DASH, MediaSniffer.classify("https://cdn.example/movie/manifest.mpd")?.kind)
        assertEquals(Kind.FILE, MediaSniffer.classify("https://cdn.example/tool.zip")?.kind)
    }

    @Test
    fun `mime types rescue urls without an extension`() {
        assertEquals(Kind.VIDEO, MediaSniffer.classify("https://cdn.example/stream", "video/mp4")?.kind)
        assertEquals(Kind.AUDIO, MediaSniffer.classify("https://cdn.example/stream", "audio/mpeg")?.kind)
        assertEquals(
            Kind.HLS,
            MediaSniffer.classify("https://cdn.example/play", "application/vnd.apple.mpegurl")?.kind,
        )
    }

    @Test
    fun `pages and scripts are not media`() {
        assertNull(MediaSniffer.classify("https://example.com/index.html"))
        assertNull(MediaSniffer.classify("https://example.com/app.js"))
        assertNull(MediaSniffer.classify("ftp://example.com/a.mp4"))
        assertNull(MediaSniffer.classify(""))
    }

    @Test
    fun `the query string survives but the fragment does not`() {
        val sniffed = MediaSniffer.classify("https://cdn.example/a.mp4?token=1#t=10")
        assertEquals("https://cdn.example/a.mp4?token=1", sniffed?.url)
        assertEquals("a.mp4", sniffed?.suggestedName)
    }

    @Test
    fun `absurdly long urls are ignored`() {
        val long = "https://cdn.example/" + "a".repeat(4_000) + ".mp4"
        assertNull(MediaSniffer.classify(long))
    }
}
