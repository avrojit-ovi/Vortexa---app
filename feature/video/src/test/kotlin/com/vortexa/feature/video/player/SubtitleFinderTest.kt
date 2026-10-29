package com.vortexa.feature.video.player

import androidx.media3.common.MimeTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleFinderTest {

    @Test
    fun `a plain sidecar with the same name is found`() {
        val found = SubtitleFinder.match(
            videoPath = "/movies/Heat.1995.mkv",
            candidates = listOf("/movies/Heat.1995.srt"),
        )
        assertEquals(1, found.size)
        assertEquals(MimeTypes.APPLICATION_SUBRIP, found.single().mimeType)
    }

    @Test
    fun `language suffixes are matched and labelled`() {
        val found = SubtitleFinder.match(
            videoPath = "/movies/Heat.1995.mkv",
            candidates = listOf(
                "/movies/Heat.1995.en.srt",
                "/movies/Heat.1995.bn.ass",
            ),
        )
        assertEquals(2, found.size)
        assertEquals("BN · ASS", found[0].label)
        assertEquals("bn", found[0].language)
        assertEquals(MimeTypes.TEXT_SSA, found[0].mimeType)
        assertEquals("EN · SRT", found[1].label)
    }

    @Test
    fun `unrelated files are ignored`() {
        val found = SubtitleFinder.match(
            videoPath = "/movies/Heat.mkv",
            candidates = listOf(
                "/movies/Heat.1995.srt",   // different stem
                "/movies/Other.srt",
                "/movies/Heat.txt",        // not a subtitle container
                "/movies/Heat",
            ),
        )
        assertTrue(found.isEmpty())
    }

    @Test
    fun `unknown subtitle containers map to no mime type`() {
        assertNull(SubtitleFinder.mimeTypeFor("txt"))
        assertEquals(MimeTypes.TEXT_VTT, SubtitleFinder.mimeTypeFor("VTT"))
    }

    @Test
    fun `a video without a dot still matches a plain subtitle`() {
        val found = SubtitleFinder.match("episode", listOf("episode.srt"))
        assertEquals(1, found.size)
    }
}
