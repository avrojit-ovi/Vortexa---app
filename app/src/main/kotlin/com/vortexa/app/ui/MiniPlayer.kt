package com.vortexa.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.session.MediaController
import com.vortexa.app.R as AppR
import com.vortexa.core.playback.PlaybackService
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * What is playing right now, on every screen.
 *
 * It reads from the same [PlaybackService] session the video player and the
 * Music tab use, so it appears for both and never owns a second player. It only
 * polls while mounted, and it renders nothing when the session is empty.
 */
@Composable
fun MiniPlayer(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var title by remember { mutableStateOf<String?>(null) }
    var subtitle by remember { mutableStateOf<String?>(null) }
    var isPlaying by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        val future = PlaybackService.connect(context)
        future.addListener(
            {
                runCatching { future.get() }.onSuccess { connected -> controller = connected }
            },
            java.util.concurrent.Executor { command -> android.os.Handler(context.mainLooper).post(command) },
        )
        onDispose {
            controller = null
            MediaController.releaseFuture(future)
        }
    }

    LaunchedEffect(controller) {
        val player = controller ?: return@LaunchedEffect
        while (isActive) {
            val metadata = player.mediaMetadata
            title = metadata.title?.toString()?.takeIf { it.isNotBlank() }
            subtitle = metadata.artist?.toString()?.takeIf { it.isNotBlank() }
            isPlaying = player.isPlaying
            delay(700)
        }
    }

    val currentTitle = title ?: return

    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = {
                    val player = controller ?: return@IconButton
                    if (player.isPlaying) player.pause() else player.play()
                },
            ) {
                Icon(
                    painter = painterResource(
                        if (isPlaying) AppR.drawable.ic_mini_pause else AppR.drawable.ic_mini_play,
                    ),
                    contentDescription = stringResource(
                        if (isPlaying) AppR.string.mini_player_pause else AppR.string.mini_player_play,
                    ),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Column(modifier = Modifier.padding(horizontal = 8.dp)) {
                Text(
                    text = currentTitle,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
