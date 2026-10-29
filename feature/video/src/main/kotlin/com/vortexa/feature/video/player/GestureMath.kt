package com.vortexa.feature.video.player

import kotlin.math.abs
import kotlin.math.sign

/** Which axis a drag locked onto. */
enum class DragAxis { NONE, HORIZONTAL, VERTICAL }

/**
 * Gesture arithmetic for the player, kept free of Android types so the rules
 * (how far a swipe seeks, how much a vertical drag changes brightness, what
 * happens at the edges) are tested rather than tuned by feel on one phone.
 */
object GestureMath {

    /** A full-width horizontal swipe covers this much of the video. */
    const val FULL_SWIPE_SEEK_FRACTION = 0.5

    /** A full-height vertical swipe changes brightness/volume by this much. */
    const val FULL_SWIPE_LEVEL_CHANGE = 1.0

    private const val LOCK_THRESHOLD_PX = 12f

    fun axis(dx: Float, dy: Float): DragAxis = when {
        abs(dx) < LOCK_THRESHOLD_PX && abs(dy) < LOCK_THRESHOLD_PX -> DragAxis.NONE
        abs(dx) >= abs(dy) -> DragAxis.HORIZONTAL
        else -> DragAxis.VERTICAL
    }

    /**
     * Where a horizontal swipe of [dragFraction] of the screen width lands.
     * Duration 0 (live stream) never seeks.
     */
    fun seekTarget(positionMs: Long, durationMs: Long, dragFraction: Float): Long {
        if (durationMs <= 0L) return positionMs
        val delta = (dragFraction * FULL_SWIPE_SEEK_FRACTION * durationMs).toLong()
        return (positionMs + delta).coerceIn(0L, durationMs)
    }

    /** New 0..1 level after an upward drag (positive [dragFraction]) of the screen height. */
    fun levelAfter(current: Float, dragFraction: Float): Float =
        (current + dragFraction * FULL_SWIPE_LEVEL_CHANGE.toFloat()).coerceIn(0f, 1f)

    /** Signed seconds for the on-screen hint, e.g. "+42s" / "-1:05". */
    fun seekDeltaLabel(deltaMs: Long): String {
        if (deltaMs == 0L) return "0s"
        val sign = if (deltaMs > 0) "+" else "-"
        val totalSeconds = abs(deltaMs) / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return if (minutes > 0) {
            String.format(java.util.Locale.US, "%s%d:%02d", sign, minutes, seconds)
        } else {
            "%s%ds".format(sign, seconds)
        }
    }

    /** Brightness feels natural as a step per swipe, never as a jump. */
    fun brightnessAfter(current: Float, dragFraction: Float): Float {
        val base = if (current < 0f) 0.5f else current
        return levelAfter(base, dragFraction)
    }

    fun volumeIndexAfter(current: Int, max: Int, dragFraction: Float): Int {
        if (max <= 0) return current
        val target = current + (dragFraction * max).toInt()
        return target.coerceIn(0, max)
    }

    /** True when the drag started on the left half of the screen. */
    fun sideOf(x: Float, width: Int): Boolean = sign(x - width / 2f) < 0
}
