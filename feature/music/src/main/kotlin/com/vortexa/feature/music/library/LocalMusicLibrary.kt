package com.vortexa.feature.music.library

import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.Environment
import com.vortexa.core.common.file.AudioFileTypes
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One audio file on disk, with the tags that make a list readable. */
data class LocalTrack(
    val path: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val durationMs: Long,
    val sizeBytes: Long,
) {
    val subtitle: String?
        get() = listOfNotNull(artist, album).firstOrNull()?.takeIf { it.isNotBlank() }
}

/**
 * The device library, read straight from the download folder.
 *
 * No MediaStore permission is needed for the app's own folder, which is why the
 * library never asks for access to the whole phone, and a file the user deleted
 * elsewhere simply stops appearing.
 */
@Singleton
class LocalMusicLibrary @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    suspend fun load(): List<LocalTrack> = withContext(Dispatchers.IO) {
        val roots = buildList {
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let(::add)
            add(File(context.filesDir, "downloads"))
        }.distinctBy { it.absolutePath }

        roots.filter { it.isDirectory && it.canRead() }
            .flatMap { root ->
                runCatching { root.walkTopDown().filter { it.isFile }.toList() }
                    .getOrDefault(emptyList())
            }
            .filter { AudioFileTypes.isAudio(it.name) }
            .sortedBy { it.name.lowercase() }
            .mapNotNull { file -> runCatching { read(file) }.getOrNull() }
            .filter { it.sizeBytes > 0L }
    }

    private fun read(file: File): LocalTrack {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            fun tag(key: Int) = retriever.extractMetadata(key)?.takeIf { it.isNotBlank() }
            LocalTrack(
                path = file.absolutePath,
                title = tag(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: file.name,
                artist = tag(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                album = tag(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                durationMs = tag(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
                sizeBytes = file.length(),
            )
        } finally {
            runCatching { retriever.release() }
        }
    }
}
