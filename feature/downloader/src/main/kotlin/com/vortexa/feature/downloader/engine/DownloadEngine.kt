package com.vortexa.feature.downloader.engine

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** A range that still has to be fetched, plus how much of it is already on disk. */
data class SegmentState(
    val start: Long,
    val end: Long,
    val downloaded: Long = 0L,
)

data class DownloadSpec(
    val url: HttpUrl,
    val file: File,
    val headers: Map<String, String> = emptyMap(),
    val partCount: Int = 8,
    val minPartBytes: Long = KotlinSegmentPlanner.DEFAULT_MIN_SEGMENT_BYTES,
    val maxRetries: Int = 3,
    val retryDelayMillis: Long = 1_000L,
    /** Ranges left over from a previous session; empty means "start fresh". */
    val resumeStates: List<SegmentState> = emptyList(),
)

/**
 * Segmented HTTP downloader.
 *
 * Responsibilities kept here on purpose: HTTP transport (OkHttp gives us HTTP/2,
 * proxies, cookies and TLS) and disk I/O. Segment arithmetic, resume validity
 * and checksums are pure functions covered by their own tests and mirrored in
 * the Rust core.
 *
 * Cancelling the flow leaves the partial file on disk — that is what makes a
 * later resume possible. It never truncates on failure.
 */
