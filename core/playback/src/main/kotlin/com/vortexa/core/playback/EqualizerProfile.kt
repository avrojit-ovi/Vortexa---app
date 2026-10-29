package com.vortexa.core.playback

/**
 * Equalizer maths with no Android types, so the awkward parts (parsing what was
 * saved, clamping to what the device supports, matching bands to a preset) are
 * unit tested instead of discovered on a phone.
 *
 * Gains are millibels, the unit `android.media.audiofx.Equalizer` uses:
 * -1500 .. +1500, i.e. -15 dB .. +15 dB.
 */
object EqualizerProfile {

    const val BAND_COUNT = 5
    const val MIN_LEVEL_MB = -1500
    const val MAX_LEVEL_MB = 1500
    val LEVELS_MB = listOf(-1500, -1000, -500, 0, 500, 1000, 1500)

    data class Preset(val name: String, val gains: List<Int>)

    /** Deliberately small, honest presets rather than a wall of fake names. */
    val presets: List<Preset> = listOf(
        Preset("Flat", listOf(0, 0, 0, 0, 0)),
        Preset("Bass", listOf(600, 400, 0, -100, -200)),
        Preset("Vocal", listOf(-200, 0, 400, 300, 0)),
        Preset("Treble", listOf(-200, -100, 0, 400, 600)),
        Preset("Loud", listOf(400, 200, 0, 200, 400)),
    )

    fun flat(): List<Int> = List(BAND_COUNT) { 0 }

    /** Never returns fewer or more than [BAND_COUNT] entries. */
    fun clampBands(bands: List<Int>): List<Int> {
        val safe = bands.take(BAND_COUNT).map { it.coerceIn(MIN_LEVEL_MB, MAX_LEVEL_MB) }
        return if (safe.size == BAND_COUNT) safe else flat()
    }

    fun encode(bands: List<Int>): String = clampBands(bands).joinToString(",")

    /** Anything unparseable falls back to flat audio, never to a crash. */
    fun decode(raw: String?): List<Int> {
        if (raw.isNullOrBlank()) return flat()
        val parsed = raw.split(',')
            .mapNotNull { it.trim().toIntOrNull() }
        return clampBands(parsed)
    }

    fun preset(name: String): List<Int> =
        presets.firstOrNull { it.name.equals(name, ignoreCase = true) }?.gains ?: flat()

    fun matchingPresetName(bands: List<Int>): String? =
        presets.firstOrNull { it.gains == clampBands(bands) }?.name
}
