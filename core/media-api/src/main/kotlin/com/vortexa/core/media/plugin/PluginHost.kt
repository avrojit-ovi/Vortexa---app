package com.vortexa.core.media.plugin

import android.content.Context
import com.vortexa.core.media.Extractor
import dalvik.system.DexClassLoader
import java.io.File

/**
 * Loads user-supplied extractor plugins from a `.apk` the user picked.
 *
 * Contract (documented in docs/PLUGIN_API.md): the APK contains a class
 * `com.vortexa.plugin.Entry` implementing [PluginEntry]. Because the plugin is
 * loaded with the app's class loader as parent, the [Extractor] interface has
 * exactly one identity at runtime — no AIDL, no duplicated copies.
 *
 * Everything here is defensive: a plugin that does not compile against this API
 * version, throws while loading, or returns garbage is reported as a
 * [PluginFailure] and never crashes the app. Loading code from an APK the user
 * chose is a trust decision, which is why plugins stay off until enabled.
 */
class PluginHost(private val context: Context) {

    fun pluginsDir(): File = File(context.filesDir, DIRECTORY).apply { mkdirs() }

    fun installedFiles(): List<File> =
        pluginsDir().listFiles { file -> file.isFile && file.extension.equals("apk", true) }
            ?.sortedBy { it.name.lowercase() }
            .orEmpty()

    /** Copies a SAF-picked APK into the private plugin folder. */
    fun install(source: File, displayName: String): File {
        val target = File(pluginsDir(), safeName(displayName))
        source.inputStream().use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        return target
    }

    fun remove(fileName: String): Boolean = File(pluginsDir(), fileName).delete()

    fun loadAll(): PluginLoadResult {
        val loaded = mutableListOf<LoadedPlugin>()
        val failed = mutableListOf<PluginFailure>()

        installedFiles().forEach { apk ->
            when (val result = load(apk)) {
                is Result.Success -> loaded += result.plugin
                is Result.Failure -> failed += PluginFailure(apk.name, result.message)
            }
        }
        return PluginLoadResult(loaded = loaded, failed = failed)
    }

    private fun load(apk: File): Result {
        val dexDir = File(context.codeCacheDir, "plugin-${apk.nameWithoutExtension}")
        val classLoader = runCatching {
            DexClassLoader(apk.absolutePath, dexDir.absolutePath, null, context.classLoader)
        }.getOrElse { return Result.Failure("Could not read the APK") }

        val entry = runCatching {
            val type = Class.forName(ENTRY_CLASS, true, classLoader)
            type.getDeclaredConstructor().newInstance() as? PluginEntry
        }.getOrElse { return Result.Failure("No usable $ENTRY_CLASS (${it.javaClass.simpleName})") }
            ?: return Result.Failure("$ENTRY_CLASS does not implement PluginEntry")

        if (entry.apiVersion != API_VERSION) {
            return Result.Failure(
                "Plugin API ${entry.apiVersion} does not match app API $API_VERSION",
            )
        }

        val extractors = runCatching { entry.createExtractors() }
            .getOrElse { return Result.Failure("createExtractors() failed: ${it.message}") }
            .filterIsInstance<Extractor>()

        if (extractors.isEmpty()) return Result.Failure("Plugin offers no extractors")

        return Result.Success(
            LoadedPlugin(
                fileName = apk.name,
                name = entry.name.ifBlank { apk.nameWithoutExtension },
                extractors = extractors,
            ),
        )
    }

    private fun safeName(raw: String): String {
        val base = raw.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .ifBlank { "plugin.apk" }
        return if (base.endsWith(".apk", ignoreCase = true)) base else "$base.apk"
    }

    private sealed interface Result {
        data class Success(val plugin: LoadedPlugin) : Result
        data class Failure(val message: String) : Result
    }

    companion object {
        /** Bumped only when the plugin contract changes in a breaking way. */
        const val API_VERSION = 1
        const val ENTRY_CLASS = "com.vortexa.plugin.Entry"
        private const val DIRECTORY = "plugins"
    }
}

/** Version of the `Extractor` contract a plugin compiles against. */
interface PluginEntry {
    val apiVersion: Int
    val name: String
    fun createExtractors(): List<Extractor>
}

data class LoadedPlugin(
    val fileName: String,
    val name: String,
    val extractors: List<Extractor>,
)

data class PluginFailure(
    val fileName: String,
    val message: String,
)

data class PluginLoadResult(
    val loaded: List<LoadedPlugin> = emptyList(),
    val failed: List<PluginFailure> = emptyList(),
)
