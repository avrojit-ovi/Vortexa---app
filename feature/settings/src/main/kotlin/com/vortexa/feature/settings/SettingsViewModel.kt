package com.vortexa.feature.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vortexa.core.common.preferences.SettingsRepository
import com.vortexa.core.common.preferences.ThemeMode
import com.vortexa.core.common.preferences.UserPreferences
import com.vortexa.core.online.OnlineSourcesRepository
import com.vortexa.core.online.OnlineSourcesState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val onlineSources: OnlineSourcesRepository,
) : ViewModel() {

    val preferences: StateFlow<UserPreferences> = settingsRepository.preferences
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UserPreferences(),
        )

    val online: StateFlow<OnlineSourcesState> = onlineSources.state

    /** Last plugin-related feedback for the UI, if any. */
    private val _pluginMessage = MutableStateFlow<PluginMessage?>(null)
    val pluginMessage: StateFlow<PluginMessage?> = _pluginMessage

    init {
        viewModelScope.launch { onlineSources.ensureLoaded() }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDynamicColor(enabled) }
    }

    fun setSpeedLimit(kbps: Int) {
        viewModelScope.launch { settingsRepository.setSpeedLimitKbps(kbps) }
    }

    fun setSchedule(enabled: Boolean, startMinute: Int, endMinute: Int) {
        viewModelScope.launch {
            settingsRepository.setDownloadSchedule(enabled, startMinute, endMinute)
        }
    }

    fun setOnlineSources(enabled: Boolean) {
        viewModelScope.launch { onlineSources.setEnabled(enabled) }
    }

    fun reloadSources() {
        viewModelScope.launch { onlineSources.reload() }
    }

    /** Copies the picked APK out of the SAF URI, then hands it to the loader. */
    fun installPlugin(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val name = queryDisplayName(uri) ?: "plugin.apk"
                    val temp = File(context.cacheDir, "incoming-plugin.apk")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        temp.outputStream().use { output -> input.copyTo(output) }
                    } ?: error("Could not read the selected file")
                    onlineSources.installPlugin(temp, name).getOrThrow()
                    temp.delete()
                }
            }
            _pluginMessage.value = result.fold(
                onSuccess = { PluginMessage.Installed },
                onFailure = { PluginMessage.Failed(it.message ?: it.javaClass.simpleName) },
            )
        }
    }

    fun removePlugin(fileName: String) {
        viewModelScope.launch { onlineSources.removePlugin(fileName) }
    }

    fun clearPluginMessage() {
        _pluginMessage.value = null
    }

    private fun queryDisplayName(uri: Uri): String? {
        val projection = arrayOf(android.provider.OpenableColumns.DISPLAY_NAME)
        return runCatching {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
    }
}

sealed interface PluginMessage {
    data object Installed : PluginMessage
    data class Failed(val reason: String) : PluginMessage
}
