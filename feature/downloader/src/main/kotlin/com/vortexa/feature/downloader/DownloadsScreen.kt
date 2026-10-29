package com.vortexa.feature.downloader

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vortexa.core.common.format.Formatters
import com.vortexa.core.database.entity.DownloadEntity
import com.vortexa.core.database.entity.DownloadStatus
import com.vortexa.core.ui.component.PhaseBadge

@Composable
fun DownloadsScreen(
    modifier: Modifier = Modifier,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DownloadsContent(
        state = state,
        onAdd = viewModel::addDownload,
        onPause = viewModel::pause,
        onResume = viewModel::resume,
        onCancel = viewModel::cancel,
        modifier = modifier,
    )
}

@Composable
internal fun DownloadsContent(
    state: DownloadsUiState,
    onAdd: (String) -> Boolean,
    onPause: (Long) -> Unit,
    onResume: (Long) -> Unit,
    onCancel: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAddDialog by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            state.isLoading -> Unit

            state.isEmpty -> EmptyDownloads(modifier = Modifier.fillMaxSize())

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(items = state.downloads, key = { it.id }) { download ->
                    DownloadRow(
                        download = download,
                        speed = state.speeds[download.id] ?: 0L,
                        onPause = { onPause(download.id) },
                        onResume = { onResume(download.id) },
                        onCancel = { onCancel(download.id) },
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
        ) {
            Icon(
                painter = painterResource(com.vortexa.core.ui.R.drawable.ic_action_add),
                contentDescription = stringResource(R.string.downloads_add),
            )
        }
    }

    if (showAddDialog) {
        AddDownloadDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { url ->
                val accepted = onAdd(url)
                if (accepted) showAddDialog = false
                accepted
            },
        )
    }
}

@Composable
private fun AddDownloadDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Boolean,
) {
    var url by remember { mutableStateOf("") }
    var isInvalid by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.downloads_add_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it
                        isInvalid = false
                    },
                    singleLine = true,
                    isError = isInvalid,
                    placeholder = { Text(stringResource(R.string.downloads_add_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (isInvalid) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.downloads_add_invalid),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { isInvalid = !onConfirm(url) }) {
                Text(stringResource(R.string.downloads_add_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun EmptyDownloads(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(com.vortexa.core.ui.R.drawable.ic_empty_downloads),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.downloads_empty_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.downloads_empty_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        PhaseBadge(text = stringResource(R.string.feature_phase))
    }
}

@Composable
private fun DownloadRow(
    download: DownloadEntity,
    speed: Long,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = download.fileName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = statusLine(download, speed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when (download.status) {
                DownloadStatus.RUNNING, DownloadStatus.QUEUED -> IconButton(onClick = onPause) {
                    Icon(
                        painter = painterResource(com.vortexa.core.ui.R.drawable.ic_action_pause),
                        contentDescription = stringResource(R.string.action_pause),
                    )
                }

                DownloadStatus.PAUSED, DownloadStatus.FAILED -> IconButton(onClick = onResume) {
                    Icon(
                        painter = painterResource(com.vortexa.core.ui.R.drawable.ic_action_play),
                        contentDescription = stringResource(R.string.action_resume),
                    )
                }

                else -> Unit
            }
            IconButton(onClick = onCancel) {
                Icon(
                    painter = painterResource(com.vortexa.core.ui.R.drawable.ic_action_delete),
                    contentDescription = stringResource(R.string.action_cancel),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { download.progress },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun statusLine(download: DownloadEntity, speed: Long): String {
    val status = when (download.status) {
        DownloadStatus.QUEUED -> stringResource(R.string.status_queued)
        DownloadStatus.RUNNING -> if (speed > 0) Formatters.speed(speed) else stringResource(R.string.status_running)
        DownloadStatus.PAUSED -> stringResource(R.string.status_paused)
        DownloadStatus.COMPLETED -> stringResource(R.string.status_completed)
        DownloadStatus.FAILED -> stringResource(R.string.status_failed)
        DownloadStatus.CANCELED -> stringResource(R.string.status_canceled)
    }
    val size = if (download.totalBytes > 0) {
        "${Formatters.bytes(download.downloadedBytes)} / ${Formatters.bytes(download.totalBytes)}"
    } else {
        Formatters.bytes(download.downloadedBytes)
    }
    return "$status · $size"
}
