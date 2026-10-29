package com.vortexa.feature.video.player

import java.util.Locale

/**
 * Subtitle cues with a user-controlled delay.
 *
 * Media3 hands cues over at the moment they become active and offers no hook to
 * shift them, so the player keeps the recent cue updates and decides which one
 * belongs on screen. Positive delay is exact; a negative delay cannot be
 * honoured without the whole file, so the range starts at zero.
 */
class SubtitleTimeline(private val maxEmissions: Int = 64) {

    private data class Emission(val atMillis: Long, val lines: List<String>)

    private val emissions = ArrayDeque<Emission>()

    @Synchronized
    fun push(lines: List<String>, atMillis: Long) {
        emissions.addLast(Emission(atMillis, lines))
        while (emissions.size > maxEmissions) emissions.removeFirst()
    }

    @Synchronized
    fun visible(nowMillis: Long, delayMillis: Long): List<String> {
        var current: List<String> = emptyList()
        for (emission in emissions) {
            if (emission.atMillis + delayMillis <= nowMillis) current = emission.lines
        }
        return current
    }

    @Synchronized
    fun clear() {
        emissions.clear()
    }
}

/** Delay bounds and labels, shared by the slider and its tests. */
object SubtitleDelay {

    const val MIN_SECONDS: Float = 0f
    const val MAX_SECONDS: Float = 10f
    const val STEP_SECONDS: Float = 0.5f

    fun clamp(seconds: Float): Float = seconds.coerceIn(MIN_SECONDS, MAX_SECONDS)

    fun step(seconds: Float, delta: Float): Float = clamp(seconds + delta)

    /** "+0.5s", "+2.0s", "0.0s" — always one decimal so the label does not jump. */
    fun label(seconds: Float): String {
        val value = clamp(seconds)
        val sign = if (value > 0f) "+" else ""
        return String.format(Locale.US, "%s%.1fs", sign, value)
    }
}

/** Three steps are enough to read subtitles from a sofa or across a room. */
enum class SubtitleSize(val step: Int, val sizeSp: Int, val label: String) {
    SMALL(0, 14, "S"),
    MEDIUM(1, 18, "M"),
    LARGE(2, 22, "L"),
    ;

    companion object {
        fun fromStep(step: Int): SubtitleSize = entries.firstOrNull { it.step == step } ?: MEDIUM
    }
}
