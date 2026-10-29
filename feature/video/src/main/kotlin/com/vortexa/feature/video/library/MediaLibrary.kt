package com.vortexa.feature.video.library

import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One playable file on disk. */
data class LibraryItem(
    val path: String,
    val name: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val width: Int,
    val height: Int,
) {
    val kind: MediaKind get() = MediaClassifier.kindOf(name)
    val isVideo: Boolean get() = kind == MediaKind.VIDEO
}

/**
 * The library is the file system, not a second database.
 *
 * Everything Vortexa downloads lands in its own folder, so scanning it is what
 * keeps the player honest: a file the user deleted elsewhere never shows up as
 * a broken row. Files are only read for metadata (duration, resolution).
 */
@Singleton
class MediaLibrary @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    suspend fun load(): List<LibraryItem> = withContext(Dispatchers.IO) { scan(roots()) }

    internal fun roots(): List<File> = buildList {
        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let(::add)
        add(File(context.filesDir, DOWNLOAD_DIR))
    }.distinctBy { it.absolutePath }

    internal fun scan(directories: List<File>): List<LibraryItem> {
        val files = directories
            .filter { it.isDirectory && it.canRead() }
            .flatMap { root ->
                runCatching {
                    root.walkTopDown().filter { it.isFile }.toList()
                }.getOrDefault(emptyList())
            }

        return files
            .filter { MediaClassifier.isPlayable(it.name) }
            .mapNotNull { file -> runCatching { toItem(file) }.getOrNull() }
            .filter { it.sizeBytes > 0L }
            .sortedWith(compareBy<LibraryItem>({ !it.isVideo }, { it.name.lowercase() }))
    }

    private fun toItem(file: File): LibraryItem {
        val (duration, width, height) = readMetadata(file)
        return LibraryItem(
            path = file.absolutePath,
            name = file.name,
            sizeBytes = file.length(),
            durationMs = duration,
            width = width,
            height = height,
        )
    }

    private fun readMetadata(file: File): Triple<Long, Int, Int> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            Triple(
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0,
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0,
            )
        } finally {
            runCatching { retriever.release() }
        }
    }

    companion object {
        private const val DOWNLOAD_DIR = "downloads"
    }
}
