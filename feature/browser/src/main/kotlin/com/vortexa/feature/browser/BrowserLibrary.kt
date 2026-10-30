package com.vortexa.feature.browser

import com.vortexa.core.database.dao.BrowserEntryDao
import com.vortexa.core.database.entity.BrowserEntryEntity
import com.vortexa.core.database.entity.BrowserEntryKind
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Which visits are worth remembering. Pure, so the rules are covered by tests. */
object BrowserHistoryPolicy {

    fun shouldRecord(url: String?, incognito: Boolean): Boolean {
        if (incognito || url.isNullOrBlank()) return false
        return url.startsWith("http://", ignoreCase = true) ||
            url.startsWith("https://", ignoreCase = true)
    }
}

/** Bookmarks and history, one row per URL for each kind. */
@Singleton
class BrowserLibrary @Inject constructor(
    private val dao: BrowserEntryDao,
) {

    fun observeBookmarks(): Flow<List<BrowserEntryEntity>> =
        dao.observeByKind(BrowserEntryKind.BOOKMARK)

    fun observeHistory(): Flow<List<BrowserEntryEntity>> =
        dao.observeByKind(BrowserEntryKind.HISTORY)

    /** Whether [url] is currently bookmarked. */
    fun observeIsBookmarked(url: Flow<String>): Flow<Boolean> =
        url.map { dao.findByKindAndUrl(BrowserEntryKind.BOOKMARK, it) != null }

    /** Toggles the bookmark for [url]; returns true when it is now bookmarked. */
    suspend fun toggleBookmark(url: String, title: String): Boolean {
        val existing = dao.findByKindAndUrl(BrowserEntryKind.BOOKMARK, url)
        if (existing != null) {
            dao.deleteById(existing.id)
            return false
        }
        dao.insert(BrowserEntryEntity(kind = BrowserEntryKind.BOOKMARK, url = url, title = title))
        return true
    }

    suspend fun removeBookmark(id: Long) = dao.deleteById(id)

    /** Records a visit; a revisit refreshes the existing row rather than stacking one. */
    suspend fun recordVisit(url: String, title: String, incognito: Boolean) {
        if (!BrowserHistoryPolicy.shouldRecord(url, incognito)) return
        val existing = dao.findByKindAndUrl(BrowserEntryKind.HISTORY, url)
        val now = System.currentTimeMillis()
        if (existing != null) {
            dao.touch(existing.id, title, now)
        } else {
            dao.insert(
                BrowserEntryEntity(
                    kind = BrowserEntryKind.HISTORY,
                    url = url,
                    title = title,
                    visitedAt = now,
                ),
            )
        }
    }

    suspend fun removeHistory(id: Long) = dao.deleteById(id)

    suspend fun clearHistory() = dao.clearKind(BrowserEntryKind.HISTORY)
}
