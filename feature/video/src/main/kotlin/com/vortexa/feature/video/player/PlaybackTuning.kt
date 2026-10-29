package com.vortexa.feature.video.player

import java.util.Locale

/** Playback speed presets, cycled by one button. */
object SpeedPresets {

    val values: List<Float> = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

    const val DEFAULT: Float = 1f

    /** Next preset after [current]; wraps back to 0.5x after 2x. */
    fun next(current: Float): Float {
        val epsilon = 0.001f
        return values.firstOrNull { it > current + epsilon } ?: values.first()
    }

    /** "1x", "0.75x", "1.5x" — trailing zeros trimmed. */
    fun label(speed: Float): String {
        val trimmed = if (speed == speed.toLong().toFloat()) {
            speed.toInt().toString()
        } else {
            String.format(Locale.US, "%.2f", speed).trimEnd('0').trimEnd('.')
        }
        return "${trimmed}x"
    }
}

/** Position/duration labels as a media player shows them: 0:07, 12:34, 1:02:03. */
object PlaybackClock {

    fun format(positionMs: Long): String {
        if (positionMs <= 0L) return "0:00"
        val totalSeconds = positionMs / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, seconds)
        }
    }
}
