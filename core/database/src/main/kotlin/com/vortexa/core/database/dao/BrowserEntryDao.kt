package com.vortexa.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.vortexa.core.database.entity.BrowserEntryEntity
import com.vortexa.core.database.entity.BrowserEntryKind
import kotlinx.coroutines.flow.Flow

@Dao
interface BrowserEntryDao {

    @Query("SELECT * FROM browser_entries WHERE kind = :kind ORDER BY visited_at DESC")
    fun observeByKind(kind: BrowserEntryKind): Flow<List<BrowserEntryEntity>>

    @Query("SELECT * FROM browser_entries WHERE kind = :kind AND url = :url LIMIT 1")
    suspend fun findByKindAndUrl(kind: BrowserEntryKind, url: String): BrowserEntryEntity?

    @Insert
    suspend fun insert(entry: BrowserEntryEntity): Long

    @Query("UPDATE browser_entries SET title = :title, visited_at = :visitedAt WHERE id = :id")
    suspend fun touch(id: Long, title: String, visitedAt: Long)

    @Query("DELETE FROM browser_entries WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM browser_entries WHERE kind = :kind")
    suspend fun clearKind(kind: BrowserEntryKind)
}
