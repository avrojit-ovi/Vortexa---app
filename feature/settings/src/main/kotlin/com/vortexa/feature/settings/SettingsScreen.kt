package com.vortexa.feature.settings

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vortexa.core.common.preferences.ThemeMode
import com.vortexa.core.common.preferences.UserPreferences
import com.vortexa.core.online.OnlineSourcesState
import com.vortexa.core.online.SourceInfo

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val online by viewModel.online.collectAsStateWithLifecycle()
    val message by viewModel.pluginMessage.collectAsStateWithLifecycle()

    val pickPlugin = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        viewModel.installPlugin(uri)
    }

    SettingsContent(
        preferences = preferences,
        online = online,
        pluginMessage = message,
        onThemeModeChange = viewModel::setThemeMode,
        onDynamicColorChange = viewModel::setDynamicColor,
        onSpeedLimitChange = viewModel::setSpeedLimit,
        onScheduleChange = viewModel::setSchedule,
        onOnlineSourcesChange = viewModel::setOnlineSources,
        onReloadSources = viewModel::reloadSources,
        onAddPlugin = {
            // Any MIME filter that still shows .apk files: some pickers report octet-stream.
            pickPlugin.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream"))
        },
        onRemovePlugin = viewModel::removePlugin,
        onDismissMessage = viewModel::clearPluginMessage,
        modifier = modifier,
    )
}

@Composable
internal fun SettingsContent(
    preferences: UserPreferences,
    online: OnlineSourcesState,
    pluginMessage: PluginMessage?,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onSpeedLimitChange: (Int) -> Unit,
    onScheduleChange: (Boolean, Int, Int) -> Unit,
    onOnlineSourcesChange: (Boolean) -> Unit,
    onReloadSources: () -> Unit,
    onAddPlugin: () -> Unit,
    onRemovePlugin: (String) -> Unit,
    onDismissMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        SectionHeader(text = stringResource(R.string.settings_appearance))

        ThemeMode.entries.forEach { mode ->
            ThemeRow(
                label = stringResource(mode.labelRes()),
                selected = preferences.themeMode == mode,
                onSelect = { onThemeModeChange(mode) },
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_dynamic_color),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.settings_dynamic_color_summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = preferences.dynamicColor,
                onCheckedChange = onDynamicColorChange,
                enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        DownloadsSection(
            preferences = preferences,
            onSpeedLimitChange = onSpeedLimitChange,
            onScheduleChange = onScheduleChange,
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        OnlineSourcesSection(
            online = online,
            pluginMessage = pluginMessage,
            onEnabledChange = onOnlineSourcesChange,
            onReload = onReloadSources,
            onAddPlugin = onAddPlugin,
            onRemovePlugin = onRemovePlugin,
            onDismissMessage = onDismissMessage,
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        SectionHeader(text = stringResource(R.string.settings_about))
        Text(
            text = stringResource(R.string.settings_version, appVersion()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.settings_license),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun DownloadsSection(
    preferences: UserPreferences,
    onSpeedLimitChange: (Int) -> Unit,
    onScheduleChange: (Boolean, Int, Int) -> Unit,
) {
    SectionHeader(text = stringResource(R.string.settings_downloads))
    Text(
        text = stringResource(R.string.settings_speed_limit),
        style = MaterialTheme.typography.titleMedium,
    )
    SPEED_LIMITS.forEach { kbps ->
        ThemeRow(
            label = if (kbps == 0) {
                stringResource(R.string.settings_speed_unlimited)
            } else {
                stringResource(R.string.settings_speed_value, kbps)
            },
            selected = preferences.speedLimitKbps == kbps,
            onSelect = { onSpeedLimitChange(kbps) },
        )
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.settings_schedule),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(
                    R.string.settings_schedule_summary,
                    DownloadScheduleLabel.format(preferences.scheduleStartMinute, preferences.scheduleEndMinute),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = preferences.scheduleEnabled,
            onCheckedChange = { enabled ->
                onScheduleChange(enabled, preferences.scheduleStartMinute, preferences.scheduleEndMinute)
            },
        )
    }

    if (preferences.scheduleEnabled) {
        SCHEDULE_WINDOWS.forEach { window ->
            ThemeRow(
                label = DownloadScheduleLabel.format(window.first, window.second),
                selected = preferences.scheduleStartMinute == window.first &&
                    preferences.scheduleEndMinute == window.second,
                onSelect = { onScheduleChange(true, window.first, window.second) },
            )
        }
    }
}

/** Formatting shared with the engine's schedule, without importing it here. */
private object DownloadScheduleLabel {
    fun format(startMinute: Int, endMinute: Int): String =
        "${formatMinute(startMinute)} – ${formatMinute(endMinute)}"

    private fun formatMinute(minuteOfDay: Int): String {
        val minute = minuteOfDay.coerceIn(0, 24 * 60 - 1)
        return "%02d:%02d".format(minute / 60, minute % 60)
    }
}

private val SPEED_LIMITS = listOf(0, 512, 1024, 2048)
private val SCHEDULE_WINDOWS = listOf(23 * 60 to 6 * 60, 1 * 60 to 7 * 60)

@Composable
private fun OnlineSourcesSection(
    online: OnlineSourcesState,
    pluginMessage: PluginMessage?,
    onEnabledChange: (Boolean) -> Unit,
    onReload: () -> Unit,
    onAddPlugin: () -> Unit,
    onRemovePlugin: (String) -> Unit,
    onDismissMessage: () -> Unit,
) {
    SectionHeader(text = stringResource(R.string.settings_online))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.settings_online_summary),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.settings_online_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Switch(checked = online.enabled, onCheckedChange = onEnabledChange)
    }

    if (online.enabled) {
        Text(
            text = stringResource(R.string.settings_online_sources),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (online.sources.isEmpty()) {
            Text(
                text = stringResource(R.string.settings_online_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        online.sources.forEach { source ->
            SourceRow(
                source = source,
                onRemove = source.pluginFile?.let { file -> { onRemovePlugin(file) } },
            )
        }

        online.failures.forEach { failure ->
            Text(
                text = stringResource(R.string.settings_online_plugin_failed, failure.fileName, failure.message),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = onAddPlugin) {
                Text(text = stringResource(R.string.settings_online_add))
            }
            TextButton(onClick = onReload) {
                Text(text = stringResource(R.string.settings_online_refresh))
            }
        }
    }

    when (val message = pluginMessage) {
        null -> Unit
        PluginMessage.Installed -> Text(
            text = stringResource(R.string.settings_online_installed),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 8.dp),
        )

        is PluginMessage.Failed -> Text(
            text = message.reason,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
    if (pluginMessage != null) {
        TextButton(onClick = onDismissMessage) { Text(text = "OK") }
    }
}

@Composable
private fun SourceRow(source: SourceInfo, onRemove: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = source.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = if (source.isBuiltIn) {
                    stringResource(R.string.settings_online_builtin)
                } else {
                    "${source.id} · v${source.version}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onRemove != null) {
            TextButton(onClick = onRemove) {
                Text(text = stringResource(R.string.settings_online_remove))
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(top = 16.dp, bottom = 4.dp)
            .semantics { heading() },
    )
}

@Composable
private fun ThemeRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.settings_theme_system
    ThemeMode.LIGHT -> R.string.settings_theme_light
    ThemeMode.DARK -> R.string.settings_theme_dark
    ThemeMode.AMOLED -> R.string.settings_theme_amoled
}

@Composable
private fun appVersion(): String {
    val context = LocalContext.current
    return runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "-"
}
