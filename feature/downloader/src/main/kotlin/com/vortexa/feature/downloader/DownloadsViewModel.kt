package com.vortexa.feature.downloader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vortexa.core.database.entity.DownloadEntity
import com.vortexa.core.database.entity.DownloadStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrl

data class DownloadsUiState(
    val isLoading: Boolean = true,
    val downloads: List<DownloadEntity> = emptyList(),
    val speeds: Map<Long, Long> = emptyMap(),
) {
    val isEmpty: Boolean get() = !isLoading && downloads.isEmpty()
    val activeCount: Int get() = downloads.count { it.status == DownloadStatus.RUNNING }
}

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val manager: DownloadManager,
) : ViewModel() {

    val uiState: StateFlow<DownloadsUiState> =
        combine(manager.observeAll(), manager.speeds) { downloads, speeds ->
            DownloadsUiState(isLoading = false, downloads = downloads, speeds = speeds)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DownloadsUiState(),
        )

    /** Returns false when the URL is not usable, so the dialog can stay open. */
    fun addDownload(url: String): Boolean {
        val parsed = runCatching { url.trim().toHttpUrl() }.getOrNull() ?: return false
        viewModelScope.launch { manager.enqueue(parsed.toString()) }
        return true
    }

    fun pause(id: Long) = manager.pause(id)

    fun resume(id: Long) = manager.start(id)

    fun cancel(id: Long) = manager.cancel(id)

    fun remove(id: Long) {
        viewModelScope.launch { manager.remove(id) }
    }
}
