package com.vortexa.feature.video.library

/** What a downloaded file can be played as. */
enum class MediaKind { VIDEO, AUDIO, OTHER }

/**
 * Decides whether a file belongs in the library, and whether it is video or
 * audio. Pure string logic so the JVM tests can cover the awkward cases
 * (upper case, double extensions, dot files, no extension).
 */
object MediaClassifier {

    private val VIDEO_EXTENSIONS = setOf(
        "mp4", "m4v", "mkv", "webm", "avi", "mov", "3gp", "3g2",
        "flv", "ts", "m2ts", "mpg", "mpeg", "wmv", "ogv", "mts",
    )

    private val AUDIO_EXTENSIONS = setOf(
        "mp3", "m4a", "aac", "flac", "wav", "ogg", "oga", "opus", "wma", "amr", "mka", "aiff",
    )

    /** Lowercase extension without the dot, or null when the name has none. */
    fun extensionOf(fileName: String): String? {
        val name = fileName.substringAfterLast('/').substringAfterLast('\\')
        val dot = name.lastIndexOf('.')
        if (dot <= 0 || dot == name.lastIndex) return null
        return name.substring(dot + 1).lowercase()
    }

    fun kindOf(fileName: String): MediaKind = when (extensionOf(fileName)) {
        in VIDEO_EXTENSIONS -> MediaKind.VIDEO
        in AUDIO_EXTENSIONS -> MediaKind.AUDIO
        else -> MediaKind.OTHER
    }

    fun isPlayable(fileName: String): Boolean = kindOf(fileName) != MediaKind.OTHER
}
