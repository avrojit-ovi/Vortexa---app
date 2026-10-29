package com.vortexa.feature.downloader.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RateLimiterTest {

    private var now = 0L
    private val slept = mutableListOf<Long>()

    private fun limiter(bytesPerSecond: Long) = RateLimiter(
        bytesPerSecond = bytesPerSecond,
        clock = { now },
        sleeper = { millis ->
            slept += millis
            now += millis
        },
    )

    @Test
    fun `a zero limit never sleeps`() {
        val limiter = limiter(0)
        assertFalse(limiter.isLimited)
        limiter.acquire(1_000_000)
        assertTrue(slept.isEmpty())
    }

    @Test
    fun `the first chunk pays for its own bytes`() {
        val limiter = limiter(1_000)
        limiter.acquire(500)
        assertEquals(listOf(500L), slept)
    }

    @Test
    fun `time already spent is not slept twice`() {
        val limiter = limiter(1_000)
        limiter.acquire(500)
        now += 400          // 400 ms of real work happened
        limiter.acquire(500)
        // 1000 bytes need 1000 ms and 900 ms have passed, so only 100 is owed.
        assertEquals(100L, slept.last())
        assertEquals(600L, slept.sum())
    }

    @Test
    fun `a download that is keeping up does not sleep`() {
        val limiter = limiter(1_000)
        limiter.acquire(100)
        now += 10_000       // a slow read, way behind the budget
        limiter.acquire(100)
        assertEquals(1, slept.size)
    }

    @Test
    fun `the cap holds across many chunks`() {
        val limiter = limiter(64 * 1024)
        repeat(16) { limiter.acquire(64 * 1024) }
        // 16 chunks of 64 KB at 64 KB/s is 16 seconds of budget.
        assertEquals(16_000L, slept.sum())
    }

    @Test
    fun `unlimited is a no-op instance`() {
        assertFalse(RateLimiter.unlimited().isLimited)
    }
}
