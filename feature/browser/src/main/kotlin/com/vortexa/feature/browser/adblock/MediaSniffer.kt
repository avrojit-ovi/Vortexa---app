package com.vortexa.feature.browser.adblock

import java.util.Locale

/** Something worth handing to the downloader, spotted on a page. */
data class SniffedMedia(
    val url: String,
    val kind: Kind,
    val suggestedName: String,
)

enum class Kind { VIDEO, AUDIO, HLS, DASH, FILE }

/**
 * Decides whether a request the WebView makes is media the user may want.
 *
 * It only looks at the URL — no page parsing, no JavaScript — so it never
 * mistakes an ad frame for a video, and it never fetches anything on its own.
 */
object MediaSniffer {

    private val VIDEO = setOf("mp4", "m4v", "webm", "mkv", "mov", "3gp")
    private val AUDIO = setOf("mp3", "m4a", "aac", "flac", "ogg", "opus", "wav")
    private val FILE = setOf(
        "zip", "apk", "pdf", "epub", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
        "tar", "gz", "7z", "rar", "iso", "exe", "dmg",
    )
    private const val MAX_URL = 2_048

    fun classify(url: String, mimeType: String? = null): SniffedMedia? {
        if (url.length > MAX_URL) return null
        if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) return null

        val clean = url.substringBefore('#')
        val path = clean.substringBefore('?')
        val extension = path.substringAfterLast('/')
            .substringAfterLast('.', "")
            .lowercase(Locale.US)

        val kind = when {
            extension == "m3u8" -> Kind.HLS
            extension == "mpd" -> Kind.DASH
            extension in VIDEO -> Kind.VIDEO
            extension in AUDIO -> Kind.AUDIO
            extension in FILE -> Kind.FILE
            mimeType?.startsWith("video/") == true -> Kind.VIDEO
            mimeType?.startsWith("audio/") == true -> Kind.AUDIO
            mimeType == "application/vnd.apple.mpegurl" -> Kind.HLS
            else -> return null
        }

        val name = path.substringAfterLast('/').ifBlank { "download.${extension.ifBlank { "bin" }}" }
        return SniffedMedia(url = clean, kind = kind, suggestedName = name)
    }
}
