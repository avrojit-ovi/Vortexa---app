package com.vortexa.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per download. Fields are intentionally engine-agnostic so the Rust
 * download core (phase 2) can own them without a schema rewrite.
 */
@Entity(
    tableName = "downloads",
    indices = [
        Index("status"),
        Index("created_at"),
        Index(value = ["url"], unique = false),
    ],
)
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "url")
    val url: String,

    @ColumnInfo(name = "file_name")
    val fileName: String,

    @ColumnInfo(name = "mime_type")
    val mimeType: String? = null,

    @ColumnInfo(name = "directory")
    val directory: String? = null,

    @ColumnInfo(name = "total_bytes")
    val totalBytes: Long = UNKNOWN_SIZE,

    @ColumnInfo(name = "downloaded_bytes")
    val downloadedBytes: Long = 0L,

    /** Number of byte-range segments. 1 means a plain single-connection download. */
    @ColumnInfo(name = "segment_count")
    val segmentCount: Int = 1,

    @ColumnInfo(name = "status")
    val status: DownloadStatus = DownloadStatus.QUEUED,

    /** Strong validator; required for a safe resume. */
    @ColumnInfo(name = "etag")
    val etag: String? = null,

    @ColumnInfo(name = "last_modified")
    val lastModified: String? = null,

    @ColumnInfo(name = "error_message")
    val errorMessage: String? = null,

    @ColumnInfo(name = "retry_count")
    val retryCount: Int = 0,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "completed_at")
    val completedAt: Long? = null,
) {
    val progress: Float
        get() = when {
            totalBytes <= 0L -> 0f
            else -> (downloadedBytes.toDouble() / totalBytes.toDouble()).toFloat().coerceIn(0f, 1f)
        }

    val isActive: Boolean
        get() = status == DownloadStatus.RUNNING || status == DownloadStatus.QUEUED

    companion object {
        const val UNKNOWN_SIZE = -1L
    }
}
