package com.vortexa.core.media

import java.net.URLDecoder
import java.util.Locale

/**
 * The built-in source: a plain URL the user pasted or shared.
 *
 * It never scrapes anything. If the text is an http(s) URL the app shows it as
 * one playable item, which is what makes "here is my stream" work for HLS
 * radio, a podcast file, a self-hosted track or a CDN link.
 */
class DirectLinkExtractor : Extractor {

    override val id: String = ID
    override val displayName: String = "Direct link"
    override val version: String = "1.0"
    override val capabilities: Set<Capability> = setOf(Capability.SEARCH, Capability.RESOLVE)

    override suspend fun search(query: MediaQuery): List<MediaTrack> {
        val link = DirectLinkParser.parse(query.text) ?: return emptyList()
        return listOf(
            MediaTrack(
                id = link.url,
                title = link.displayName,
                artist = link.host,
                isLive = link.isHls,
                extractorId = id,
            ),
        )
    }

    override suspend fun resolve(track: MediaTrack): StreamLink {
        val link = DirectLinkParser.parse(track.id)
            ?: throw ExtractionException("Not a playable link: ${track.id}")
        return StreamLink(url = link.url, isHls = link.isHls)
    }

    companion object {
        const val ID = "builtin.direct"
    }
}

/** A parsed, playable direct link. */
data class DirectLink(
    val url: String,
    val host: String,
    val displayName: String,
    val isHls: Boolean,
)

/**
 * URL handling with no network access, so it is fully unit tested.
 *
 * HLS playlists (`.m3u8`) and DASH manifests (`.mpd`) are marked as HLS-like
 * adaptive sources; Media3 picks the right renderer from the mime type.
 */
object DirectLinkParser {

    private val HLS_EXTENSIONS = setOf("m3u8", "m3u")
    private val DASH_EXTENSIONS = setOf("mpd")
    private val MEDIA_EXTENSIONS = setOf(
        "mp4", "m4v", "mkv", "webm", "mov", "3gp", "ts", "m2ts", "mpg", "mpeg",
        "mp3", "m4a", "aac", "flac", "wav", "ogg", "oga", "opus", "weba",
    )

    fun parse(rawText: String): DirectLink? {
        val text = rawText.trim()
        if (!text.startsWith("http://", ignoreCase = true) && !text.startsWith("https://", ignoreCase = true)) {
            return null
        }
        val withoutFragment = text.substringBefore('#')
        val host = runCatching { java.net.URI(withoutFragment).host }.getOrNull()
        if (host.isNullOrBlank()) return null

        val path = runCatching { java.net.URI(withoutFragment).path }.getOrNull().orEmpty()
        val extension = extensionOf(path)

        val isHls = extension in HLS_EXTENSIONS || extension in DASH_EXTENSIONS
        if (!isHls && extension !in MEDIA_EXTENSIONS) return null

        val name = path.substringAfterLast('/')
            .takeIf { it.isNotBlank() }
            ?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }
            ?: host

        return DirectLink(
            url = withoutFragment,
            host = host,
            displayName = name,
            isHls = isHls,
        )
    }

    private fun extensionOf(path: String): String? {
        val name = path.substringAfterLast('/')
        val dot = name.lastIndexOf('.')
        if (dot <= 0 || dot == name.lastIndex) return null
        return name.substring(dot + 1).lowercase(Locale.US)
    }
}
