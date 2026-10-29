package com.vortexa.feature.downloader.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadCategoryTest {

    @Test
    fun `file extensions decide the category`() {
        assertEquals(DownloadCategory.VIDEO, DownloadCategory.classify("clip.MKV"))
        assertEquals(DownloadCategory.AUDIO, DownloadCategory.classify("song.flac"))
        assertEquals(DownloadCategory.IMAGE, DownloadCategory.classify("photo.jpeg"))
        assertEquals(DownloadCategory.DOCUMENT, DownloadCategory.classify("book.pdf"))
        assertEquals(DownloadCategory.APP, DownloadCategory.classify("build.apk"))
        assertEquals(DownloadCategory.ARCHIVE, DownloadCategory.classify("backup.zip"))
    }

    @Test
    fun `the mime type is used when the name says nothing`() {
        assertEquals(DownloadCategory.VIDEO, DownloadCategory.classify("stream", "video/mp4"))
        assertEquals(DownloadCategory.AUDIO, DownloadCategory.classify("stream", "audio/mpeg"))
        assertEquals(DownloadCategory.IMAGE, DownloadCategory.classify("stream", "image/webp"))
        assertEquals(DownloadCategory.DOCUMENT, DownloadCategory.classify("stream", "text/plain"))
        assertEquals(DownloadCategory.APP, DownloadCategory.classify("file", "application/vnd.android.package-archive"))
        assertEquals(DownloadCategory.ARCHIVE, DownloadCategory.classify("file", "application/zip"))
    }

    @Test
    fun `an extension beats a vague mime type`() {
        assertEquals(
            DownloadCategory.VIDEO,
            DownloadCategory.classify("movie.mp4", "application/octet-stream"),
        )
    }

    @Test
    fun `unknown names and types fall back to other`() {
        assertEquals(DownloadCategory.OTHER, DownloadCategory.classify("file.xyz"))
        assertEquals(DownloadCategory.OTHER, DownloadCategory.classify("no-extension"))
        assertEquals(DownloadCategory.OTHER, DownloadCategory.classify("file.xyz", "application/x-whatever"))
    }

    @Test
    fun `a dotted path still reads the last segment`() {
        assertEquals(DownloadCategory.AUDIO, DownloadCategory.classify("v1.2.track.mp3"))
    }
}
