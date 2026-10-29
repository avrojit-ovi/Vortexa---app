package com.vortexa.feature.vault.crypto

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * A PIN is never stored, only a salted PBKDF2 hash of it.
 *
 * The vault's encryption key lives in the Keystore, so this hash guards the UI,
 * not the files; it still deserves to be a real KDF rather than a plain digest.
 */
object PinHash {

    private const val PREFIX = "v1"
    private const val ITERATIONS = 120_000
    private const val SALT_BYTES = 16
    private const val HASH_BITS = 256
    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 12

    fun isValidPin(pin: String): Boolean =
        pin.length in MIN_LENGTH..MAX_LENGTH && pin.all { it in '0'..'9' }

    fun create(pin: String, salt: ByteArray = newSalt(), iterations: Int = ITERATIONS): String {
        val hash = derive(pin, salt, iterations)
        return listOf(
            PREFIX,
            iterations.toString(),
            Base64.getEncoder().encodeToString(salt),
            Base64.getEncoder().encodeToString(hash),
        ).joinToString("$")
    }

    /** A malformed or empty stored value never matches. */
    fun verify(stored: String, pin: String): Boolean {
        val parts = stored.split('$')
        if (parts.size != 4 || parts[0] != PREFIX) return false
        val iterations = parts[1].toIntOrNull()?.takeIf { it > 0 } ?: return false
        val salt = decode(parts[2]) ?: return false
        val expected = decode(parts[3]) ?: return false
        val actual = derive(pin, salt, iterations)
        return MessageDigest.isEqual(expected, actual)
    }

    private fun derive(pin: String, salt: ByteArray, iterations: Int): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(pin.toCharArray(), salt, iterations, HASH_BITS))
            .encoded

    private fun decode(value: String): ByteArray? =
        runCatching { Base64.getDecoder().decode(value) }.getOrNull()

    private fun newSalt(): ByteArray = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
}
