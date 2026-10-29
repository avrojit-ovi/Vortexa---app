package com.vortexa.feature.downloader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vortexa.core.database.dao.DownloadDao
import com.vortexa.core.database.entity.DownloadEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class DownloadsUiState(
    val isLoading: Boolean = true,
    val downloads: List<DownloadEntity> = emptyList(),
) {
    val isEmpty: Boolean get() = !isLoading && downloads.isEmpty()
}

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    downloadDao: DownloadDao,
) : ViewModel() {

    val uiState: StateFlow<DownloadsUiState> = downloadDao.observeAll()
        .map { downloads -> DownloadsUiState(isLoading = false, downloads = downloads) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DownloadsUiState(),
        )
}
