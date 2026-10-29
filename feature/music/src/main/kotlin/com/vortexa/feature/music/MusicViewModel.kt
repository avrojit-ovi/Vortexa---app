package com.vortexa.feature.music

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.common.Player
import com.vortexa.core.common.preferences.SettingsRepository
import com.vortexa.core.media.ExtractorFailure
import com.vortexa.core.media.MediaTrack
import com.vortexa.core.online.OnlineSourcesRepository
import com.vortexa.core.playback.EqualizerProfile
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
    val equalizerEnabled: Boolean = false,
    val equalizerBands: List<Int> = EqualizerProfile.flat(),
    /** Set when extraction failed but an official web player exists. */
    val fallbackUrl: String? = null,
    val fallbackTitle: String? = null,
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
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MusicUiState())
    val state: StateFlow<MusicUiState> = _state.asStateFlow()

    private var controller: MediaController? = null
    private var playbackTicker: Job? = null

    /** The search results become the queue; only the current item is resolved. */
    private var queue: List<MediaTrack> = emptyList()
    private var queueIndex: Int = -1
    private var advancing = false

    init {
        viewModelScope.launch {
            settings.preferences.collect { preferences ->
                _state.update {
                    it.copy(
                        equalizerEnabled = preferences.equalizerEnabled,
                        equalizerBands = EqualizerProfile.decode(preferences.equalizerBands),
                    )
                }
            }
        }
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

    fun play(track: MediaTrack, results: List<MediaTrack> = emptyList()) {
        val source = if (results.isEmpty()) listOf(track) else results
        queue = source
        queueIndex = source.indexOfFirst { it.extractorId == track.extractorId && it.id == track.id }
            .takeIf { it >= 0 } ?: 0
        playAt(queueIndex)
    }

    fun setEqualizer(enabled: Boolean, bands: List<Int>) {
        val safe = EqualizerProfile.clampBands(bands)
        _state.update { it.copy(equalizerEnabled = enabled, equalizerBands = safe) }
        viewModelScope.launch { settings.setEqualizer(enabled, EqualizerProfile.encode(safe)) }
    }

    private fun playAt(index: Int) {
        val track = queue.getOrNull(index) ?: return
        queueIndex = index
        viewModelScope.launch {
            _state.update { it.copy(message = null) }
            val resolved = online.resolve(track)
            val link = resolved.getOrElse { error ->
                _state.update {
                    it.copy(
                        message = error.userText(),
                        fallbackUrl = track.fallbackUrl,
                        fallbackTitle = track.title,
                    )
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

    fun consumeFallback() {
        _state.update { it.copy(fallbackUrl = null, fallbackTitle = null) }
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
                if (player.playbackState == Player.STATE_ENDED) playNext()
                delay(700)
            }
        }
    }

    /** Lazily resolves the next queued item, so a dead link skips instead of stopping. */
    private fun playNext() {
        if (advancing) return
        val next = queueIndex + 1
        if (next !in queue.indices) return
        advancing = true
        playAt(next)
        advancing = false
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
