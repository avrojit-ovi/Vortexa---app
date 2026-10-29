package com.vortexa.feature.vault.crypto

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinHashTest {

    private val salt = ByteArray(16) { it.toByte() }

    @Test
    fun `the right pin verifies`() {
        val stored = PinHash.create("2468", salt = salt, iterations = 1_000)
        assertTrue(PinHash.verify(stored, "2468"))
    }

    @Test
    fun `a wrong pin does not`() {
        val stored = PinHash.create("2468", salt = salt, iterations = 1_000)
        assertFalse(PinHash.verify(stored, "2469"))
        assertFalse(PinHash.verify(stored, ""))
    }

    @Test
    fun `the pin itself is not in the stored value`() {
        val stored = PinHash.create("1357", salt = salt, iterations = 1_000)
        assertFalse(stored.contains("1357"))
        assertTrue(stored.startsWith("v1$"))
    }

    @Test
    fun `a different salt gives a different hash for the same pin`() {
        val first = PinHash.create("1111", salt = ByteArray(16) { 1 }, iterations = 1_000)
        val second = PinHash.create("1111", salt = ByteArray(16) { 2 }, iterations = 1_000)
        assertNotEquals(first, second)
        assertTrue(PinHash.verify(first, "1111"))
        assertTrue(PinHash.verify(second, "1111"))
    }

    @Test
    fun `malformed stored values never match`() {
        assertFalse(PinHash.verify("", "1234"))
        assertFalse(PinHash.verify("nonsense", "1234"))
        assertFalse(PinHash.verify("v1\$0\$AAAA\$AAAA", "1234"))
        assertFalse(PinHash.verify("v1\$1000\$!!!\$!!!", "1234"))
        assertFalse(PinHash.verify("v2\$1000\$AAAA\$AAAA", "1234"))
    }

    @Test
    fun `pins are four to twelve digits`() {
        assertTrue(PinHash.isValidPin("1234"))
        assertTrue(PinHash.isValidPin("123456789012"))
        assertFalse(PinHash.isValidPin("123"))
        assertFalse(PinHash.isValidPin("1234567890123"))
        assertFalse(PinHash.isValidPin("12 34"))
        assertFalse(PinHash.isValidPin("abcd"))
    }
}
