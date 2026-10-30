package com.vortexa.core.common.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vortexa.core.common.security.KeystoreSecretBox
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
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
    /** Browser ad/tracker blocking. On by default: it is the point of the browser. */
    val blockAds: Boolean = true,
    /** Hardware decoding unless the user asks for software — see [DecoderMode]. */
    val decoderMode: DecoderMode = DecoderMode.AUTO,
    /** Four digits that unlock a player the user locked for a child. Not a secret store. */
    val kidsLockPin: String = "",
    /** Salted PBKDF2 hash of the vault PIN; empty until the vault is set up. */
    val vaultPinHash: String = "",
    /** Download cap in KiB/s; 0 means unlimited. */
    val speedLimitKbps: Int = 0,
    /** When on, queued downloads only start inside the window below. */
    val scheduleEnabled: Boolean = false,
    val scheduleStartMinute: Int = 23 * 60,
    val scheduleEndMinute: Int = 6 * 60,
    /** Downloads go through a proxy when this is on and [proxyHost] is set. */
    val proxyEnabled: Boolean = false,
    val proxyType: ProxyType = ProxyType.HTTP,
    val proxyHost: String = "",
    val proxyPort: Int = 8080,
    val proxyUsername: String = "",
    /** True when a password is stored; the secret itself never leaves the repository. */
    val proxyHasPassword: Boolean = false,
    /** SAF tree URI of a user-picked folder (e.g. SD card); empty means app storage. */
    val downloadTreeUri: String = "",
)

interface SettingsRepository {
    val preferences: Flow<UserPreferences>
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setDynamicColor(enabled: Boolean)
    suspend fun setLanguageTag(tag: String)
    suspend fun setOnlineSourcesEnabled(enabled: Boolean)
    suspend fun setEqualizer(enabled: Boolean, bands: String)
    suspend fun setBlockAds(enabled: Boolean)
    suspend fun setDecoderMode(mode: DecoderMode)
    suspend fun setKidsLockPin(pin: String)
    suspend fun setVaultPinHash(hash: String)
    suspend fun setSpeedLimitKbps(kbps: Int)
    suspend fun setDownloadSchedule(enabled: Boolean, startMinute: Int, endMinute: Int)
    suspend fun setDownloadTreeUri(uri: String)

    /**
     * Stores the proxy. A blank [password] keeps the saved one, so changing the
     * host does not force the user to retype their password every time.
     */
    suspend fun setProxy(
        enabled: Boolean,
        type: ProxyType,
        host: String,
        port: Int,
        username: String,
        password: String,
    )

    /** Decrypts the stored proxy password; "" when none is set or the key was invalidated. */
    suspend fun proxyPassword(): String = ""
}

// A single DataStore instance per process; the delegate enforces that.
val Context.vortexaDataStore: DataStore<Preferences> by preferencesDataStore(name = "vortexa_settings")

