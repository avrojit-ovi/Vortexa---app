package com.vortexa.feature.video.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KidsLockTest {

    @Test
    fun `only four digits count as a pin`() {
        assertTrue(KidsLock.isValidPin("1234"))
        assertTrue(KidsLock.isValidPin("0000"))
        assertFalse(KidsLock.isValidPin("123"))
        assertFalse(KidsLock.isValidPin("12345"))
        assertFalse(KidsLock.isValidPin("12a4"))
        assertFalse(KidsLock.isValidPin(""))
    }

    @Test
    fun `an unset pin never unlocks`() {
        assertFalse(KidsLock.matches(stored = "", entered = ""))
        assertFalse(KidsLock.matches(stored = "", entered = "1234"))
    }

    @Test
    fun `the entered pin must match exactly`() {
        assertTrue(KidsLock.matches(stored = "2468", entered = "2468"))
        assertFalse(KidsLock.matches(stored = "2468", entered = "2469"))
    }
}
