package com.vortexa.feature.downloader

import android.content.Context
import android.os.Environment
import com.vortexa.core.common.file.FileNameSanitizer
import com.vortexa.core.database.dao.DownloadDao
import com.vortexa.core.database.entity.DownloadEntity
import com.vortexa.core.database.entity.DownloadStatus
import com.vortexa.feature.downloader.engine.DownloadEngine
import com.vortexa.feature.downloader.engine.DownloadEvent
import com.vortexa.feature.downloader.engine.DownloadSpec
import com.vortexa.feature.downloader.engine.SegmentCodec
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * Owns the queue: one coroutine per active download, an application-scoped
 * scope so a screen rotation does not cancel anything, and Room as the single
 * source of truth for what the UI shows.
 */
@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: DownloadDao,
    private val engine: DownloadEngine,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<Long, Job>()
    private val _speeds = MutableStateFlow<Map<Long, Long>>(emptyMap())

    /** Live speed per download id, in bytes/second. */
    val speeds: StateFlow<Map<Long, Long>> = _speeds

    fun observeAll() = dao.observeAll()

    suspend fun enqueue(url: String, partCount: Int = DEFAULT_PARTS): Long? {
        val parsed = runCatching { url.trim().toHttpUrl() }.getOrNull() ?: return null
        val fileName = FileNameSanitizer.sanitize(
            rawName = parsed.pathSegments.lastOrNull(),
            fallback = "download.bin",
        )
        val id = dao.upsert(
            DownloadEntity(
                url = parsed.toString(),
                fileName = fileName,
                segmentCount = partCount,
                status = DownloadStatus.QUEUED,
            ),
        )
        start(id)
        return id
    }

    fun start(id: Long) {
        if (jobs[id]?.isActive == true) return
        DownloadService.ensureRunning(context)
        jobs[id] = scope.launch {
            runCatching { runDownload(id) }.onFailure { error ->
                dao.updateStatus(id, DownloadStatus.FAILED)
                _speeds.update { it - id }
            }
        }
    }

    fun pause(id: Long) {
        jobs.remove(id)?.cancel()
        _speeds.update { it - id }
        scope.launch { dao.updateStatus(id, DownloadStatus.PAUSED) }
    }

    fun pauseAll() {
        jobs.keys.toList().forEach { pause(it) }
    }

    fun cancel(id: Long) {
        jobs.remove(id)?.cancel()
        _speeds.update { it - id }
        scope.launch {
            dao.updateStatus(id, DownloadStatus.CANCELED)
            targetFile(dao.findById(id)?.fileName ?: return@launch).delete()
        }
    }

    suspend fun remove(id: Long) {
        cancel(id)
        dao.deleteById(id)
    }

    private suspend fun runDownload(id: Long) {
        val entity = dao.findById(id) ?: return
        dao.updateStatus(id, DownloadStatus.RUNNING)

        val spec = DownloadSpec(
            url = entity.url.toHttpUrl(),
            file = targetFile(entity.fileName),
            partCount = entity.segmentCount,
            // Per-range offsets from the previous session; empty means fresh.
            resumeStates = SegmentCodec.decode(entity.segments),
        )

        var lastPersist = 0L
        engine.download(spec).collect { event ->
            when (event) {
                is DownloadEvent.Metadata -> {
                    val name = FileNameSanitizer.sanitize(
                        event.metadata.suggestedFileName ?: entity.fileName,
                        fallback = entity.fileName,
                    )
                    dao.updateMetadata(
                        id = id,
                        fileName = name,
                        total = event.metadata.totalBytes,
                        etag = event.metadata.etag,
                        lastModified = event.metadata.lastModified,
                    )
                }

                is DownloadEvent.Progress -> {
                    _speeds.update { it + (id to event.bytesPerSecond) }
                    val now = System.currentTimeMillis()
                    if (now - lastPersist >= PERSIST_INTERVAL_MS) {
                        lastPersist = now
                        dao.updateProgressDetail(
                            id = id,
                            bytes = event.downloadedBytes,
                            total = event.totalBytes,
                            segments = SegmentCodec.encode(event.parts),
                        )
                    }
                }

                DownloadEvent.Completed -> {
                    _speeds.update { it - id }
                    dao.updateProgressDetail(
                        id = id,
                        bytes = spec.file.length(),
                        total = spec.file.length(),
                        segments = null,
                    )
                    dao.updateStatus(id, DownloadStatus.COMPLETED)
                }

                is DownloadEvent.Failed -> {
                    _speeds.update { it - id }
                    // The partial file and its per-range state stay on disk so the
                    // user can press resume instead of starting over.
                    dao.updateStatus(id, DownloadStatus.FAILED)
                }
            }
        }
    }

    /** Where a finished download lives, for the player and for search. */
    fun fileFor(fileName: String): File = targetFile(fileName)

    private fun targetFile(fileName: String): File {
        val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(context.filesDir, "downloads")
        if (!directory.exists()) directory.mkdirs()
        return File(directory, FileNameSanitizer.sanitize(fileName))
    }

    companion object {
        const val DEFAULT_PARTS = 8
        private const val PERSIST_INTERVAL_MS = 1_000L
    }
}
