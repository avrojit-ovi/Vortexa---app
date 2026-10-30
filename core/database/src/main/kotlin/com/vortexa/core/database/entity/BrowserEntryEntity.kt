package com.vortexa.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A bookmark or a history entry. Both are kept to one row per URL per kind:
 * revisiting a page refreshes its row instead of stacking duplicates.
 */
@Entity(
    tableName = "browser_entries",
    indices = [
        Index("kind"),
        Index("url"),
    ],
)
data class BrowserEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "kind")
    val kind: BrowserEntryKind,

    @ColumnInfo(name = "url")
    val url: String,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "visited_at")
    val visitedAt: Long = System.currentTimeMillis(),
)

enum class BrowserEntryKind { BOOKMARK, HISTORY }
