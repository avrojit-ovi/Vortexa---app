package com.vortexa.feature.downloader

import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.vortexa.feature.downloader.engine.DownloadCategory
import com.vortexa.feature.downloader.engine.LinkBatch

@Composable
fun DownloadsScreen(
    modifier: Modifier = Modifier,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DownloadsContent(
        state = state,
        onAdd = viewModel::addDownloads,
        onPause = viewModel::pause,
        onResume = viewModel::resume,
        onCancel = viewModel::cancel,
        onSelectCategory = viewModel::selectCategory,
        modifier = modifier,
    )
}

@Composable
internal fun DownloadsContent(
    state: DownloadsUiState,
    onAdd: (String) -> Int,
    onPause: (Long) -> Unit,
    onResume: (Long) -> Unit,
    onCancel: (Long) -> Unit,
    onSelectCategory: (DownloadCategory?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAddDialog by remember { mutableStateOf(false) }
    // One tap on a link that is already queued would only create a duplicate row.
    val clipboardLink = rememberClipboardLink()
    var clipboardDismissed by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (!state.isLoading && !clipboardDismissed && clipboardLink != null && clipboardLink !in state.existingUrls) {
                ClipboardLinkBanner(
                    link = clipboardLink,
                    onAdd = { if (onAdd(clipboardLink) > 0) clipboardDismissed = true },
                    onDismiss = { clipboardDismissed = true },
                )
            }
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.isLoading -> Unit

                    state.isEmpty -> EmptyDownloads(modifier = Modifier.fillMaxSize())

                    else -> Column(modifier = Modifier.fillMaxSize()) {
                        // With a single category the chips would be noise, but a selection must
                        // always keep the way back to "All" visible.
                        if (state.categories.size > 1 || state.selectedCategory != null) {
                            CategoryChips(
                                categories = state.categories,
                                selected = state.selectedCategory,
                                onSelect = onSelectCategory,
                            )
                        }
                        if (state.isFilterEmpty) {
                            Text(
                                text = stringResource(R.string.downloads_category_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
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
                    }
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
            onConfirm = { text ->
                val accepted = onAdd(text)
                if (accepted > 0) showAddDialog = false
                accepted
            },
        )
    }
}

@Composable
private fun CategoryChips(
    categories: List<DownloadCategory>,
    selected: DownloadCategory?,
    onSelect: (DownloadCategory?) -> Unit,
) {
    Column {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                FilterChip(
                    selected = selected == null,
                    onClick = { onSelect(null) },
                    label = { Text(text = stringResource(R.string.downloads_category_all)) },
                )
            }
            items(categories, key = { it.name }) { category ->
                FilterChip(
                    selected = selected == category,
                    onClick = { onSelect(category) },
                    label = { Text(text = categoryLabel(category)) },
                )
            }
        }
    }
}

@Composable
private fun categoryLabel(category: DownloadCategory): String = when (category) {
    DownloadCategory.VIDEO -> stringResource(R.string.downloads_cat_video)
    DownloadCategory.AUDIO -> stringResource(R.string.downloads_cat_audio)
    DownloadCategory.IMAGE -> stringResource(R.string.downloads_cat_image)
    DownloadCategory.DOCUMENT -> stringResource(R.string.downloads_cat_document)
    DownloadCategory.APP -> stringResource(R.string.downloads_cat_app)
    DownloadCategory.ARCHIVE -> stringResource(R.string.downloads_cat_archive)
    DownloadCategory.OTHER -> stringResource(R.string.downloads_cat_other)
}

/**
 * The link offer only appears when the clipboard holds an http(s) link, and the
 * screen filters out links that are already queued. Reading the clipboard needs
 * no permission, but Android only hands it over while the app has focus — which
 * is exactly when this composable is on screen.
 */
@Composable
private fun rememberClipboardLink(): String? {
    val context = LocalContext.current
    var link by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = clipboard?.primaryClip
        val text = if (clip != null && clip.itemCount > 0) {
            clip.getItemAt(0).coerceToText(context)?.toString()
        } else {
            null
        }
        link = text?.let { LinkBatch.parse(it, limit = 1).firstOrNull() }
    }
    return link
}

@Composable
private fun ClipboardLinkBanner(
    link: String,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.downloads_clipboard_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = link,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = onAdd) {
                Text(text = stringResource(R.string.downloads_clipboard_add))
            }
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_dismiss))
            }
        }
    }
}

@Composable
private fun AddDownloadDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Int,
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
                    minLines = 1,
                    maxLines = 4,
                    isError = isInvalid,
                    placeholder = { Text(stringResource(R.string.downloads_add_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                if (isInvalid) {
                    Text(
                        text = stringResource(R.string.downloads_add_invalid),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.downloads_add_batch_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { isInvalid = onConfirm(url) == 0 }) {
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
