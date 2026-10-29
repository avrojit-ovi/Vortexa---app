package com.vortexa.core.common.file

import java.util.Locale

/**
 * Which files the music library accepts.
 *
 * Kept pure (and separate from the video classifier) because the two lists drift
 * apart: `.mka` is music here, `.mkv` is video there, and neither should pull in
 * the other module's code.
 */
object AudioFileTypes {

    val extensions: Set<String> = setOf(
        "mp3", "m4a", "aac", "flac", "wav", "ogg", "oga", "opus", "wma", "amr", "mka", "aiff", "alac",
    )

    fun extensionOf(fileName: String): String? {
        val name = fileName.substringAfterLast('/').substringAfterLast('\\')
        val dot = name.lastIndexOf('.')
        if (dot <= 0 || dot == name.lastIndex) return null
        return name.substring(dot + 1).lowercase(Locale.US)
    }

    fun isAudio(fileName: String): Boolean = extensionOf(fileName) in extensions
}
