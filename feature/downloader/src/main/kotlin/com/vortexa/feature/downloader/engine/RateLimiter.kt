package com.vortexa.feature.downloader.engine

/**
 * A shared byte budget for every segment of a download.
 *
 * Each chunk pays for itself: if the bytes so far would have taken longer than
 * the time actually elapsed, the difference is slept away. One instance is
 * shared by all ranges, so the cap is per download, not per connection.
 *
 * The clock and the sleep are injected so the arithmetic can be tested without
 * waiting in real time.
 */
class RateLimiter(
    private val bytesPerSecond: Long,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
    private val sleeper: (Long) -> Unit = { millis -> if (millis > 0) Thread.sleep(millis) },
) {

    private var startedAt = clock()
    private var bytesSoFar = 0L

    val isLimited: Boolean get() = bytesPerSecond > 0L

    @Synchronized
    fun acquire(bytes: Int) {
        if (bytesPerSecond <= 0L || bytes <= 0) return
        bytesSoFar += bytes
        if (bytesSoFar == 1L) startedAt = clock()

        val requiredMillis = bytesSoFar * 1000L / bytesPerSecond
        val elapsed = clock() - startedAt
        val wait = requiredMillis - elapsed
        if (wait > 0L) sleeper(wait)
    }

    companion object {
        /** 0 means "no limit", which is what the setting sends when it is off. */
        fun unlimited(): RateLimiter = RateLimiter(0L)
    }
}
