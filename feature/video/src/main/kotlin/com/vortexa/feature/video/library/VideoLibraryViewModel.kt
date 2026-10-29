package com.vortexa.feature.video.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VideoLibraryState(
    val items: List<LibraryItem> = emptyList(),
    val loading: Boolean = true,
)

@HiltViewModel
class VideoLibraryViewModel @Inject constructor(
    private val library: MediaLibrary,
) : ViewModel() {

    private val _state = MutableStateFlow(VideoLibraryState())
    val state: StateFlow<VideoLibraryState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            val items = runCatching { library.load() }.getOrDefault(emptyList())
            _state.value = VideoLibraryState(items = items, loading = false)
        }
    }
}
