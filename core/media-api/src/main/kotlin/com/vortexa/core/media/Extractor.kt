package com.vortexa.core.media

/**
 * The whole online-media contract.
 *
 * Nothing in this file knows about a specific service. A source is anything
 * that can answer "what matches this search" and "where is the playable stream"
 * — a podcast feed, a self-hosted server, an IPTV playlist, or a third-party
 * plugin the user installs themselves.
 *
 * Rules a plugin must follow:
 *  - `search` and `resolve` are suspend and must be safe to cancel.
 *  - Failures throw [ExtractionException]; the host never crashes on them.
 *  - Stream URLs are not stored for longer than needed; they expire.
 */
interface Extractor {

    /** Stable, unique, reverse-DNS style id: `com.example.mysource`. */
    val id: String

    /** Shown in the source picker, never translated. */
    val displayName: String

    /** Free-form version string shown in settings. */
    val version: String

    val capabilities: Set<Capability>

    suspend fun search(query: MediaQuery): List<MediaTrack>

    /** Turns a search result into something a player can open. */
    suspend fun resolve(track: MediaTrack): StreamLink

    suspend fun playlists(): List<MediaPlaylist> = emptyList()

    suspend fun playlistItems(playlistId: String): List<MediaTrack> = emptyList()
}

enum class Capability { SEARCH, RESOLVE, PLAYLISTS }

data class MediaQuery(
    val text: String,
    val limit: Int = 25,
)

data class MediaTrack(
    /** Id inside the owning extractor; opaque to the app. */
    val id: String,
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val durationMs: Long = 0L,
    val thumbnailUrl: String? = null,
    val isLive: Boolean = false,
    /** Filled in by the registry so a result can be traced to its source. */
    val extractorId: String = "",

    /** Official web player for this item, used when resolution fails. */
    val fallbackUrl: String? = null,
) {
    val subtitle: String?
        get() = listOfNotNull(artist, album).firstOrNull()?.takeIf { it.isNotBlank() }
}

data class StreamLink(
    val url: String,
    val mimeType: String? = null,
    val isHls: Boolean = false,
    /** Signed CDNs often need a Referer or a User-Agent. */
    val headers: Map<String, String> = emptyMap(),
    val expiresAtEpochMs: Long? = null,
) {
    fun isExpired(nowMs: Long): Boolean = expiresAtEpochMs?.let { nowMs >= it } ?: false
}

data class MediaPlaylist(
    val id: String,
    val title: String,
    val itemCount: Int = 0,
    val thumbnailUrl: String? = null,
)

/**
 * A source failing is normal: the service changed, the plugin is old, the
 * network dropped. [userMessage] is what the UI shows instead of a stack trace.
 */
class ExtractionException(
    message: String,
    val userMessage: String = message,
    cause: Throwable? = null,
) : Exception(message, cause)

enum class ExtractorError {
    DISABLED,
    PLUGIN_LOAD_FAILED,
    NETWORK,
    RESOLVE_FAILED,
    UNKNOWN,
}