class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>,
    private val secretBox: KeystoreSecretBox = KeystoreSecretBox(),
) : SettingsRepository {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val LANGUAGE_TAG = stringPreferencesKey("language_tag")
        val ONLINE_SOURCES = booleanPreferencesKey("online_sources_enabled")
        val EQUALIZER_ENABLED = booleanPreferencesKey("equalizer_enabled")
        val EQUALIZER_BANDS = stringPreferencesKey("equalizer_bands")
        val BLOCK_ADS = booleanPreferencesKey("block_ads")
        val DECODER_MODE = stringPreferencesKey("decoder_mode")
        val KIDS_LOCK_PIN = stringPreferencesKey("kids_lock_pin")
        val VAULT_PIN_HASH = stringPreferencesKey("vault_pin_hash")
        val SPEED_LIMIT_KBPS = intPreferencesKey("speed_limit_kbps")
        val SCHEDULE_ENABLED = booleanPreferencesKey("schedule_enabled")
        val SCHEDULE_START = intPreferencesKey("schedule_start_minute")
        val SCHEDULE_END = intPreferencesKey("schedule_end_minute")
        val PROXY_ENABLED = booleanPreferencesKey("proxy_enabled")
        val PROXY_TYPE = stringPreferencesKey("proxy_type")
        val PROXY_HOST = stringPreferencesKey("proxy_host")
        val PROXY_PORT = intPreferencesKey("proxy_port")
        val PROXY_USERNAME = stringPreferencesKey("proxy_username")
        val PROXY_PASSWORD = stringPreferencesKey("proxy_password")
        val DOWNLOAD_TREE_URI = stringPreferencesKey("download_tree_uri")
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
                blockAds = prefs[Keys.BLOCK_ADS] ?: true,
                decoderMode = DecoderMode.fromStorage(prefs[Keys.DECODER_MODE]),
                kidsLockPin = prefs[Keys.KIDS_LOCK_PIN] ?: "",
                vaultPinHash = prefs[Keys.VAULT_PIN_HASH] ?: "",
                speedLimitKbps = prefs[Keys.SPEED_LIMIT_KBPS] ?: 0,
                scheduleEnabled = prefs[Keys.SCHEDULE_ENABLED] ?: false,
                scheduleStartMinute = prefs[Keys.SCHEDULE_START] ?: 23 * 60,
                scheduleEndMinute = prefs[Keys.SCHEDULE_END] ?: 6 * 60,
                proxyEnabled = prefs[Keys.PROXY_ENABLED] ?: false,
                proxyType = ProxyType.fromStorage(prefs[Keys.PROXY_TYPE]),
                proxyHost = prefs[Keys.PROXY_HOST] ?: "",
                proxyPort = prefs[Keys.PROXY_PORT] ?: 8080,
                proxyUsername = prefs[Keys.PROXY_USERNAME] ?: "",
                proxyHasPassword = !prefs[Keys.PROXY_PASSWORD].isNullOrEmpty(),
                downloadTreeUri = prefs[Keys.DOWNLOAD_TREE_URI] ?: "",
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

    override suspend fun setBlockAds(enabled: Boolean) {
        dataStore.edit { it[Keys.BLOCK_ADS] = enabled }
    }

    override suspend fun setDecoderMode(mode: DecoderMode) {
        dataStore.edit { it[Keys.DECODER_MODE] = mode.name }
    }

    override suspend fun setKidsLockPin(pin: String) {
        dataStore.edit { it[Keys.KIDS_LOCK_PIN] = pin }
    }

    override suspend fun setVaultPinHash(hash: String) {
        dataStore.edit { it[Keys.VAULT_PIN_HASH] = hash }
    }

    override suspend fun setSpeedLimitKbps(kbps: Int) {
        dataStore.edit { it[Keys.SPEED_LIMIT_KBPS] = kbps.coerceAtLeast(0) }
    }

    override suspend fun setDownloadTreeUri(uri: String) {
        dataStore.edit { it[Keys.DOWNLOAD_TREE_URI] = uri }
    }

    override suspend fun setDownloadSchedule(enabled: Boolean, startMinute: Int, endMinute: Int) {
        dataStore.edit {
            it[Keys.SCHEDULE_ENABLED] = enabled
            it[Keys.SCHEDULE_START] = startMinute.coerceIn(0, 24 * 60 - 1)
            it[Keys.SCHEDULE_END] = endMinute.coerceIn(0, 24 * 60 - 1)
        }
    }

    override suspend fun setProxy(
        enabled: Boolean,
        type: ProxyType,
        host: String,
        port: Int,
        username: String,
        password: String,
    ) {
        val secret = if (password.isNotEmpty()) secretBox.encrypt(password) else null
        dataStore.edit {
            it[Keys.PROXY_ENABLED] = enabled
            it[Keys.PROXY_TYPE] = type.name
            it[Keys.PROXY_HOST] = host.trim()
            it[Keys.PROXY_PORT] = port.coerceIn(1, 65535)
            it[Keys.PROXY_USERNAME] = username.trim()
            if (secret != null) it[Keys.PROXY_PASSWORD] = secret
        }
    }

    override suspend fun proxyPassword(): String {
        val stored = dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { it[Keys.PROXY_PASSWORD] ?: "" }
            .first()
        return secretBox.decrypt(stored)
    }
}

@Module
@InstallIn(SingletonComponent::class)
object SettingsModule {

    @Provides
    @Singleton
    fun provideSettingsRepository(@ApplicationContext context: Context): SettingsRepository =
        DataStoreSettingsRepository(context.vortexaDataStore, KeystoreSecretBox())
}
