package com.vortexa.core.common.file

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioFileTypesTest {

    @Test
    fun `common audio containers are accepted`() {
        listOf("song.mp3", "a.FLAC", "voice.m4a", "track.opus", "x.mka").forEach {
            assertTrue(it, AudioFileTypes.isAudio(it))
        }
    }

    @Test
    fun `video and documents are not music`() {
        assertFalse(AudioFileTypes.isAudio("movie.mkv"))
        assertFalse(AudioFileTypes.isAudio("clip.mp4"))
        assertFalse(AudioFileTypes.isAudio("notes.txt"))
        assertFalse(AudioFileTypes.isAudio("no-extension"))
        assertFalse(AudioFileTypes.isAudio(".hidden"))
    }

    @Test
    fun `paths look only at the file name`() {
        assertEquals("mp3", AudioFileTypes.extensionOf("/music/Album/track.MP3"))
        assertTrue(AudioFileTypes.isAudio("/music/Album/track.MP3"))
        assertFalse(AudioFileTypes.isAudio("/music/Album/cover.jpg"))
    }
}
