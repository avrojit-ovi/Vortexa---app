package com.vortexa.feature.vault

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vortexa.core.common.format.Formatters
import com.vortexa.core.ui.component.PhaseBadge
import com.vortexa.feature.vault.crypto.PinHash

/**
 * The private vault: a PIN-gated list of files that are stored encrypted on
 * disk. Opening one decrypts it into the cache for the player; nothing is ever
 * left decrypted next to the originals.
 */
@Composable
fun VaultScreen(
    modifier: Modifier = Modifier,
    onOpenMedia: (path: String, title: String) -> Unit = { _, _ -> },
    viewModel: VaultViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var pin by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }

    val importer = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) viewModel.import(uri) }

    val messageText = when (state.message) {
        VaultMessage.Imported -> stringResource(R.string.vault_imported)
        VaultMessage.ImportFailed -> stringResource(R.string.vault_import_failed)
        VaultMessage.Deleted -> stringResource(R.string.vault_deleted)
        VaultMessage.WrongPin -> stringResource(R.string.vault_wrong_pin)
        VaultMessage.PinMismatch -> stringResource(R.string.vault_pin_mismatch)
        VaultMessage.PinInvalid -> stringResource(R.string.vault_pin_invalid)
        VaultMessage.OpenFailed -> stringResource(R.string.vault_open_failed)
        null -> null
    }

    LaunchedEffect(messageText) {
        val text = messageText ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        viewModel.consumeMessage()
    }

    LaunchedEffect(state.pendingOpen) {
        val opening = state.pendingOpen ?: return@LaunchedEffect
        onOpenMedia(opening.path, opening.name)
        viewModel.consumeOpen()
    }

    LaunchedEffect(state.unlocked) {
        if (state.unlocked) {
            pin = ""
            confirmation = ""
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (state.unlocked) {
            UnlockedVault(
                state = state,
                onImport = { importer.launch(arrayOf("*/*")) },
                onOpen = viewModel::open,
                onDelete = viewModel::delete,
                onLock = viewModel::lock,
            )
        } else {
            LockedVault(
                pinSet = state.pinSet,
                pin = pin,
                confirmation = confirmation,
                onPinChange = { pin = it },
                onConfirmationChange = { confirmation = it },
                onSetPin = { viewModel.setPin(pin, confirmation) },
                onUnlock = { viewModel.unlock(pin) },
                onBiometric = {
                    BiometricUnlock.authenticate(
                        context = context,
                        title = "Unlock the vault",
                        cancelLabel = "Cancel",
                        onSuccess = viewModel::unlockWithBiometrics,
                        onFailure = {},
                    )
                },
                biometricAvailable = BiometricUnlock.isAvailable(context),
            )
        }

        if (state.busy) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun LockedVault(
    pinSet: Boolean,
    pin: String,
    confirmation: String,
    onPinChange: (String) -> Unit,
    onConfirmationChange: (String) -> Unit,
    onSetPin: () -> Unit,
    onUnlock: () -> Unit,
    onBiometric: () -> Unit,
    biometricAvailable: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(com.vortexa.core.ui.R.drawable.ic_tab_vault),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(
                if (pinSet) R.string.vault_locked_title else R.string.vault_set_pin_title,
            ),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(
                if (pinSet) R.string.vault_locked_body else R.string.vault_set_pin_body,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(24.dp))

        PinField(
            value = pin,
            label = stringResource(R.string.vault_pin),
            onValueChange = onPinChange,
        )

        if (!pinSet) {
            Spacer(modifier = Modifier.height(12.dp))
            PinField(
                value = confirmation,
                label = stringResource(R.string.vault_confirm_pin),
                onValueChange = onConfirmationChange,
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = if (pinSet) onUnlock else onSetPin,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(
                    if (pinSet) R.string.vault_unlock else R.string.vault_set_pin_action,
                ),
            )
        }

        if (pinSet && biometricAvailable) {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(onClick = onBiometric, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(R.string.vault_use_biometric))
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.vault_encrypted_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(12.dp))
        PhaseBadge(text = stringResource(R.string.vault_recovery_note))
    }
}

@Composable
private fun UnlockedVault(
    state: VaultUiState,
    onImport: () -> Unit,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
    onLock: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.vault_files, state.entries.size),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.vault_size, Formatters.bytes(state.totalBytes)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onImport) {
                Text(text = stringResource(R.string.vault_import))
            }
            TextButton(onClick = onLock) {
                Text(text = stringResource(R.string.vault_lock))
            }
        }

        if (state.entries.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(R.string.vault_empty_title),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.vault_empty_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.entries, key = { it.id }) { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpen(entry.id) }
                            .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = entry.name,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = Formatters.bytes(entry.sizeBytes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { onDelete(entry.id) }) {
                            Icon(
                                painter = painterResource(com.vortexa.core.ui.R.drawable.ic_action_delete),
                                contentDescription = stringResource(R.string.vault_delete),
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun PinField(
    value: String,
    label: String,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input ->
            if (input.length <= PinHash.MAX_LENGTH && input.all { it in '0'..'9' }) {
                onValueChange(input)
            }
        },
        label = { Text(text = label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )
}
