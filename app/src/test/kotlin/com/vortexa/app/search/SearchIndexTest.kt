package com.vortexa.app.search

import com.vortexa.core.database.entity.DownloadEntity
import com.vortexa.core.database.entity.DownloadStatus
import com.vortexa.feature.music.library.LocalTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchIndexTest {

    private fun download(
        id: Long,
        name: String,
        status: DownloadStatus = DownloadStatus.COMPLETED,
        total: Long = 1_000,
    ) = DownloadEntity(
        id = id,
        url = "https://example.com/$name",
        fileName = name,
        status = status,
        totalBytes = total,
    )

    private fun track(title: String, artist: String? = null, album: String? = null) =
        LocalTrack(
            path = "/music/$title.mp3",
            title = title,
            artist = artist,
            album = album,
            durationMs = 200_000,
            sizeBytes = 4_000_000,
        )

    @Test
    fun `a query shorter than two characters returns nothing`() {
        assertTrue(SearchIndex.search("a", listOf(download(1, "a.mp4")), emptyList()).isEmpty())
        assertTrue(SearchIndex.search("", listOf(download(1, "a.mp4")), emptyList()).isEmpty())
        assertTrue(SearchIndex.search("   ", listOf(download(1, "a.mp4")), emptyList()).isEmpty())
    }

    @Test
    fun `downloads match on the file name, ignoring case`() {
        val hits = SearchIndex.search("CLIP", listOf(download(1, "clip.mp4")), emptyList())
        assertEquals(1, hits.size)
        assertEquals("clip.mp4", hits.first().title)
        assertEquals(SearchSource.DOWNLOAD, hits.first().source)
        assertEquals("clip.mp4", hits.first().fileName)
    }

    @Test
    fun `unfinished downloads are left out`() {
        val rows = listOf(
            download(1, "movie.mp4", status = DownloadStatus.RUNNING),
            download(2, "movie2.mp4", status = DownloadStatus.COMPLETED),
        )
        val hits = SearchIndex.search("movie", rows, emptyList())
        assertEquals(listOf("movie2.mp4"), hits.map { it.title })
    }

    @Test
    fun `music matches on title, artist and album`() {
        val tracks = listOf(track("Kotha", artist = "Arijit", album = "Album X"))
        assertEquals(1, SearchIndex.search("kotha", emptyList(), tracks).size)
        assertEquals(1, SearchIndex.search("arijit", emptyList(), tracks).size)
        assertEquals(1, SearchIndex.search("album x", emptyList(), tracks).size)
    }

    @Test
    fun `music hits carry a playable path`() {
        val hits = SearchIndex.search("kotha", emptyList(), listOf(track("Kotha")))
        assertEquals("/music/Kotha.mp3", hits.first().path)
    }

    @Test
    fun `downloads come first, then music`() {
        val hits = SearchIndex.search(
            "kotha",
            listOf(download(1, "kotha.mp4")),
            listOf(track("Kotha")),
        )
        assertEquals(listOf(SearchSource.DOWNLOAD, SearchSource.MUSIC), hits.map { it.source })
    }

    @Test
    fun `the result list is capped`() {
        val rows = (1..60).map { download(it.toLong(), "clip-$it.mp4") }
        assertEquals(SearchIndex.MAX_RESULTS, SearchIndex.search("clip", rows, emptyList()).size)
    }

    @Test
    fun `no match means no rows`() {
        assertTrue(SearchIndex.search("zzz", listOf(download(1, "clip.mp4")), emptyList()).isEmpty())
    }
}
