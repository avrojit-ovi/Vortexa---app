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
import com.vortexa.core.online.community.DislikeStats
import com.vortexa.core.online.community.ReturnYouTubeDislikeClient
import com.vortexa.core.online.community.SponsorBlockClient
import com.vortexa.core.online.community.SponsorSegment
import com.vortexa.core.playback.EqualizerProfile
import com.vortexa.core.playback.PlaybackService
import com.vortexa.feature.music.library.LocalMusicLibrary
import com.vortexa.feature.music.library.LocalTrack
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
    val community: DislikeStats? = null,
    val sponsorSegments: List<SponsorSegment> = emptyList(),
    val positionMs: Long = 0L,
    val sponsorsSkipped: Int = 0,
    val localTracks: List<LocalTrack> = emptyList(),
    val localLoading: Boolean = true,
) {
    /** The sponsor segment the playhead is currently inside, if any. */
    val activeSponsorSegment: SponsorSegment?
        get() {
            val seconds = positionMs / 1000.0
            return sponsorSegments.firstOrNull { seconds >= it.startSeconds && seconds < it.endSeconds }
        }

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
    private val sponsorBlock: SponsorBlockClient,
    private val dislikes: ReturnYouTubeDislikeClient,
    private val localLibrary: LocalMusicLibrary,
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
        refreshLocal()
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

    /** Rescans the download folder for audio; cheap and permission-free. */
    fun refreshLocal() {
        viewModelScope.launch {
            val tracks = runCatching { localLibrary.load() }.getOrDefault(emptyList())
            _state.update { it.copy(localTracks = tracks, localLoading = false) }
        }
    }

    /** Local files need no resolution: the whole album becomes the queue at once. */
    fun playLocal(track: LocalTrack) {
        val tracks = _state.value.localTracks
        val index = tracks.indexOfFirst { it.path == track.path }.coerceAtLeast(0)
        viewModelScope.launch {
            val player = connect() ?: return@launch
            val items = tracks.map { local ->
                MediaItem.Builder()
                    .setUri(android.net.Uri.fromFile(java.io.File(local.path)))
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(local.title)
                            .setArtist(local.artist)
                            .setAlbumTitle(local.album)
                            .build(),
                    )
                    .build()
            }
            player.setMediaItems(items, index, 0L)
            player.prepare()
            player.play()
            _state.update {
                it.copy(
                    nowPlayingTitle = track.title,
                    nowPlayingArtist = track.subtitle,
                    community = null,
                    sponsorSegments = emptyList(),
                )
            }
            startTicker(player)
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
                    community = null,
                    sponsorSegments = emptyList(),
                )
            }
            startTicker(player)
            loadCommunityData(track)
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

    /** Layer (c): optional community data, never blocking playback. */
    private fun loadCommunityData(track: MediaTrack) {
        val videoId = YouTubeIds.videoIdOf(track.id) ?: return
        viewModelScope.launch {
            val votes = dislikes.votes(videoId)
            _state.update { it.copy(community = votes) }
        }
        viewModelScope.launch {
            val segments = sponsorBlock.segments(videoId)
            _state.update { it.copy(sponsorSegments = segments) }
        }
    }

    /** Jumps past the sponsor segment the playhead is inside. */
    fun skipActiveSponsor() {
        val segment = _state.value.activeSponsorSegment ?: return
        val player = controller ?: return
        player.seekTo((segment.endSeconds * 1000).toLong())
        _state.update {
            it.copy(
                positionMs = (segment.endSeconds * 1000).toLong(),
                sponsorsSkipped = it.sponsorsSkipped + 1,
            )
        }
    }

    private fun startTicker(player: MediaController) {
        playbackTicker?.cancel()
        playbackTicker = viewModelScope.launch {
            while (isActive) {
                _state.update {
                    it.copy(isPlaying = player.isPlaying, positionMs = player.currentPosition)
                }
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

    private object YouTubeIds {
        fun videoIdOf(url: String): String? {
            val candidate = when {
                url.contains("youtu.be/") -> url.substringAfter("youtu.be/").substringBefore('?')
                url.contains("v=") -> url.substringAfter("v=").substringBefore('&')
                url.contains("/shorts/") -> url.substringAfter("/shorts/").substringBefore('?')
                url.contains("/embed/") -> url.substringAfter("/embed/").substringBefore('?')
                else -> null
            }
            return candidate?.takeIf { it.length in 5..20 }
        }
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
