package com.vortexa.feature.downloader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vortexa.core.database.entity.DownloadEntity
import com.vortexa.core.database.entity.DownloadStatus
import com.vortexa.feature.downloader.engine.DownloadCategory
import com.vortexa.feature.downloader.engine.LinkBatch
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DownloadsUiState(
    val isLoading: Boolean = true,
    val downloads: List<DownloadEntity> = emptyList(),
    val speeds: Map<Long, Long> = emptyMap(),
    val categories: List<DownloadCategory> = emptyList(),
    val selectedCategory: DownloadCategory? = null,
    val totalCount: Int = 0,
    /** Every queued URL, filtered rows included, so the clipboard offer can skip duplicates. */
    val existingUrls: Set<String> = emptySet(),
) {
    val isEmpty: Boolean get() = !isLoading && downloads.isEmpty() && totalCount == 0
    val isFilterEmpty: Boolean get() = !isLoading && downloads.isEmpty() && totalCount > 0
    val activeCount: Int get() = downloads.count { it.status == DownloadStatus.RUNNING }
}

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val manager: DownloadManager,
) : ViewModel() {

    private val selectedCategory = MutableStateFlow<DownloadCategory?>(null)

    val uiState: StateFlow<DownloadsUiState> =
        combine(manager.observeAll(), manager.speeds, selectedCategory) { downloads, speeds, selected ->
            val categorized = downloads.map { it to DownloadCategory.classify(it.fileName, it.mimeType) }
            // Ordered by the enum declaration, so the chips do not reshuffle as rows arrive.
            val categories = categorized.map { it.second }.distinct().sortedBy { it.ordinal }
            // A filter whose category lost its last download is dropped: it would otherwise
            // hide the queue behind a chip the chips row can no longer show.
            val active = selected?.takeIf { it in categories }
            if (selected != null && active == null) selectedCategory.value = null
            DownloadsUiState(
                isLoading = false,
                downloads = categorized.filter { active == null || it.second == active }.map { it.first },
                speeds = speeds,
                categories = categories,
                selectedCategory = active,
                totalCount = downloads.size,
                existingUrls = downloads.map { it.url }.toSet(),
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DownloadsUiState(),
        )

    /**
     * Queues every usable link in [text] and returns how many were accepted, so the
     * dialog can stay open (and say why) when the paste contained nothing usable.
     */
    fun addDownloads(text: String): Int {
        val links = LinkBatch.parse(text)
        if (links.isEmpty()) return 0
        viewModelScope.launch { links.forEach { manager.enqueue(it) } }
        return links.size
    }

    fun selectCategory(category: DownloadCategory?) {
        selectedCategory.value = category
    }

    fun pause(id: Long) = manager.pause(id)

    fun resume(id: Long) = manager.start(id)

    fun cancel(id: Long) = manager.cancel(id)

    fun remove(id: Long) {
        viewModelScope.launch { manager.remove(id) }
    }
}
