package com.vortexa.app.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vortexa.core.database.dao.DownloadDao
import com.vortexa.feature.downloader.DownloadManager
import com.vortexa.feature.music.library.LocalMusicLibrary
import com.vortexa.feature.music.library.LocalTrack
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val hits: List<SearchHit> = emptyList(),
    val ready: Boolean = false,
) {
    val tooShort: Boolean get() = query.isNotBlank() && query.trim().length < SearchIndex.MIN_QUERY_LENGTH
    val isEmpty: Boolean get() = ready && query.trim().length >= SearchIndex.MIN_QUERY_LENGTH && hits.isEmpty()
}

/**
 * One box over what the app already has on disk: finished downloads and the
 * local music library. The vault is deliberately absent — its names are only
 * readable after it is unlocked, and search must not be a way around that.
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val dao: DownloadDao,
    private val library: LocalMusicLibrary,
    private val downloads: DownloadManager,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val tracks = MutableStateFlow<List<LocalTrack>>(emptyList())

    val state: StateFlow<SearchUiState> =
        combine(query, dao.observeAll(), tracks) { text, rows, music ->
            SearchUiState(
                query = text,
                hits = SearchIndex.search(text, rows, music),
                ready = true,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SearchUiState(),
        )

    init {
        viewModelScope.launch { tracks.value = library.load() }
    }

    fun onQueryChange(value: String) {
        query.value = value
    }

    /** Turns a hit into a playable path; null when the file is no longer there. */
    fun pathFor(hit: SearchHit): String? {
        val path = hit.path ?: hit.fileName?.let { downloads.fileFor(it).absolutePath } ?: return null
        return path.takeIf { java.io.File(it).isFile }
    }
}
