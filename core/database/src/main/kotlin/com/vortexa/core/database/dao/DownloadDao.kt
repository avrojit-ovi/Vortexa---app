package com.vortexa.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.vortexa.core.database.entity.DownloadEntity
import com.vortexa.core.database.entity.DownloadStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    @Query("SELECT * FROM downloads ORDER BY created_at DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status = :status ORDER BY created_at DESC")
    fun observeByStatus(status: DownloadStatus): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id")
    fun observeById(id: Long): Flow<DownloadEntity?>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun findById(id: Long): DownloadEntity?

    @Upsert
    suspend fun upsert(download: DownloadEntity): Long

    @Query("UPDATE downloads SET status = :status, updated_at = :timestamp WHERE id = :id")
    suspend fun updateStatus(id: Long, status: DownloadStatus, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE downloads SET downloaded_bytes = :bytes, updated_at = :timestamp WHERE id = :id")
    suspend fun updateProgress(id: Long, bytes: Long, timestamp: Long = System.currentTimeMillis())

    @Query(
        "UPDATE downloads SET downloaded_bytes = :bytes, total_bytes = :total, segments = :segments, " +
            "updated_at = :timestamp WHERE id = :id",
    )
    suspend fun updateProgressDetail(
        id: Long,
        bytes: Long,
        total: Long,
        segments: String?,
        timestamp: Long = System.currentTimeMillis(),
    )

    @Query(
        "UPDATE downloads SET file_name = :fileName, mime_type = :mimeType, total_bytes = :total, " +
            "etag = :etag, last_modified = :lastModified, updated_at = :timestamp WHERE id = :id",
    )
    suspend fun updateMetadata(
        id: Long,
        fileName: String,
        mimeType: String?,
        total: Long,
        etag: String?,
        lastModified: String?,
        timestamp: Long = System.currentTimeMillis(),
    )

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM downloads WHERE status IN ('COMPLETED', 'CANCELED', 'FAILED')")
    suspend fun clearFinished()

    @Query("SELECT COUNT(*) FROM downloads")
    fun observeCount(): Flow<Int>
}
