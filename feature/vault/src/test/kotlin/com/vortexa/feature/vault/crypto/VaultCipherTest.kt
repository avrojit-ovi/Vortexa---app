package com.vortexa.feature.vault.crypto

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class VaultCipherTest {

    private val cipher = VaultCipher {
        SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")
    }

    private fun roundTrip(name: String, payload: ByteArray): ByteArray {
        val container = ByteArrayOutputStream()
        cipher.encrypt(ByteArrayInputStream(payload), container, name)

        val restored = ByteArrayOutputStream()
        val storedName = cipher.decrypt(ByteArrayInputStream(container.toByteArray()), restored)

        assertEquals(name, storedName)
        return restored.toByteArray()
    }

    @Test
    fun `a file survives the round trip with its name`() {
        val payload = "Vortexa vault payload".toByteArray()
        assertArrayEquals(payload, roundTrip("clip.mp4", payload))
    }

    @Test
    fun `an empty file is valid`() {
        assertArrayEquals(ByteArray(0), roundTrip("empty.txt", ByteArray(0)))
    }

    @Test
    fun `a multi chunk file is streamed intact`() {
        val payload = ByteArray(300_000) { (it % 251).toByte() }
        assertArrayEquals(payload, roundTrip("big.mkv", payload))
    }

    @Test
    fun `unicode names come back unchanged`() {
        assertArrayEquals("x".toByteArray(), roundTrip("নোট - ২০২৬.txt", "x".toByteArray()))
    }

    @Test
    fun `the stored bytes do not contain the plaintext`() {
        val container = ByteArrayOutputStream()
        cipher.encrypt(ByteArrayInputStream("secret-words".toByteArray()), container, "notes.txt")
        val stored = container.toString(Charsets.ISO_8859_1)
        assertTrue(!stored.contains("secret-words"))
        assertTrue(!stored.contains("notes.txt"))
    }

    @Test
    fun `the name can be read without decrypting the body`() {
        val container = ByteArrayOutputStream()
        cipher.encrypt(ByteArrayInputStream(ByteArray(1024)), container, "movie.mp4")
        val name = cipher.readName(ByteArrayInputStream(container.toByteArray()))
        assertEquals("movie.mp4", name)
    }

    @Test
    fun `a tampered body is rejected instead of served`() {
        val container = ByteArrayOutputStream()
        cipher.encrypt(ByteArrayInputStream("original".toByteArray()), container, "a.txt")
        val bytes = container.toByteArray()
        bytes[bytes.size - 1] = (bytes[bytes.size - 1] + 1).toByte()

        try {
            cipher.decrypt(ByteArrayInputStream(bytes), ByteArrayOutputStream())
            fail("A flipped byte must not decrypt")
        } catch (expected: VaultException) {
            assertTrue(expected.message!!.contains("tampered"))
        }
    }

    @Test
    fun `a foreign file is refused by the magic`() {
        try {
            cipher.readName(ByteArrayInputStream("not a vault".toByteArray()))
            fail("Foreign bytes must be refused")
        } catch (expected: VaultException) {
            assertTrue(expected.message!!.contains("Not a Vortexa vault file"))
        }
    }

    @Test
    fun `an absurd name length cannot exhaust memory`() {
        val header = ByteArrayOutputStream()
        header.write(byteArrayOf(0x56, 0x58, 0x56, 0x31))
        header.write(ByteArray(12))
        header.write(byteArrayOf(0x7F, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()))

        try {
            cipher.readName(ByteArrayInputStream(header.toByteArray()))
            fail("A huge length must be rejected before reading")
        } catch (expected: VaultException) {
            assertTrue(expected.message!!.contains("not readable"))
        }
    }
}
