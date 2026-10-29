package com.vortexa.app.search

import com.vortexa.core.database.entity.DownloadEntity
import com.vortexa.core.database.entity.DownloadStatus
import com.vortexa.core.common.format.Formatters
import com.vortexa.feature.music.library.LocalTrack
import java.util.Locale

enum class SearchSource { DOWNLOAD, MUSIC }

/** One result row. [fileName] is resolved to a path by the caller, [path] is ready to open. */
data class SearchHit(
    val key: String,
    val title: String,
    val subtitle: String,
    val source: SearchSource,
    val fileName: String? = null,
    val path: String? = null,
)

/**
 * The matching rules for the one search box, kept pure so the behaviour is
 * pinned by tests: a two-character minimum, case-insensitive matching on the
 * names people actually see, finished downloads first.
 */
object SearchIndex {

    const val MIN_QUERY_LENGTH = 2
    const val MAX_RESULTS = 40

    fun search(
        query: String,
        downloads: List<DownloadEntity>,
        tracks: List<LocalTrack>,
    ): List<SearchHit> {
        val needle = query.trim().lowercase(Locale.getDefault())
        if (needle.length < MIN_QUERY_LENGTH) return emptyList()

        val downloadHits = downloads
            .asSequence()
            .filter { it.status == DownloadStatus.COMPLETED }
            .filter { it.fileName.lowercase(Locale.getDefault()).contains(needle) }
            .map { download ->
                SearchHit(
                    key = "download:${download.id}",
                    title = download.fileName,
                    subtitle = Formatters.bytes(download.totalBytes),
                    source = SearchSource.DOWNLOAD,
                    fileName = download.fileName,
                )
            }

        val trackHits = tracks
            .asSequence()
            .filter { track -> track.matches(needle) }
            .map { track ->
                SearchHit(
                    key = "music:${track.path}",
                    title = track.title,
                    subtitle = listOfNotNull(track.artist, track.album)
                        .joinToString(" · ")
                        .ifBlank { Formatters.duration(track.durationMs / 1000) },
                    source = SearchSource.MUSIC,
                    path = track.path,
                )
            }

        return (downloadHits + trackHits).take(MAX_RESULTS).toList()
    }

    private fun LocalTrack.matches(needle: String): Boolean {
        val haystack = listOfNotNull(title, artist, album).joinToString(" ").lowercase(Locale.getDefault())
        return haystack.contains(needle)
    }
}
