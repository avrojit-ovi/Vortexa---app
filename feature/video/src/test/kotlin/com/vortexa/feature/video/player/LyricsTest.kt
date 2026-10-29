package com.vortexa.feature.video.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LyricsTest {

    @Test
    fun `timed lines are parsed in order`() {
        val lines = LrcParser.parse(
            """
            [00:12.34]First line
            [00:15.00]Second line
            """.trimIndent(),
        )
        assertEquals(2, lines.size)
        assertEquals(12_340L, lines[0].atMillis)
        assertEquals("First line", lines[0].text)
        assertEquals(15_000L, lines[1].atMillis)
    }

    @Test
    fun `several timestamps on one line repeat it`() {
        val lines = LrcParser.parse("[00:01.00][00:31.00]Chorus")
        assertEquals(listOf(1_000L, 31_000L), lines.map { it.atMillis })
        assertEquals(listOf("Chorus", "Chorus"), lines.map { it.text })
    }

    @Test
    fun `the offset tag shifts every line`() {
        val lines = LrcParser.parse(
            """
            [offset:+500]
            [00:01.00]Late
            """.trimIndent(),
        )
        assertEquals(1_500L, lines.single().atMillis)
    }

    @Test
    fun `metadata and unsynced text are ignored`() {
        val lines = LrcParser.parse(
            """
            [ar:Someone]
            [ti:A song]
            just a plain paragraph
            [00:05.00]Only this counts
            """.trimIndent(),
        )
        assertEquals(1, lines.size)
        assertEquals("Only this counts", lines.single().text)
    }

    @Test
    fun `out of order input is sorted`() {
        val lines = LrcParser.parse("[00:20.00]Second\n[00:10.00]First")
        assertEquals(listOf("First", "Second"), lines.map { it.text })
    }

    @Test
    fun `hundredths and thousandths both read correctly`() {
        val lines = LrcParser.parse("[00:01.5]a\n[00:02.05]b\n[00:03.500]c")
        assertEquals(listOf(1_500L, 2_050L, 3_500L), lines.map { it.atMillis })
    }

    @Test
    fun `the current line advances with the position`() {
        val lines = listOf(LyricLine(1_000, "one"), LyricLine(2_000, "two"))
        assertNull(LyricsPosition.lineAt(lines, 999))
        assertEquals("one", LyricsPosition.lineAt(lines, 1_000)?.text)
        assertEquals("two", LyricsPosition.lineAt(lines, 9_000)?.text)
    }

    @Test
    fun `a sidecar lyrics file is found by name`() {
        val found = LyricsFinder.find(
            audioPath = "/music/Song.mp3",
            candidates = listOf("Song.mp3", "Song.lrc", "Other.lrc"),
        )
        assertEquals("Song.lrc", found)
        assertNull(LyricsFinder.find("/music/Song.mp3", listOf("Other.lrc", "Song.txt")))
    }
}
