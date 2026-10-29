package com.vortexa.core.online

import android.content.Context
import com.vortexa.core.common.preferences.SettingsRepository
import com.vortexa.core.media.DirectLinkExtractor
import com.vortexa.core.media.ExtractorRegistry
import com.vortexa.core.media.MediaQuery
import com.vortexa.core.media.MediaTrack
import com.vortexa.core.media.SearchOutcome
import com.vortexa.core.media.StreamLink
import com.vortexa.core.media.plugin.PluginFailure
import com.vortexa.core.media.plugin.PluginHost
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** One source that is currently answering searches. */
data class SourceInfo(
    val id: String,
    val name: String,
    val version: String,
    val isBuiltIn: Boolean,
    /** APK file name for plugin sources; null for the built-in one. */
    val pluginFile: String? = null,
)

data class OnlineSourcesState(
    val enabled: Boolean = false,
    val sources: List<SourceInfo> = emptyList(),
    val failures: List<PluginFailure> = emptyList(),
    val loading: Boolean = false,
)

/**
 * The only place that knows both the preference and the plugins.
 *
 * Off by default: with the toggle off nothing is loaded, not even the built-in
 * direct-link source, so the app has no online code path until the user opts in.
 */
@Singleton
class OnlineSourcesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val builtInSources: Set<@JvmSuppressWildcards BuiltInSourceProvider>,
) {

    private val host = PluginHost(context)
    private val registry = ExtractorRegistry()
    private val mutex = Mutex()
    private var loadedOnce = false

    private val _state = MutableStateFlow(OnlineSourcesState())
    val state: StateFlow<OnlineSourcesState> = _state.asStateFlow()

    suspend fun ensureLoaded() = mutex.withLock {
        if (loadedOnce) return@withLock
        loadedOnce = true
        reloadLocked(enabled = settings.preferences.first().onlineSourcesEnabled)
    }

    suspend fun setEnabled(enabled: Boolean) {
        settings.setOnlineSourcesEnabled(enabled)
        mutex.withLock { reloadLocked(enabled = enabled) }
    }

    suspend fun reload() = mutex.withLock {
        reloadLocked(enabled = settings.preferences.first().onlineSourcesEnabled)
    }

    suspend fun search(text: String): SearchOutcome {
        ensureLoaded()
        return registry.search(MediaQuery(text))
    }

    suspend fun resolve(track: MediaTrack): Result<StreamLink> {
        ensureLoaded()
        return registry.resolve(track)
    }

    /** Loads a user-picked plugin APK into the private plugin folder. */
    suspend fun installPlugin(file: File, displayName: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { host.install(file, displayName) }
            .map { }
            .onSuccess { reload() }
    }

    suspend fun removePlugin(fileName: String): Boolean {
        val removed = withContext(Dispatchers.IO) { host.remove(fileName) }
        if (removed) reload()
        return removed
    }

    fun pluginFiles(): List<String> = host.installedFiles().map { it.name }

    private suspend fun reloadLocked(enabled: Boolean) {
        registry.clear()
        if (!enabled) {
            _state.value = OnlineSourcesState(enabled = false)
            return
        }

        _state.value = OnlineSourcesState(enabled = true, loading = true)

        val builtIn = DirectLinkExtractor()
        registry.register(builtIn)
        builtInSources.forEach { provider ->
            provider.extractors().forEach { registry.register(it) }
        }

        val result = withContext(Dispatchers.IO) { host.loadAll() }
        val origins = mutableMapOf<String, String>()
        result.loaded.forEach { plugin ->
            plugin.extractors.forEach { extractor ->
                if (registry.register(extractor)) origins[extractor.id] = plugin.fileName
            }
        }

        _state.value = OnlineSourcesState(
            enabled = true,
            sources = registry.extractors.map { extractor ->
                SourceInfo(
                    id = extractor.id,
                    name = extractor.displayName,
                    version = extractor.version,
                    isBuiltIn = extractor.id == DirectLinkExtractor.ID ||
                        builtInSources.any { provider -> provider.extractors().any { it.id == extractor.id } },
                    pluginFile = origins[extractor.id],
                )
            },
            failures = result.failed,
            loading = false,
        )
    }
}
