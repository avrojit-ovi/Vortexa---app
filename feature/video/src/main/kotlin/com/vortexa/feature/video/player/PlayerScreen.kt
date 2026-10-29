package com.vortexa.feature.video.player

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.graphics.Color as AndroidColor
import android.media.AudioManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import android.os.SystemClock
import android.view.View
import com.vortexa.core.common.preferences.DecoderMode
import com.vortexa.core.playback.PlaybackService
import com.vortexa.feature.video.R
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Full-screen player for a downloaded file.
 *
 * It talks to [PlaybackService] through a [MediaController] instead of owning
 * an ExoPlayer, so leaving the screen keeps playing and the notification keeps
 * working. Everything user-visible (position, speed, aspect) is read back from
 * the session, never duplicated in UI state that could drift.
 */
@OptIn(ExperimentalMaterial3Api::class)
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    path: String,
    title: String,
    onBack: () -> Unit,
    onEnterPip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val positions = remember(context) { PlaybackPositions(context) }
    val view = LocalView.current

    var controller by remember(path) { mutableStateOf<MediaController?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(true) }
    var isEnded by remember { mutableStateOf(false) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var scrubbing by remember { mutableStateOf<Float?>(null) }
    var speed by remember { mutableStateOf(SpeedPresets.DEFAULT) }
    var zoomed by remember { mutableStateOf(false) }
    var captionsEnabled by remember { mutableStateOf(false) }
    var subtitlesPresent by remember(path) { mutableStateOf(false) }
    var gestureHint by remember { mutableStateOf<String?>(null) }
    val activity = context as? Activity

    val playerViewModel: PlayerViewModel = hiltViewModel()
    val preferences by playerViewModel.preferences.collectAsStateWithLifecycle()

    val timeline = remember { SubtitleTimeline() }
    var subtitleLines by remember { mutableStateOf<List<String>>(emptyList()) }
    var subtitleDelay by remember { mutableFloatStateOf(0f) }
    var subtitleSize by remember { mutableStateOf(SubtitleSize.MEDIUM) }
    var subtitleBackground by remember { mutableStateOf(true) }
    var showPlaybackSettings by remember { mutableStateOf(false) }

    // Kids lock hides the controls behind a PIN; it is not a security boundary.
    var locked by remember { mutableStateOf(false) }
    var pinPrompt by remember { mutableStateOf<PinPrompt?>(null) }
    var pinError by remember { mutableStateOf<String?>(null) }

    val invalidPinText = stringResource(R.string.player_pin_invalid)
    val wrongPinText = stringResource(R.string.player_pin_mismatch)

    // Locked playback blocks the back gesture too — that is the point of it.
    BackHandler(enabled = true) {
        if (!locked) onBack()
    }

    DisposableEffect(context, path) {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            {
                runCatching { future.get() }.onSuccess { connected ->
                    val saved = positions.lastPosition(path)
                    val startAt = if (saved >= ResumePolicy.MIN_RESUME_MS) saved else 0L
                    val videoFile = File(path)
                    // Sidecar subtitles: movie.srt, movie.en.srt, movie.bn.ass ...
                    val subtitles = SubtitleFinder.match(
                        videoPath = path,
                        candidates = videoFile.parentFile?.list()?.toList().orEmpty(),
                    )
                    val subtitleConfigs = subtitles.map { subtitle ->
                        MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(File(subtitle.path)))
                            .setMimeType(subtitle.mimeType)
                            .setLanguage(subtitle.language)
                            .setLabel(subtitle.label)
                            .build()
                    }
                    connected.setMediaItem(
                        MediaItem.Builder()
                            .setUri(Uri.fromFile(videoFile))
                            .setSubtitleConfigurations(subtitleConfigs)
                            .setMediaMetadata(
                                androidx.media3.common.MediaMetadata.Builder()
                                    .setTitle(title)
                                    .build(),
                            )
                            .build(),
                        startAt,
                    )
                    subtitlesPresent = subtitleConfigs.isNotEmpty()
                    // Subtitles that exist are shown by default, as before; the
                    // CC button is there to turn them off.
                    captionsEnabled = subtitlesPresent
                    timeline.clear()
                    connected.addListener(object : Player.Listener {
                        override fun onCues(cueGroup: CueGroup) {
                            val lines = cueGroup.cues.flatMap { cue ->
                                cue.text?.toString()
                                    ?.split('\n')
                                    ?.map { it.trim() }
                                    ?.filter { it.isNotEmpty() }
                                    .orEmpty()
                            }
                            timeline.push(lines, SystemClock.elapsedRealtime())
                        }
                    })
                    connected.prepare()
                    connected.playWhenReady = true
                    controller = connected
                }
            },
            ContextCompat.getMainExecutor(context),
        )
        onDispose {
            controller?.let { connected ->
                positions.save(path, connected.currentPosition)
            }
            MediaController.releaseFuture(future)
        }
    }

    // Screen stays awake only while something is actually playing.
    DisposableEffect(view, isPlaying) {
        view.keepScreenOn = isPlaying
        onDispose { view.keepScreenOn = false }
    }

    LaunchedEffect(controller) {
        val player = controller ?: return@LaunchedEffect
        while (isActive) {
            durationMs = player.duration.coerceAtLeast(0L)
            if (scrubbing == null) positionMs = player.currentPosition.coerceAtLeast(0L)
            isPlaying = player.isPlaying
            isBuffering = player.playbackState == Player.STATE_BUFFERING
            isEnded = player.playbackState == Player.STATE_ENDED
            speed = player.playbackParameters.speed
            if (ResumePolicy.shouldPersist(positionMs, durationMs)) {
                positions.save(path, positionMs)
            }
            delay(if (player.isPlaying) 500L else 1_000L)
        }
    }

    // Cues arrive when they become active; this ticker applies the user's delay
    // and keeps the on-screen text in step with it.
    LaunchedEffect(subtitleDelay) {
        while (isActive) {
            subtitleLines = timeline.visible(
                nowMillis = SystemClock.elapsedRealtime(),
                delayMillis = (subtitleDelay * 1000f).toLong(),
            )
            delay(150L)
        }
    }

    val toggleCaptions: () -> Unit = {
        captionsEnabled = !captionsEnabled
        controller?.let { player ->
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !captionsEnabled)
                .build()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black),
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    setShutterBackgroundColor(AndroidColor.BLACK)
                    // Subtitles are drawn by Compose so they can be styled and
                    // delayed; the library's own renderer would double them up.
                    subtitleView?.visibility = View.GONE
                }
            },
            update = { playerView ->
                playerView.player = controller
                playerView.resizeMode = if (zoomed) {
                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                } else {
                    AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Gestures sit under the controls: empty areas respond, buttons still win.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(controller, durationMs, locked) {
                    if (locked) return@pointerInput
                    var axis = DragAxis.NONE
                    var dx = 0f
                    var dy = 0f
                    var basePosition = 0L
                    var baseLevel = 0f
                    var leftSide = true

                    detectDragGestures(
                        onDragStart = { offset ->
                            axis = DragAxis.NONE
                            dx = 0f
                            dy = 0f
                            basePosition = controller?.currentPosition ?: positionMs
                            leftSide = GestureMath.sideOf(offset.x, size.width)
                            baseLevel = if (leftSide) {
                                activity?.window?.attributes?.screenBrightness ?: -1f
                            } else {
                                val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                                val max = audio?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 0
                                if (max > 0) {
                                    audio!!.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
                                } else {
                                    0f
                                }
                            }
                        },
                        onDragEnd = {
                            if (axis == DragAxis.HORIZONTAL && durationMs > 0L) {
                                val target = GestureMath.seekTarget(
                                    basePosition,
                                    durationMs,
                                    dx / size.width,
                                )
                                controller?.seekTo(target)
                                positionMs = target
                            }
                            gestureHint = null
                        },
                        onDragCancel = { gestureHint = null },
                        onDrag = { change, drag ->
                            change.consume()
                            dx += drag.x
                            dy += drag.y
                            if (axis == DragAxis.NONE) axis = GestureMath.axis(dx, dy)

                            when (axis) {
                                DragAxis.HORIZONTAL -> {
                                    val target = GestureMath.seekTarget(
                                        basePosition,
                                        durationMs,
                                        dx / size.width,
                                    )
                                    gestureHint = PlaybackClock.format(target) +
                                        "  (" + GestureMath.seekDeltaLabel(target - basePosition) + ")"
                                }

                                DragAxis.VERTICAL -> {
                                    val fraction = -dy / size.height
                                    if (leftSide) {
                                        val level = GestureMath.brightnessAfter(baseLevel, fraction)
                                        activity?.window?.let { window ->
                                            val attributes = window.attributes
                                            attributes.screenBrightness = level
                                            window.attributes = attributes
                                        }
                                        gestureHint = "Brightness " + (level * 100).toInt() + "%"
                                    } else {
                                        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                                        val max = audio?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 0
                                        if (audio != null && max > 0) {
                                            val current = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                                            val target = GestureMath.volumeIndexAfter(current, max, fraction)
                                            audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
                                            gestureHint = "Volume " + (target * 100 / max) + "%"
                                        }
                                    }
                                }

                                DragAxis.NONE -> Unit
                            }
                        },
                    )
                },
        )

        gestureHint?.let { hint ->
            Text(
                text = hint,
                color = androidx.compose.ui.graphics.Color.White,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 56.dp)
                    .background(
                        color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(8.dp),
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }

        if (isBuffering && !isEnded) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(48.dp),
                color = androidx.compose.ui.graphics.Color.White,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.65f),
                        0.3f to androidx.compose.ui.graphics.Color.Transparent,
                        0.7f to androidx.compose.ui.graphics.Color.Transparent,
                        1f to androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.75f),
                    ),
                ),
        ) {
            PlayerTopBar(
                title = title,
                locked = locked,
                onBack = onBack,
                onEnterPip = onEnterPip,
                onToggleLock = {
                    if (KidsLock.isValidPin(preferences.kidsLockPin)) {
                        locked = true
                    } else {
                        pinError = null
                        pinPrompt = PinPrompt.SET
                    }
                },
            )
            Spacer(modifier = Modifier.weight(1f))
            PlayerTransport(
                locked = locked,
                positionMs = positionMs,
                durationMs = durationMs,
                scrubbing = scrubbing,
                isPlaying = isPlaying,
                isEnded = isEnded,
                speed = speed,
                zoomed = zoomed,
                onScrub = { scrubbing = it },
                onScrubFinished = { fraction ->
                    val target = (fraction * durationMs).toLong()
                    controller?.seekTo(target.coerceIn(0L, durationMs.coerceAtLeast(0L)))
                    positionMs = target
                    scrubbing = null
                },
                onTogglePlay = {
                    val player = controller
                    if (player == null) {
                        // Nothing connected yet; the session is still starting.
                    } else if (player.playbackState == Player.STATE_ENDED) {
                        positions.clear(path)
                        player.seekTo(0)
                        player.play()
                    } else if (player.isPlaying) {
                        player.pause()
                    } else {
                        player.play()
                    }
                },
                onSeekBy = { delta ->
                    controller?.let { player ->
                        val target = (player.currentPosition + delta)
                            .coerceIn(0L, durationMs.coerceAtLeast(0L))
                        player.seekTo(target)
                        positionMs = target
                    }
                },
                onToggleSpeed = {
                    val next = SpeedPresets.next(speed)
                    controller?.setPlaybackSpeed(next)
                    speed = next
                },
                onToggleZoom = { zoomed = !zoomed },
                captionsAvailable = subtitlesPresent,
                captionsEnabled = captionsEnabled,
                onToggleCaptions = toggleCaptions,
                onOpenSettings = { showPlaybackSettings = true },
            )
        }

        if (captionsEnabled && subtitleLines.isNotEmpty()) {
            SubtitleOverlay(
                lines = subtitleLines,
                size = subtitleSize,
                background = subtitleBackground,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        start = 24.dp,
                        end = 24.dp,
                        bottom = if (locked) 32.dp else 136.dp,
                    ),
            )
        }

        if (locked) {
            LockBadge(
                onClick = {
                    pinError = null
                    pinPrompt = PinPrompt.ENTER
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 48.dp),
            )
        }
    }

    if (showPlaybackSettings) {
        ModalBottomSheet(onDismissRequest = { showPlaybackSettings = false }) {
            PlaybackSettingsSheet(
                delay = subtitleDelay,
                size = subtitleSize,
                background = subtitleBackground,
                captionsAvailable = subtitlesPresent,
                captionsEnabled = captionsEnabled,
                softwareDecoding = preferences.decoderMode == DecoderMode.SOFTWARE,
                onDelay = { subtitleDelay = SubtitleDelay.step(subtitleDelay, it) },
                onSize = { subtitleSize = it },
                onBackground = { subtitleBackground = it },
                onToggleCaptions = toggleCaptions,
                onSoftwareDecoding = { enabled ->
                    playerViewModel.setDecoderMode(
                        if (enabled) DecoderMode.SOFTWARE else DecoderMode.AUTO,
                    )
                },
            )
        }
    }

    pinPrompt?.let { prompt ->
        PinDialog(
            prompt = prompt,
            error = pinError,
            onDismiss = {
                pinPrompt = null
                pinError = null
            },
            onSubmit = { pin ->
                when (prompt) {
                    PinPrompt.SET -> if (KidsLock.isValidPin(pin)) {
                        playerViewModel.setKidsLockPin(pin)
                        pinPrompt = null
                        pinError = null
                        locked = true
                    } else {
                        pinError = invalidPinText
                    }

                    PinPrompt.ENTER -> if (KidsLock.matches(preferences.kidsLockPin, pin)) {
                        locked = false
                        pinPrompt = null
                        pinError = null
                    } else {
                        pinError = wrongPinText
                    }
                }
            },
        )
    }
}

