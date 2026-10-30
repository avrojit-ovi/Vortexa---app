package com.vortexa.feature.downloader

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import com.vortexa.core.common.file.FileNameSanitizer
import com.vortexa.core.database.dao.DownloadDao
import com.vortexa.core.database.entity.DownloadEntity
import com.vortexa.core.common.preferences.ProxyType
import com.vortexa.core.common.preferences.SettingsRepository
import com.vortexa.core.database.entity.DownloadStatus
import com.vortexa.feature.downloader.engine.DownloadEngine
import com.vortexa.feature.downloader.engine.DownloadEvent
import com.vortexa.feature.downloader.engine.DownloadSchedule
import com.vortexa.feature.downloader.engine.DownloadSink
import com.vortexa.feature.downloader.engine.DownloadSpec
import com.vortexa.feature.downloader.engine.ProxyKind
import com.vortexa.feature.downloader.engine.ProxySettings
import com.vortexa.feature.downloader.engine.SegmentCodec
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.channels.FileChannel
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
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
    private val settings: SettingsRepository,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<Long, Job>()
    private val _speeds = MutableStateFlow<Map<Long, Long>>(emptyMap())

    /** Live speed per download id, in bytes/second. */
    val speeds: StateFlow<Map<Long, Long>> = _speeds

    @Volatile
    private var speedLimitBytesPerSecond: Long = 0L

    @Volatile
    private var schedule = DownloadSchedule()

    @Volatile
    private var proxy: ProxySettings? = null

    init {
        // Both settings are read once and kept current, so a running download
        // picks up a new cap on its next chunk instead of needing a restart.
        scope.launch {
            settings.preferences.collect { preferences ->
                speedLimitBytesPerSecond = preferences.speedLimitKbps.toLong() * 1024L
                schedule = DownloadSchedule(
                    enabled = preferences.scheduleEnabled,
                    startMinuteOfDay = preferences.scheduleStartMinute,
                    endMinuteOfDay = preferences.scheduleEndMinute,
                )
                // Decrypted on the IO collector, never on the main thread; the header
                // builder only ever sees a ready-to-use endpoint.
                proxy = ProxySettings.from(
                    enabled = preferences.proxyEnabled,
                    kind = if (preferences.proxyType == ProxyType.SOCKS) ProxyKind.SOCKS else ProxyKind.HTTP,
                    host = preferences.proxyHost,
                    port = preferences.proxyPort,
                    username = preferences.proxyUsername,
                    password = if (preferences.proxyEnabled) settings.proxyPassword() else "",
                )
            }
        }
        // Nothing else would notice when a scheduled window opens.
        scope.launch {
            while (true) {
                delay(SCHEDULE_CHECK_MS)
                if (schedule.enabled) startQueuedInsideWindow()
            }
        }
    }

    private suspend fun startQueuedInsideWindow() {
        if (!schedule.allows(minuteOfDay())) return
        dao.observeAll().first()
            .filter { it.status == DownloadStatus.QUEUED }
            .forEach { start(it.id) }
    }

    private fun minuteOfDay(): Int {
        val calendar = Calendar.getInstance()
        return calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
    }

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
        // Outside the window the item stays queued; the ticker starts it later.
        if (!schedule.allows(minuteOfDay())) return
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
            val row = dao.findById(id) ?: return@launch
            val safDocument = row.directory?.takeIf { it.startsWith("content:") }
            if (safDocument != null) {
                runCatching {
                    DocumentsContract.deleteDocument(context.contentResolver, Uri.parse(safDocument))
                }
            } else {
                targetFile(row.fileName).delete()
            }
        }
    }

    suspend fun remove(id: Long) {
        cancel(id)
        dao.deleteById(id)
    }

    private suspend fun runDownload(id: Long) {
        val entity = dao.findById(id) ?: return
        dao.updateStatus(id, DownloadStatus.RUNNING)

        // A chosen folder is a SAF tree; the document created inside it is saved in
        // `directory` so resume and delete reuse the same file. Without a folder the
        // download stays in app storage, as before.
        val tree = settings.preferences.first().downloadTreeUri
        var safDocument: Uri? = entity.directory?.takeIf { it.startsWith("content:") }?.let { Uri.parse(it) }
        if (safDocument == null && tree.isNotEmpty()) {
            safDocument = createSafDocument(Uri.parse(tree), entity.fileName)
            if (safDocument != null) dao.upsert(entity.copy(directory = safDocument.toString()))
        }
        val document = safDocument
        var lastTotal = 0L

        val spec = DownloadSpec(
            url = entity.url.toHttpUrl(),
            file = targetFile(entity.fileName),
            openSink = document?.let { uri -> { _: Long -> SafFileSink(context, uri) } },
            partCount = entity.segmentCount,
            // Per-range offsets from the previous session; empty means fresh.
            resumeStates = SegmentCodec.decode(entity.segments),
            bytesPerSecond = speedLimitBytesPerSecond,
            proxy = proxy,
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
                        // Stored so the UI can group a download whose name says nothing
                        // (`download.bin`) by what the server called it.
                        mimeType = event.metadata.contentType?.substringBefore(';')?.trim()
                            ?.takeIf { it.isNotEmpty() },
                        total = event.metadata.totalBytes,
                        etag = event.metadata.etag,
                        lastModified = event.metadata.lastModified,
                    )
                }

                is DownloadEvent.Progress -> {
                    _speeds.update { it + (id to event.bytesPerSecond) }
                    lastTotal = event.totalBytes
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
                    val size = if (document != null) lastTotal else spec.file.length()
                    dao.updateProgressDetail(
                        id = id,
                        bytes = size,
                        total = size,
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

    private fun createSafDocument(tree: Uri, fileName: String): Uri? = runCatching {
        val parent = DocumentsContract.buildDocumentUriUsingTree(
            tree,
            DocumentsContract.getTreeDocumentId(tree),
        )
        DocumentsContract.createDocument(context.contentResolver, parent, "application/octet-stream", fileName)
    }.getOrNull()

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
        private const val SCHEDULE_CHECK_MS = 60_000L
    }
}

/** Writes a download into a SAF document through a file descriptor. */
private class SafFileSink(context: Context, uri: Uri) : DownloadSink {
    private val pfd: ParcelFileDescriptor =
        context.contentResolver.openFileDescriptor(uri, "rw")
            ?: throw IOException("Cannot open $uri")
    private val stream = FileOutputStream(pfd.fileDescriptor)

    override val channel: FileChannel get() = stream.channel

    override fun length(): Long = stream.channel.size()

    override fun close() {
        runCatching { stream.close() }
        pfd.close()
    }
}
