package com.vortexa.feature.video.player

import android.content.ComponentName
import android.graphics.Color as AndroidColor
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
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

    BackHandler(onBack = onBack)

    DisposableEffect(context, path) {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            {
                runCatching { future.get() }.onSuccess { connected ->
                    val saved = positions.lastPosition(path)
                    val startAt = if (saved >= ResumePolicy.MIN_RESUME_MS) saved else 0L
                    connected.setMediaItem(
                        MediaItem.Builder()
                            .setUri(Uri.fromFile(File(path)))
                            .setMediaMetadata(
                                androidx.media3.common.MediaMetadata.Builder()
                                    .setTitle(title)
                                    .build(),
                            )
                            .build(),
                        startAt,
                    )
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
                onBack = onBack,
                onEnterPip = onEnterPip,
            )
            Spacer(modifier = Modifier.weight(1f))
            PlayerTransport(
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
            )
        }
    }
}

@Composable
private fun PlayerTopBar(
    title: String,
    onBack: () -> Unit,
    onEnterPip: () -> Unit,
) {
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
) {
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
        }
    }
}

private const val REWIND_MS = 10_000L
