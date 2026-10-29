package com.vortexa.feature.video.player

import androidx.media3.common.MimeTypes
import java.util.Locale

/** A subtitle file sitting next to the video. */
data class SidecarSubtitle(
    val path: String,
    val label: String,
    val language: String?,
    val mimeType: String,
)

/**
 * Real players pick up `movie.srt` next to `movie.mkv`, including language
 * suffixes (`movie.en.srt`, `movie.bn.srt`). This is that rule, with no file
 * system access, so it is unit tested.
 */
object SubtitleFinder {

    private val MIME_BY_EXTENSION = mapOf(
        "srt" to MimeTypes.APPLICATION_SUBRIP,
        "vtt" to MimeTypes.TEXT_VTT,
        "ssa" to MimeTypes.TEXT_SSA,
        "ass" to MimeTypes.TEXT_SSA,
        "ttml" to MimeTypes.APPLICATION_TTML,
        "dfxp" to MimeTypes.APPLICATION_TTML,
    )

    fun mimeTypeFor(extension: String): String? = MIME_BY_EXTENSION[extension.lowercase(Locale.US)]

    private val LANGUAGE_TAG = Regex("[a-z]{2,3}(-[a-z0-9]{2,8})*")
    private val LANGUAGE_WORDS = setOf("forced", "sdh", "hi", "cc")

    private fun isLanguageTag(suffix: String): Boolean =
        suffix.matches(LANGUAGE_TAG) || suffix in LANGUAGE_WORDS

    /**
     * [candidates] are full paths or names in the same folder as [videoPath].
     * A match is the video's base name, optionally followed by a language tag.
     */
    fun match(videoPath: String, candidates: List<String>): List<SidecarSubtitle> {
        val videoName = videoPath.substringAfterLast('/').substringAfterLast('\\')
        val base = videoName.substringBeforeLast('.', videoName).lowercase(Locale.US)
        if (base.isEmpty()) return emptyList()

        return candidates.mapNotNull { candidate ->
            val name = candidate.substringAfterLast('/').substringAfterLast('\\')
            val stem = name.substringBeforeLast('.', name).lowercase(Locale.US)
            val extension = name.substringAfterLast('.', "").lowercase(Locale.US)
            val mime = mimeTypeFor(extension) ?: return@mapNotNull null
            if (stem != base && !stem.startsWith("$base.")) return@mapNotNull null

            val suffix = stem.removePrefix(base).removePrefix(".").takeIf { it.isNotBlank() }
            // `movie.1995.srt` is a different cut, not a language variant.
            if (stem != base && suffix != null && !isLanguageTag(suffix)) return@mapNotNull null

            val language = suffix
            SidecarSubtitle(
                path = candidate,
                label = if (language != null) {
                    "${language.uppercase(Locale.US)} · ${extension.uppercase(Locale.US)}"
                } else {
                    extension.uppercase(Locale.US)
                },
                language = language,
                mimeType = mime,
            )
        }.sortedBy { it.label }
    }
}
