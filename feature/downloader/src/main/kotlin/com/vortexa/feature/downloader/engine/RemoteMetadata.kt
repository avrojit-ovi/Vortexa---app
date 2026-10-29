package com.vortexa.feature.downloader.engine

/** What the server told us about the resource. */
data class RemoteMetadata(
    val totalBytes: Long,
    val supportsRanges: Boolean,
    val etag: String?,
    val lastModified: String?,
    val contentType: String?,
    val suggestedFileName: String?,
) {
    val isResumable: Boolean get() = supportsRanges && totalBytes > 0L
}