@Composable
private fun PlayerTopBar(
    title: String,
    locked: Boolean,
    onBack: () -> Unit,
    onEnterPip: () -> Unit,
    onToggleLock: () -> Unit,
) {
    if (locked) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_back),
                contentDescription = stringResource(R.string.player_back),
                tint = androidx.compose.ui.graphics.Color.White,
            )
        }
        Text(
            text = title,
            color = androidx.compose.ui.graphics.Color.White,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onToggleLock) {
            Icon(
                painter = painterResource(R.drawable.ic_lock),
                contentDescription = stringResource(R.string.player_lock),
                tint = androidx.compose.ui.graphics.Color.White,
            )
        }
        IconButton(onClick = onEnterPip) {
            Icon(
                painter = painterResource(R.drawable.ic_pip),
                contentDescription = stringResource(R.string.player_pip),
                tint = androidx.compose.ui.graphics.Color.White,
            )
        }
    }
}

@Composable
private fun PlayerTransport(
    locked: Boolean,
    positionMs: Long,
    durationMs: Long,
    scrubbing: Float?,
    isPlaying: Boolean,
    isEnded: Boolean,
    speed: Float,
    zoomed: Boolean,
    onScrub: (Float) -> Unit,
    onScrubFinished: (Float) -> Unit,
    onTogglePlay: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onToggleSpeed: () -> Unit,
    onToggleZoom: () -> Unit,
    captionsAvailable: Boolean,
    captionsEnabled: Boolean,
    onToggleCaptions: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    if (locked) return
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        val fraction = when {
            scrubbing != null -> scrubbing
            durationMs > 0L -> (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
            else -> 0f
        }
        Slider(
            value = fraction,
            onValueChange = onScrub,
            onValueChangeFinished = { onScrubFinished(fraction) },
            colors = SliderDefaults.colors(
                thumbColor = androidx.compose.ui.graphics.Color.White,
                activeTrackColor = androidx.compose.ui.graphics.Color.White,
                inactiveTrackColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.35f),
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = PlaybackClock.format(positionMs),
                color = androidx.compose.ui.graphics.Color.White,
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                text = PlaybackClock.format(durationMs),
                color = androidx.compose.ui.graphics.Color.White,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            IconButton(onClick = { onSeekBy(-REWIND_MS) }) {
                Icon(
                    painter = painterResource(R.drawable.ic_rewind_10),
                    contentDescription = stringResource(R.string.player_rewind, 10),
                    tint = androidx.compose.ui.graphics.Color.White,
                )
            }
            IconButton(
                onClick = onTogglePlay,
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(28.dp),
                    ),
            ) {
                Icon(
                    painter = painterResource(
                        if (isPlaying && !isEnded) R.drawable.ic_pause else R.drawable.ic_play,
                    ),
                    contentDescription = stringResource(
                        if (isPlaying && !isEnded) R.string.player_pause else R.string.player_play,
                    ),
                    tint = androidx.compose.ui.graphics.Color.White,
                )
            }
            IconButton(onClick = { onSeekBy(REWIND_MS) }) {
                Icon(
                    painter = painterResource(R.drawable.ic_forward_10),
                    contentDescription = stringResource(R.string.player_forward, 10),
                    tint = androidx.compose.ui.graphics.Color.White,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onToggleSpeed) {
                Text(
                    text = SpeedPresets.label(speed),
                    color = androidx.compose.ui.graphics.Color.White,
                )
            }
            TextButton(onClick = onToggleZoom) {
                Text(
                    text = stringResource(
                        if (zoomed) R.string.player_aspect_fit else R.string.player_aspect_fill,
                    ),
                    color = androidx.compose.ui.graphics.Color.White,
                )
            }
            if (captionsAvailable) {
                TextButton(onClick = onToggleCaptions) {
                    Text(
                        text = stringResource(
                            if (captionsEnabled) R.string.player_captions_on else R.string.player_captions_off,
                        ),
                        color = if (captionsEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            androidx.compose.ui.graphics.Color.White
                        },
                    )
                }
            }
            TextButton(onClick = onOpenSettings) {
                Text(
                    text = stringResource(R.string.player_settings),
                    color = androidx.compose.ui.graphics.Color.White,
                )
            }
        }
    }
}

