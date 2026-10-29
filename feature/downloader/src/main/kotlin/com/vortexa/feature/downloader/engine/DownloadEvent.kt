package com.vortexa.feature.downloader.engine

sealed interface DownloadEvent {
    data class Metadata(val metadata: RemoteMetadata) : DownloadEvent

    data class Progress(
        val downloadedBytes: Long,
        val totalBytes: Long,
        val bytesPerSecond: Long,
        val activeParts: Int,
        /** Snapshot of every range, used to persist crash-safe resume state. */
        val parts: List<SegmentState> = emptyList(),
    ) : DownloadEvent {
        val fraction: Float
            get() = if (totalBytes <= 0L) 0f
            else (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
    }

    data object Completed : DownloadEvent

    data class Failed(
        val reason: String,
        val cause: Throwable? = null,
    ) : DownloadEvent
}
