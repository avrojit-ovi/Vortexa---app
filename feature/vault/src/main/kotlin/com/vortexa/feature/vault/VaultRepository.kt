package com.vortexa.feature.vault

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.vortexa.feature.vault.crypto.VaultCipher
import com.vortexa.feature.vault.crypto.VaultException
import com.vortexa.feature.vault.crypto.VaultKeyProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.KeyStore
import java.util.UUID
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A file inside the vault, as the list shows it. */
data class VaultFile(
    val id: String,
    val name: String,
    val sizeBytes: Long,
)

/**
 * The vault on disk: opaque `.vxv` blobs under `filesDir/vault`, named by random
 * id so the folder itself does not leak what is inside.
 */
@Singleton
class VaultRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val cipher = VaultCipher(KeystoreKeyProvider())

    private val vaultDir: File
        get() = File(context.filesDir, VAULT_DIR).apply { if (!exists()) mkdirs() }

    private val previewDir: File
        get() = File(context.cacheDir, PREVIEW_DIR).apply { if (!exists()) mkdirs() }

    suspend fun list(): List<VaultFile> = withContext(Dispatchers.IO) {
        vaultDir.listFiles { file -> file.isFile && file.name.endsWith(EXTENSION) }
            .orEmpty()
            .map { file ->
                val name = runCatching {
                    file.inputStream().use { cipher.readName(it) }
                }.getOrElse { file.nameWithoutExtension }
                VaultFile(
                    id = file.nameWithoutExtension,
                    name = name,
                    sizeBytes = file.length(),
                )
            }
            .sortedBy { it.name.lowercase() }
    }

    suspend fun import(source: InputStream, displayName: String): VaultFile =
        withContext(Dispatchers.IO) {
            val id = UUID.randomUUID().toString()
            val part = File(vaultDir, "$id.part")
            val name = displayName.trim().ifEmpty { "vault-file" }.take(MAX_NAME_LENGTH)
            try {
                FileOutputStream(part).use { output -> cipher.encrypt(source, output, name) }
                val target = File(vaultDir, "$id$EXTENSION")
                if (!part.renameTo(target)) throw VaultException("Could not store the file")
                VaultFile(id = id, name = name, sizeBytes = target.length())
            } catch (error: Throwable) {
                part.delete()
                throw error
            }
        }

    /** Decrypts into the cache for the player; the previous preview is dropped first. */
    suspend fun decryptToPreview(id: String): Pair<File, String> = withContext(Dispatchers.IO) {
        val source = File(vaultDir, "$id$EXTENSION")
        if (!source.isFile) throw VaultException("That file is no longer in the vault")
        previewDir.listFiles()?.forEach { it.delete() }
        val target = File(previewDir, "preview-${id.take(8)}")
        val name = FileInputStream(source).use { input ->
            FileOutputStream(target).use { output -> cipher.decrypt(input, output) }
        }
        target to name
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        File(vaultDir, "$id$EXTENSION").delete()
    }

    private companion object {
        const val VAULT_DIR = "vault"
        const val PREVIEW_DIR = "vault-preview"
        const val EXTENSION = ".vxv"
        const val MAX_NAME_LENGTH = 120
    }
}

/** AES-256 key that never leaves the device's Keystore. */
class KeystoreKeyProvider : VaultKeyProvider {

    override fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "vortexa.vault"
    }
}
