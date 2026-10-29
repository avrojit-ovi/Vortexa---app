package com.vortexa.core.common.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts the few secrets that must survive a restart (proxy password today)
 * with an AES-256-GCM key that never leaves the Android Keystore.
 *
 * The ciphertext is Base64 of `iv || body`, so it can sit in DataStore next to
 * ordinary settings without ever writing the secret in clear. A keystore entry
 * that has been invalidated (restored backup, key reset) decrypts to an empty
 * string instead of crashing the downloader — the user just re-enters the
 * password.
 */
class KeystoreSecretBox(private val alias: String = DEFAULT_ALIAS) {

    fun encrypt(plain: String): String {
        if (plain.isEmpty()) return ""
        return synchronized(lock) {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val body = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(cipher.iv + body, Base64.NO_WRAP)
        }
    }

    /** Returns "" for empty, corrupt or invalidated ciphertext. */
    fun decrypt(stored: String): String {
        if (stored.isEmpty()) return ""
        return runCatching {
            val blob = Base64.decode(stored, Base64.NO_WRAP)
            require(blob.size > IV_BYTES) { "Ciphertext is too short" }
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                key(),
                GCMParameterSpec(TAG_BITS, blob.copyOfRange(0, IV_BYTES)),
            )
            String(cipher.doFinal(blob.copyOfRange(IV_BYTES, blob.size)), Charsets.UTF_8)
        }.getOrDefault("")
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
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
        const val KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val DEFAULT_ALIAS = "vortexa.proxy.secret"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}

private val lock = Any()
