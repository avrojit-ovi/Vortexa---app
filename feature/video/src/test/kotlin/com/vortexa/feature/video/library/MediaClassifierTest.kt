package com.vortexa.feature.video.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaClassifierTest {

    @Test
    fun `common video containers are video`() {
        listOf("a.mp4", "a.MKV", "movie.webm", "clip.mov", "x.3gp").forEach {
            assertEquals(it, MediaKind.VIDEO, MediaClassifier.kindOf(it))
        }
    }

    @Test
    fun `audio containers are audio`() {
        listOf("song.mp3", "track.FLAC", "voice.m4a", "sound.ogg").forEach {
            assertEquals(it, MediaKind.AUDIO, MediaClassifier.kindOf(it))
        }
    }

    @Test
    fun `extensions are matched case insensitively`() {
        assertEquals(MediaKind.VIDEO, MediaClassifier.kindOf("SHOW.S01E01.MP4"))
        assertEquals("mp4", MediaClassifier.extensionOf("SHOW.S01E01.MP4"))
    }

    @Test
    fun `names without a real extension are not playable`() {
        assertNull(MediaClassifier.extensionOf("download"))
        assertNull(MediaClassifier.extensionOf("archive."))
        assertNull(MediaClassifier.extensionOf(".hidden"))
        assertFalse(MediaClassifier.isPlayable("tarball.tar.gz"))
        assertFalse(MediaClassifier.isPlayable("report.pdf"))
    }

    @Test
    fun `double extensions use the last one`() {
        assertEquals(MediaKind.VIDEO, MediaClassifier.kindOf("episode.mkv.part.mp4"))
        assertEquals("mp4", MediaClassifier.extensionOf("episode.mp4"))
        // A browser-style suffix hides the real container until the download finishes.
        assertEquals(MediaKind.OTHER, MediaClassifier.kindOf("song.mp3.download"))
    }

    @Test
    fun `paths with directories only look at the file name`() {
        assertEquals("gif", MediaClassifier.extensionOf("/sdcard/Downloads/holiday.GIF"))
        assertEquals(MediaKind.OTHER, MediaClassifier.kindOf("/sdcard/Downloads/holiday.GIF"))
        assertEquals(MediaKind.VIDEO, MediaClassifier.kindOf("/sdcard/Downloads/clip.mp4"))
    }
}
