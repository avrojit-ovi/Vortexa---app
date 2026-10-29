package com.vortexa.feature.vault

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vortexa.core.common.preferences.SettingsRepository
import com.vortexa.feature.vault.crypto.PinHash
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface VaultMessage {
    data object Imported : VaultMessage
    data object ImportFailed : VaultMessage
    data object Deleted : VaultMessage
    data object WrongPin : VaultMessage
    data object PinMismatch : VaultMessage
    data object PinInvalid : VaultMessage
    data object OpenFailed : VaultMessage
}

data class VaultOpening(val path: String, val name: String)

data class VaultUiState(
    val pinSet: Boolean = false,
    val unlocked: Boolean = false,
    val entries: List<VaultFile> = emptyList(),
    val busy: Boolean = false,
    val message: VaultMessage? = null,
    val pendingOpen: VaultOpening? = null,
    val totalBytes: Long = 0L,
)

@HiltViewModel
class VaultViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: VaultRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(VaultUiState())
    val state: StateFlow<VaultUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val stored = settings.preferences.first().vaultPinHash
            _state.update { it.copy(pinSet = stored.isNotEmpty()) }
        }
    }

    fun setPin(pin: String, confirmation: String) {
        when {
            !PinHash.isValidPin(pin) -> _state.update { it.copy(message = VaultMessage.PinInvalid) }
            pin != confirmation -> _state.update { it.copy(message = VaultMessage.PinMismatch) }
            else -> viewModelScope.launch {
                settings.setVaultPinHash(PinHash.create(pin))
                _state.update { it.copy(pinSet = true, unlocked = true) }
                refresh()
            }
        }
    }

    fun unlock(pin: String) {
        viewModelScope.launch {
            val stored = settings.preferences.first().vaultPinHash
            if (!PinHash.verify(stored, pin)) {
                _state.update { it.copy(message = VaultMessage.WrongPin) }
                return@launch
            }
            _state.update { it.copy(unlocked = true) }
            refresh()
        }
    }

    /** Called after the system prompt reports success; the PIN must still exist. */
    fun unlockWithBiometrics() {
        viewModelScope.launch {
            val stored = settings.preferences.first().vaultPinHash
            if (stored.isEmpty()) return@launch
            _state.update { it.copy(unlocked = true) }
            refresh()
        }
    }

    fun lock() {
        _state.update { it.copy(unlocked = false, entries = emptyList(), totalBytes = 0L) }
    }

    fun import(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            val entry = runCatching {
                val name = queryDisplayName(uri) ?: "vault-file"
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    repository.import(stream, name)
                } ?: error("Could not read that file")
            }.getOrNull()
            _state.update {
                it.copy(busy = false, message = if (entry != null) VaultMessage.Imported else VaultMessage.ImportFailed)
            }
            refresh()
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            repository.delete(id)
            _state.update { it.copy(message = VaultMessage.Deleted) }
            refresh()
        }
    }

    fun open(id: String) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            val result = runCatching { repository.decryptToPreview(id) }.getOrNull()
            _state.update { current ->
                current.copy(
                    busy = false,
                    message = if (result == null) VaultMessage.OpenFailed else null,
                    pendingOpen = result?.let { (file, name) -> VaultOpening(file.absolutePath, name) },
                )
            }
        }
    }

    fun consumeOpen() {
        _state.update { it.copy(pendingOpen = null) }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    private suspend fun refresh() {
        val entries = withContext(Dispatchers.IO) { repository.list() }
        _state.update {
            it.copy(entries = entries, totalBytes = entries.sumOf(VaultFile::sizeBytes))
        }
    }

    private suspend fun queryDisplayName(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver
                .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        }.getOrNull()
    }
}
