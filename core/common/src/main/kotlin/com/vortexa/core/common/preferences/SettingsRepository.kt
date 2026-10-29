package com.vortexa.core.common.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/** Everything the settings screen owns. Defaults are production defaults, not test values. */
data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val languageTag: String = "system",
    /** Third-party extractor plugins. Off until the user turns them on. */
    val onlineSourcesEnabled: Boolean = false,
    /** Equalizer switch and per-band gains in millibels, CSV: "-300,0,200,0,-100". */
    val equalizerEnabled: Boolean = false,
    val equalizerBands: String = "",
)

interface SettingsRepository {
    val preferences: Flow<UserPreferences>
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setDynamicColor(enabled: Boolean)
    suspend fun setLanguageTag(tag: String)
    suspend fun setOnlineSourcesEnabled(enabled: Boolean)
    suspend fun setEqualizer(enabled: Boolean, bands: String)
}

// A single DataStore instance per process; the delegate enforces that.
val Context.vortexaDataStore: DataStore<Preferences> by preferencesDataStore(name = "vortexa_settings")

class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val LANGUAGE_TAG = stringPreferencesKey("language_tag")
        val ONLINE_SOURCES = booleanPreferencesKey("online_sources_enabled")
        val EQUALIZER_ENABLED = booleanPreferencesKey("equalizer_enabled")
        val EQUALIZER_BANDS = stringPreferencesKey("equalizer_bands")
    }

    override val preferences: Flow<UserPreferences> = dataStore.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { prefs ->
            UserPreferences(
                themeMode = ThemeMode.fromStorage(prefs[Keys.THEME_MODE]),
                dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: true,
                languageTag = prefs[Keys.LANGUAGE_TAG] ?: "system",
                onlineSourcesEnabled = prefs[Keys.ONLINE_SOURCES] ?: false,
                equalizerEnabled = prefs[Keys.EQUALIZER_ENABLED] ?: false,
                equalizerBands = prefs[Keys.EQUALIZER_BANDS] ?: "",
            )
        }

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    override suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    override suspend fun setLanguageTag(tag: String) {
        dataStore.edit { it[Keys.LANGUAGE_TAG] = tag }
    }

    override suspend fun setOnlineSourcesEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.ONLINE_SOURCES] = enabled }
    }

    override suspend fun setEqualizer(enabled: Boolean, bands: String) {
        dataStore.edit {
            it[Keys.EQUALIZER_ENABLED] = enabled
            it[Keys.EQUALIZER_BANDS] = bands
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
object SettingsModule {

    @Provides
    @Singleton
    fun provideSettingsRepository(@ApplicationContext context: Context): SettingsRepository =
        DataStoreSettingsRepository(context.vortexaDataStore)
}