class DownloadEngine(
    private val client: OkHttpClient = OkHttpClient(),
    private val planner: SegmentPlanner = KotlinSegmentPlanner(),
) {

    /** Reads status, validators and length without downloading the body. */
    suspend fun probe(url: HttpUrl, headers: Map<String, String> = emptyMap()): RemoteMetadata {
        val request = requestBuilder(url, headers)
            .header("Range", "bytes=0-0")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw DownloadHttpException(response.code, response.message)
            }
            val partial = response.code == 206
            val total = if (partial) {
                parseContentRangeTotal(response.header("Content-Range"))
            } else {
                response.header("Content-Length")?.toLongOrNull() ?: -1L
            }
            return RemoteMetadata(
                totalBytes = total,
                supportsRanges = partial && total > 0L,
                etag = response.header("ETag"),
                lastModified = response.header("Last-Modified"),
                contentType = response.header("Content-Type"),
                suggestedFileName = parseFileName(response.header("Content-Disposition"))
                    ?: url.encodedPath.substringAfterLast('/', "").ifBlank { null },
            )
        }
    }

    fun download(spec: DownloadSpec): Flow<DownloadEvent> = flow {
        val metadata = probe(spec.url, spec.headers)
        emit(DownloadEvent.Metadata(metadata))

        if (metadata.totalBytes <= 0L) {
            emit(DownloadEvent.Failed("Server did not report a file size"))
            return@flow
        }

        spec.file.parentFile?.mkdirs()

        val states = resolveStates(spec, metadata)
        val alreadyOnDisk = states.sumOf { it.downloaded }
        val emitter: suspend (DownloadEvent) -> Unit = { event -> emit(event) }

        try {
            RandomAccessFile(spec.file, "rw").use { raf ->
                // Sparse pre-allocation: the final size is known, holes are cheap.
                raf.setLength(metadata.totalBytes)
                val channel = raf.channel
                downloadInto(spec, metadata, states, channel, alreadyOnDisk, emitter)
            }
        } catch (io: java.io.IOException) {
            // The partial file is kept on purpose so the next run can resume it.
            emit(DownloadEvent.Failed(io.message ?: "Network error", io))
            return@flow
        }

        val size = spec.file.length()
        if (size != metadata.totalBytes) {
            emit(DownloadEvent.Failed("Size mismatch: expected ${metadata.totalBytes}, got $size"))
            return@flow
        }
        emit(DownloadEvent.Completed)
    }.flowOn(Dispatchers.IO)

    private fun resolveStates(spec: DownloadSpec, metadata: RemoteMetadata): List<SegmentState> {
        if (spec.resumeStates.isNotEmpty()) return spec.resumeStates
        if (!metadata.isResumable) {
            return listOf(SegmentState(start = 0L, end = metadata.totalBytes - 1))
        }
        return planner.plan(metadata.totalBytes, spec.partCount, spec.minPartBytes)
            .map { SegmentState(start = it.start, end = it.end) }
    }

    private suspend fun downloadInto(
        spec: DownloadSpec,
        metadata: RemoteMetadata,
        states: List<SegmentState>,
        channel: FileChannel,
        alreadyOnDisk: Long,
        emit: suspend (DownloadEvent) -> Unit,
    ) = coroutineScope {
        val downloaded = AtomicLong(alreadyOnDisk)
        val activeParts = AtomicLong(states.count { it.downloaded < it.end - it.start + 1 }.toLong())

        val ticker = launch(Dispatchers.IO) {
            var previousBytes = downloaded.get()
            var previousAt = System.currentTimeMillis()
            while (isActive) {
                delay(PROGRESS_INTERVAL_MS)
                val now = System.currentTimeMillis()
                val current = downloaded.get()
                val elapsed = (now - previousAt).coerceAtLeast(1L)
                val speed = ((current - previousBytes) * 1000L) / elapsed
                previousBytes = current
                previousAt = now
                emit(
                    DownloadEvent.Progress(
                        downloadedBytes = current,
                        totalBytes = metadata.totalBytes,
                        bytesPerSecond = speed.coerceAtLeast(0L),
                        activeParts = activeParts.get().toInt(),
                    ),
                )
            }
        }

        try {
            states.forEach { state ->
                val remaining = RangeSegment(0, state.start, state.end).remaining(state.downloaded)
                    ?: return@forEach
                launch(Dispatchers.IO) {
                    fetchRange(spec, remaining, channel, downloaded, spec.maxRetries)
                    activeParts.decrementAndGet()
                }
            }
            // Emit a final progress sample so listeners see 100%.
            emit(
                DownloadEvent.Progress(
                    downloadedBytes = downloaded.get(),
                    totalBytes = metadata.totalBytes,
                    bytesPerSecond = 0L,
                    activeParts = 0,
                ),
            )
        } finally {
            ticker.cancel()
        }
    }

    private suspend fun fetchRange(
        spec: DownloadSpec,
        range: RangeSegment,
        channel: FileChannel,
        downloaded: AtomicLong,
        retriesLeft: Int,
    ) {
        var attempt = 0
        while (true) {
            try {
                val request = requestBuilder(spec.url, spec.headers)
                    .header("Range", range.rangeHeader())
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.code != 206 && response.code != 200) {
                        throw DownloadHttpException(response.code, response.message)
                    }
                    copyBody(response, channel, range, downloaded)
                }
                return
            } catch (io: java.io.IOException) {
                if (attempt >= retriesLeft) throw io
                delay(spec.retryDelayMillis * (1L shl attempt))
                attempt++
            }
        }
    }

    private fun copyBody(
        response: Response,
        channel: FileChannel,
        range: RangeSegment,
        downloaded: AtomicLong,
    ) {
        val source = response.body.source()
        var position = range.start
        val buffer = ByteArray(COPY_BUFFER_BYTES)
        while (true) {
            val read = source.read(buffer)
            if (read == -1) break
            var offset = 0
            while (offset < read) {
                val written = channel.write(ByteBuffer.wrap(buffer, offset, read - offset), position + offset)
                // A file channel returning 0 means the write made no progress; treat it
                // as an I/O failure so the range is retried instead of silently truncated.
                if (written <= 0) throw java.io.IOException("Disk write made no progress at $position")
                offset += written
            }
            position += read
            downloaded.addAndGet(read.toLong())
        }
    }

    private fun requestBuilder(url: HttpUrl, headers: Map<String, String>): Request.Builder {
        val builder = Request.Builder().url(url)
        headers.forEach { (name, value) -> builder.header(name, value) }
        return builder
    }

    companion object {
        private const val COPY_BUFFER_BYTES = 64 * 1024
        private const val PROGRESS_INTERVAL_MS = 200L

        /** `bytes 0-99/1234` -> 1234. */
        internal fun parseContentRangeTotal(header: String?): Long {
            val total = header?.substringAfterLast('/')?.trim() ?: return -1L
            return total.toLongOrNull() ?: -1L
        }

        /** Extracts `filename` or `filename*` from a Content-Disposition header. */
        internal fun parseFileName(header: String?): String? {
            if (header.isNullOrBlank()) return null
            val extended = Regex("""filename\*\s*=\s*[^']*'[^']*'([^;]+)""", RegexOption.IGNORE_CASE)
                .find(header)?.groupValues?.getOrNull(1)
            val plain = extended ?: Regex("""filename\s*=\s*"([^"]+)"""", RegexOption.IGNORE_CASE)
                .find(header)?.groupValues?.getOrNull(1)
                ?: Regex("""filename\s*=\s*([^;]+)""", RegexOption.IGNORE_CASE)
                    .find(header)?.groupValues?.getOrNull(1)?.trim()
            return plain?.trim('"')?.takeIf { it.isNotBlank() }
        }
    }
}

class DownloadHttpException(
    val code: Int,
    message: String,
) : java.io.IOException("HTTP $code $message")
