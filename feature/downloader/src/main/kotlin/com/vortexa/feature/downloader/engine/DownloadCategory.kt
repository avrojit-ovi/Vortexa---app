package com.vortexa.feature.downloader.engine

import java.util.Locale

/**
 * What a download is, derived from its name and the server's MIME type.
 *
 * Deriving beats storing: the queue groups itself without a schema change, and a
 * file renamed on disk is re-grouped the next time the list is built.
 */
enum class DownloadCategory {
    VIDEO,
    AUDIO,
    IMAGE,
    DOCUMENT,
    APP,
    ARCHIVE,
    OTHER,
    ;

    companion object {

        private val VIDEO_EXT = setOf("mp4", "m4v", "mkv", "webm", "mov", "avi", "3gp", "ts", "flv")
        private val AUDIO_EXT = setOf("mp3", "m4a", "aac", "flac", "ogg", "opus", "wav", "mka")
        private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "avif")
        private val DOCUMENT_EXT =
            setOf("pdf", "epub", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv")
        private val APP_EXT = setOf("apk", "apks", "xapk", "aab")
        private val ARCHIVE_EXT = setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "iso")

        fun classify(fileName: String, mimeType: String? = null): DownloadCategory {
            val extension = fileName.substringAfterLast('.', "").lowercase(Locale.US)
            return when {
                extension in VIDEO_EXT -> VIDEO
                extension in AUDIO_EXT -> AUDIO
                extension in IMAGE_EXT -> IMAGE
                extension in DOCUMENT_EXT -> DOCUMENT
                extension in APP_EXT -> APP
                extension in ARCHIVE_EXT -> ARCHIVE
                else -> fromMimeType(mimeType)
            }
        }

        private fun fromMimeType(mimeType: String?): DownloadCategory = when {
            mimeType == null -> OTHER
            mimeType.startsWith("video/") -> VIDEO
            mimeType.startsWith("audio/") -> AUDIO
            mimeType.startsWith("image/") -> IMAGE
            mimeType.startsWith("text/") -> DOCUMENT
            mimeType == "application/pdf" -> DOCUMENT
            mimeType == "application/vnd.android.package-archive" -> APP
            mimeType.contains("zip") || mimeType.contains("compressed") -> ARCHIVE
            else -> OTHER
        }
    }
}
