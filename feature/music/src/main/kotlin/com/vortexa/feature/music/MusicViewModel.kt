package com.vortexa.feature.music

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import com.vortexa.core.media.ExtractorFailure
import com.vortexa.core.media.MediaTrack
import com.vortexa.core.online.OnlineSourcesRepository
import com.vortexa.core.playback.PlaybackService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

data class MusicUiState(
    val sourcesEnabled: Boolean = false,
    val query: String = "",
    val searching: Boolean = false,
    val results: List<MediaTrack> = emptyList(),
    val failures: List<ExtractorFailure> = emptyList(),
    val message: String? = null,
    val nowPlayingTitle: String? = null,
    val nowPlayingArtist: String? = null,
    val isPlaying: Boolean = false,
    val sleepTimerMinutes: Int? = null,
) {
    val canSearch: Boolean get() = sourcesEnabled && !searching
}

/**
 * The Music tab's online side.
 *
 * Search and resolve go through [OnlineSourcesRepository]; playback goes
 * through the shared [PlaybackService] session, which is what gives plugin
 * streams the same background playback, notification and headset controls as
 * local files. A failing source becomes a message, never a crash.
 */
@HiltViewModel
class MusicViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val online: OnlineSourcesRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MusicUiState())
    val state: StateFlow<MusicUiState> = _state.asStateFlow()

    private var controller: MediaController? = null
    private var playbackTicker: Job? = null

    init {
        viewModelScope.launch {
            online.ensureLoaded()
            online.state.collect { sources ->
                _state.update { it.copy(sourcesEnabled = sources.enabled) }
            }
        }
    }

    fun onQueryChange(value: String) {
        _state.update { it.copy(query = value) }
    }

    fun search() {
        val query = _state.value.query.trim()
        if (query.isEmpty() || !_state.value.sourcesEnabled) return

        viewModelScope.launch {
            _state.update { it.copy(searching = true, message = null) }
            val outcome = online.search(query)
            _state.update {
                it.copy(
                    searching = false,
                    results = outcome.items,
                    failures = outcome.failures,
                    message = if (outcome.isEmpty && outcome.failures.isEmpty()) {
                        "Nothing matched \"$query\""
                    } else {
                        null
                    },
                )
            }
        }
    }

    fun play(track: MediaTrack) {
        viewModelScope.launch {
            _state.update { it.copy(message = null) }
            val resolved = online.resolve(track)
            val link = resolved.getOrElse { error ->
                _state.update {
                    it.copy(message = error.userText())
                }
                return@launch
            }

            val player = connect()
            if (player == null) {
                _state.update { it.copy(message = "Could not start the player") }
                return@launch
            }

            player.setMediaItem(
                MediaItem.Builder()
                    .setUri(link.url)
                    .setMimeType(link.mimeType)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(track.title)
                            .setArtist(track.artist)
                            .setAlbumTitle(track.album)
                            .build(),
                    )
                    .build(),
            )
            player.prepare()
            player.play()

            _state.update {
                it.copy(
                    nowPlayingTitle = track.title,
                    nowPlayingArtist = track.artist,
                )
            }
            startTicker(player)
        }
    }

    fun pauseOrResume() {
        val player = controller ?: return
        if (player.isPlaying) player.pause() else player.play()
        _state.update { it.copy(isPlaying = player.isPlaying) }
    }

    fun setSleepTimer(minutes: Int?) {
        if (minutes == null) {
            PlaybackService.cancelSleepTimer(context)
        } else {
            PlaybackService.sleepTimer(context, minutes)
        }
        _state.update { it.copy(sleepTimerMinutes = minutes) }
    }

    private fun startTicker(player: MediaController) {
        playbackTicker?.cancel()
        playbackTicker = viewModelScope.launch {
            while (isActive) {
                _state.update { it.copy(isPlaying = player.isPlaying) }
                delay(700)
            }
        }
    }

    private suspend fun connect(): MediaController? {
        controller?.let { return it }
        val mainExecutor = java.util.concurrent.Executor { command ->
            android.os.Handler(context.mainLooper).post(command)
        }
        val connected = runCatching {
            suspendCancellableCoroutine<MediaController> { continuation ->
                val future = PlaybackService.connect(context)
                future.addListener(
                    {
                        runCatching { future.get() }
                            .onSuccess { if (continuation.isActive) continuation.resumeWith(Result.success(it)) }
                            .onFailure { if (continuation.isActive) continuation.resumeWith(Result.failure(it)) }
                    },
                    mainExecutor,
                )
            }
        }.getOrNull()
        controller = connected
        return connected
    }

    private fun Throwable.userText(): String = when (this) {
        is com.vortexa.core.media.ExtractionException -> userMessage
        is java.io.IOException -> "Network error while resolving the stream"
        else -> message ?: "This source could not resolve the track"
    }

    override fun onCleared() {
        playbackTicker?.cancel()
        controller?.release()
        controller = null
        super.onCleared()
    }
}
