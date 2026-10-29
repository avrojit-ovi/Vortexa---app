package com.vortexa.feature.downloader.engine

import java.io.File
import java.security.MessageDigest

/**
 * Streaming hashes. Used for the "verify checksum" setting; the Rust core
 * exposes the same algorithms for large files once the JNI bridge is wired.
 */
object Checksums {

    fun sha256(file: File): String = digest(file, "SHA-256")

    fun md5(file: File): String = digest(file, "MD5")

    fun sha1(file: File): String = digest(file, "SHA-1")

    private fun digest(file: File, algorithm: String): String {
        val digest = MessageDigest.getInstance(algorithm)
        file.inputStream().buffered(64 * 1024).use { stream ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = stream.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
