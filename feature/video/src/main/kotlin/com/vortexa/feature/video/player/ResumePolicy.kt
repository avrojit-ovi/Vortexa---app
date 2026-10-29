package com.vortexa.feature.video.player

/**
 * Rules for remembering where the user stopped.
 *
 * Two guards matter in practice: never offer to "resume" from the first few
 * seconds (it is noise), and never remember a position inside the credits —
 * otherwise a finished video starts again at the last five seconds forever.
 */
object ResumePolicy {

    /** Below this we just start from the beginning. */
    const val MIN_RESUME_MS: Long = 10_000L

    /** Keep a little room at the end so the file can still finish. */
    const val END_GUARD_MS: Long = 10_000L

    /** Do not write to disk for trivial movements. */
    const val MIN_SAVE_MS: Long = 5_000L

    fun shouldResume(positionMs: Long, durationMs: Long): Boolean {
        if (positionMs < MIN_RESUME_MS) return false
        if (durationMs <= 0L) return true
        return positionMs < durationMs - END_GUARD_MS
    }

    fun shouldPersist(positionMs: Long, durationMs: Long): Boolean {
        if (positionMs < MIN_SAVE_MS) return false
        if (durationMs <= 0L) return true
        return positionMs < durationMs - END_GUARD_MS
    }

    /** Clamps a remembered position into a range the player accepts. */
    fun clamped(positionMs: Long, durationMs: Long): Long {
        if (positionMs <= 0L) return 0L
        if (durationMs <= 0L) return positionMs
        return positionMs.coerceAtMost(durationMs)
    }
}
