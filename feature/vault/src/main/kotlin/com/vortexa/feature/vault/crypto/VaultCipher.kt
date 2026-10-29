package com.vortexa.feature.vault.crypto

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Where the AES key comes from: the Android Keystore in the app, a plain key in tests. */
fun interface VaultKeyProvider {
    fun key(): SecretKey
}

class VaultException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * Streaming AES-256-GCM container for vault files.
 *
 * Layout: `VXV1` magic, then the file name encrypted under its own IV, then the
 * body encrypted under another IV. Two separate GCM blobs let the list show real
 * names without decrypting whole files, and both halves are authenticated, so a
 * flipped byte is reported instead of quietly serving different content.
 */
class VaultCipher(private val keys: VaultKeyProvider) {

    private val random = SecureRandom()

    fun encrypt(source: InputStream, sink: OutputStream, name: String) {
        val nameBytes = name.toByteArray(Charsets.UTF_8)
        if (nameBytes.isEmpty() || nameBytes.size > MAX_NAME_BYTES) {
            throw VaultException("The file name cannot be stored")
        }

        sink.write(MAGIC)
        val nameIv = newIv()
        val encryptedName = try {
            cipher(Cipher.ENCRYPT_MODE, nameIv).doFinal(nameBytes)
        } catch (error: Exception) {
            throw VaultException("Could not encrypt the file name", error)
        }
        sink.write(nameIv)
        writeInt(sink, encryptedName.size)
        sink.write(encryptedName)

        val bodyIv = newIv()
        sink.write(bodyIv)
        val body = cipher(Cipher.ENCRYPT_MODE, bodyIv)
        try {
            pump(source, sink, body)
        } catch (error: Exception) {
            throw VaultException("Could not encrypt the file", error)
        }
    }

    /** Reads just the name; the body is left untouched in [source]. */
    fun readName(source: InputStream): String {
        requireMagic(source)
        return readNameBlock(source)
    }

    /** Decrypts everything, returning the original file name. */
    fun decrypt(source: InputStream, sink: OutputStream): String {
        val name = readName(source)
        val bodyIv = readFully(source, IV_BYTES)
        val body = cipher(Cipher.DECRYPT_MODE, bodyIv)
        try {
            pump(source, sink, body)
        } catch (error: Exception) {
            // GCM verifies its tag in doFinal, so a tampered file lands here
            // instead of producing a readable-but-wrong copy.
            throw VaultException("This vault file is damaged or was tampered with", error)
        }
        return name
    }

    private fun readNameBlock(source: InputStream): String {
        val nameIv = readFully(source, IV_BYTES)
        val length = readInt(source)
        if (length <= 0 || length > MAX_NAME_BYTES + TAG_BYTES) {
            throw VaultException("This vault file is not readable")
        }
        val encrypted = readFully(source, length)
        val plain = try {
            cipher(Cipher.DECRYPT_MODE, nameIv).doFinal(encrypted)
        } catch (error: Exception) {
            throw VaultException("This vault file is damaged or was tampered with", error)
        }
        return String(plain, Charsets.UTF_8)
    }

    private fun requireMagic(source: InputStream) {
        val magic = readFully(source, MAGIC.size)
        if (!magic.contentEquals(MAGIC)) throw VaultException("Not a Vortexa vault file")
    }

    private fun cipher(mode: Int, iv: ByteArray): Cipher =
        Cipher.getInstance(TRANSFORMATION).apply {
            init(mode, keys.key(), GCMParameterSpec(TAG_BITS, iv))
        }

    private fun pump(source: InputStream, sink: OutputStream, cipher: Cipher) {
        val buffer = ByteArray(BUFFER_BYTES)
        while (true) {
            val read = source.read(buffer)
            if (read < 0) break
            val chunk = cipher.update(buffer, 0, read) ?: continue
            if (chunk.isNotEmpty()) sink.write(chunk)
        }
        val tail = cipher.doFinal()
        if (tail.isNotEmpty()) sink.write(tail)
    }

    private fun newIv(): ByteArray = ByteArray(IV_BYTES).also(random::nextBytes)

    private fun readFully(source: InputStream, size: Int): ByteArray {
        val bytes = ByteArray(size)
        var offset = 0
        while (offset < size) {
            val read = source.read(bytes, offset, size - offset)
            if (read < 0) throw VaultException("This vault file ends too early")
            offset += read
        }
        return bytes
    }

    private fun readInt(source: InputStream): Int {
        val raw = readFully(source, 4)
        return ((raw[0].toInt() and 0xFF) shl 24) or
            ((raw[1].toInt() and 0xFF) shl 16) or
            ((raw[2].toInt() and 0xFF) shl 8) or
            (raw[3].toInt() and 0xFF)
    }

    private fun writeInt(sink: OutputStream, value: Int) {
        sink.write(
            byteArrayOf(
                (value ushr 24).toByte(),
                (value ushr 16).toByte(),
                (value ushr 8).toByte(),
                value.toByte(),
            ),
        )
    }

    private companion object {
        val MAGIC = byteArrayOf(0x56, 0x58, 0x56, 0x31) // "VXV1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
        const val TAG_BYTES = 16
        const val MAX_NAME_BYTES = 4_096
        const val BUFFER_BYTES = 64 * 1024
    }
}