private const val REWIND_MS = 10_000L

@Composable
private fun SubtitleOverlay(
    lines: List<String>,
    size: SubtitleSize,
    background: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        lines.forEach { line ->
            Text(
                text = line,
                color = androidx.compose.ui.graphics.Color.White,
                fontSize = size.sizeSp.sp,
                textAlign = TextAlign.Center,
                modifier = if (background) {
                    Modifier
                        .background(
                            color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(4.dp),
                        )
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                } else {
                    Modifier
                },
            )
        }
    }
}

@Composable
private fun LockBadge(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.material3.Surface(
        modifier = modifier.clickable(onClick = onClick),
        color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f),
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_lock),
                contentDescription = null,
                tint = androidx.compose.ui.graphics.Color.White,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = stringResource(R.string.player_locked),
                color = androidx.compose.ui.graphics.Color.White,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun PlaybackSettingsSheet(
    delay: Float,
    size: SubtitleSize,
    background: Boolean,
    captionsAvailable: Boolean,
    captionsEnabled: Boolean,
    softwareDecoding: Boolean,
    onDelay: (Float) -> Unit,
    onSize: (SubtitleSize) -> Unit,
    onBackground: (Boolean) -> Unit,
    onToggleCaptions: () -> Unit,
    onSoftwareDecoding: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
    ) {
        Text(
            text = stringResource(R.string.player_settings_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.size(12.dp))

        if (captionsAvailable) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.player_captions_on),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = captionsEnabled, onCheckedChange = { onToggleCaptions() })
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.player_subtitle_delay),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = { onDelay(-SubtitleDelay.STEP_SECONDS) },
                enabled = delay > SubtitleDelay.MIN_SECONDS,
            ) {
                Text(text = "-0.5s")
            }
            Text(
                text = SubtitleDelay.label(delay),
                style = MaterialTheme.typography.labelLarge,
            )
            TextButton(
                onClick = { onDelay(SubtitleDelay.STEP_SECONDS) },
                enabled = delay < SubtitleDelay.MAX_SECONDS,
            ) {
                Text(text = "+0.5s")
            }
        }

        Text(
            text = stringResource(R.string.player_subtitle_size),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SubtitleSize.entries.forEach { option ->
                FilterChip(
                    selected = option == size,
                    onClick = { onSize(option) },
                    label = { Text(text = option.label) },
                )
            }
        }
        Spacer(modifier = Modifier.size(4.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.player_subtitle_background),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = background, onCheckedChange = onBackground)
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.player_software_decoding),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = softwareDecoding, onCheckedChange = onSoftwareDecoding)
        }
        Text(
            text = stringResource(R.string.player_software_decoding_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private enum class PinPrompt { SET, ENTER }

@Composable
private fun PinDialog(
    prompt: PinPrompt,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var value by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(
                    if (prompt == PinPrompt.SET) {
                        R.string.player_pin_title
                    } else {
                        R.string.player_unlock_title
                    },
                ),
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = { input ->
                        if (input.length <= KidsLock.PIN_LENGTH && input.all { it in '0'..'9' }) {
                            value = input
                        }
                    },
                    singleLine = true,
                    label = { Text(text = stringResource(R.string.player_pin_label)) },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                    ),
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                )
                error?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(value) }) {
                Text(text = stringResource(R.string.player_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.player_cancel))
            }
        },
    )
}
