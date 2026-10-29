package com.vortexa.feature.music

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vortexa.core.playback.EqualizerProfile
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vortexa.core.common.format.Formatters
import com.vortexa.core.media.MediaTrack

/**
 * Music tab. With online sources off (the default) it explains how to enable
 * them instead of showing a dead search box; when on, it searches every enabled
 * source and plays through the shared session so background playback and the
 * notification work exactly like local files.
 */
@Composable
fun MusicScreen(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MusicViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showEqualizer by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        if (!state.sourcesEnabled) {
            SourcesOff(onOpenSettings = onOpenSettings)
            return@Column
        }

        SearchBar(
            query = state.query,
            enabled = !state.searching,
            onQueryChange = viewModel::onQueryChange,
            onSearch = viewModel::search,
        )

        if (state.searching) {
            Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        state.message?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        state.failures.forEach { failure ->
            Text(
                text = "${failure.displayName}: ${failure.message}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        if (state.results.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 12.dp),
            ) {
                items(state.results, key = { "${it.extractorId}:${it.id}" }) { track ->
                    TrackRow(track = track, onPlay = { viewModel.play(track, state.results) })
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 64.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    )
                }
            }
        }

        NowPlayingBar(
            state = state,
            onTogglePlay = viewModel::pauseOrResume,
            onSleepTimer = viewModel::setSleepTimer,
            onOpenEqualizer = { showEqualizer = true },
        )
    }

    if (showEqualizer) {
        EqualizerDialog(
            enabled = state.equalizerEnabled,
            bands = state.equalizerBands,
            onChange = viewModel::setEqualizer,
            onDismiss = { showEqualizer = false },
        )
    }
}

@Composable
private fun EqualizerDialog(
    enabled: Boolean,
    bands: List<Int>,
    onChange: (Boolean, List<Int>) -> Unit,
    onDismiss: () -> Unit,
) {
    val values = remember(bands) { mutableStateListOf(*bands.toTypedArray()) }
    val labels = listOf("60 Hz", "230 Hz", "910 Hz", "3.6 kHz", "14 kHz")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.music_equalizer)) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.music_and_audio),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = enabled,
                        onCheckedChange = { onChange(it, values.toList()) },
                    )
                }
                if (!enabled) {
                    Text(
                        text = stringResource(R.string.music_equalizer_off_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                values.forEachIndexed { index, gain ->
                    Text(
                        text = "${labels[index]}   ${gain / 100f} dB",
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Slider(
                        value = gain.toFloat(),
                        onValueChange = { values[index] = it.toInt() },
                        onValueChangeFinished = { onChange(enabled, values.toList()) },
                        valueRange = EqualizerProfile.MIN_LEVEL_MB.toFloat()..EqualizerProfile.MAX_LEVEL_MB.toFloat(),
                        steps = 29,
                        enabled = enabled,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EqualizerProfile.presets.forEach { preset ->
                        AssistChip(
                            onClick = {
                                values.clear()
                                values.addAll(preset.gains)
                                onChange(enabled, preset.gains)
                            },
                            label = { Text(text = preset.name) },
                            enabled = enabled,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.music_close)) }
        },
    )
}

@Composable
private fun SearchBar(
    query: String,
    enabled: Boolean,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        singleLine = true,
        placeholder = { Text(text = stringResource(R.string.music_search_hint)) },
        enabled = enabled,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        trailingIcon = {
            IconButton(onClick = onSearch, enabled = enabled) {
                Icon(
                    painter = painterResource(R.drawable.ic_search),
                    contentDescription = stringResource(R.string.music_search),
                )
            }
        },
    )
}

@Composable
private fun TrackRow(track: MediaTrack, onPlay: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPlay) {
            Icon(
                painter = painterResource(com.vortexa.core.ui.R.drawable.ic_action_play),
                contentDescription = stringResource(R.string.music_play),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(modifier = Modifier.size(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val details = buildList {
                track.subtitle?.let(::add)
                if (track.durationMs > 0L) add(Formatters.duration(track.durationMs / 1000))
                if (track.isLive) add("LIVE")
            }
            if (details.isNotEmpty()) {
                Text(
                    text = details.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun NowPlayingBar(
    state: MusicUiState,
    onTogglePlay: () -> Unit,
    onSleepTimer: (Int?) -> Unit,
    onOpenEqualizer: () -> Unit,
) {
    val title = state.nowPlayingTitle ?: return
    Surface(tonalElevation = 3.dp) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onTogglePlay) {
                    Icon(
                        painter = painterResource(
                            if (state.isPlaying) {
                                com.vortexa.core.ui.R.drawable.ic_action_pause
                            } else {
                                com.vortexa.core.ui.R.drawable.ic_action_play
                            },
                        ),
                        contentDescription = stringResource(
                            if (state.isPlaying) R.string.music_pause else R.string.music_play,
                        ),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    state.nowPlayingArtist?.let { artist ->
                        Text(
                            text = artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15, 30, 60).forEach { minutes ->
                    AssistChip(
                        onClick = {
                            onSleepTimer(if (state.sleepTimerMinutes == minutes) null else minutes)
                        },
                        label = { Text(text = "$minutes min") },
                    )
                }
                TextButton(onClick = { onSleepTimer(null) }) {
                    Text(text = stringResource(R.string.music_sleep_off))
                }
                TextButton(onClick = onOpenEqualizer) {
                    Text(text = stringResource(R.string.music_equalizer))
                }
            }
        }
    }
}

@Composable
private fun SourcesOff(onOpenSettings: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Text(
                text = stringResource(R.string.music_sources_off_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.music_sources_off_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(onClick = onOpenSettings) {
                Text(text = stringResource(R.string.music_open_settings))
            }
        }
    }
}
