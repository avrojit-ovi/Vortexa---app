package com.vortexa.core.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EqualizerProfileTest {

    @Test
    fun `saved bands round trip through the preference string`() {
        val bands = listOf(-300, 0, 200, 100, -100)
        val decoded = EqualizerProfile.decode(EqualizerProfile.encode(bands))
        assertEquals(bands, decoded)
    }

    @Test
    fun `garbage falls back to flat instead of throwing`() {
        assertEquals(EqualizerProfile.flat(), EqualizerProfile.decode(""))
        assertEquals(EqualizerProfile.flat(), EqualizerProfile.decode(null))
        assertEquals(EqualizerProfile.flat(), EqualizerProfile.decode("not,a,number"))
        assertEquals(EqualizerProfile.flat(), EqualizerProfile.decode("100,200"))
    }

    @Test
    fun `too many values are truncated, out of range values clamped`() {
        val decoded = EqualizerProfile.decode("9999,-9999,0,0,0,500,600")
        assertEquals(EqualizerProfile.BAND_COUNT, decoded.size)
        assertEquals(EqualizerProfile.MAX_LEVEL_MB, decoded[0])
        assertEquals(EqualizerProfile.MIN_LEVEL_MB, decoded[1])
    }

    @Test
    fun `presets are always the right width`() {
        EqualizerProfile.presets.forEach { preset ->
            assertEquals(preset.name, EqualizerProfile.BAND_COUNT, preset.gains.size)
        }
    }

    @Test
    fun `matching a preset is case insensitive`() {
        assertEquals(EqualizerProfile.preset("bass"), EqualizerProfile.preset("Bass"))
        assertEquals(EqualizerProfile.flat(), EqualizerProfile.preset("does not exist"))
    }

    @Test
    fun `the active preset is recognised, and unknown curves report none`() {
        assertEquals("Bass", EqualizerProfile.matchingPresetName(EqualizerProfile.preset("Bass")))
        assertNull(EqualizerProfile.matchingPresetName(listOf(111, 222, 333, 444, 555)))
    }

    @Test
    fun `device bands map onto the five logical bands`() {
        val equalizer = PlaybackEqualizer()
        // 1 band -> always the flat middle; 5 bands -> 1:1.
        assertEquals(0, equalizer.mapBand(0, 1))
        assertEquals(0, equalizer.mapBand(0, 5))
        assertEquals(4, equalizer.mapBand(4, 5))
        // 10 device bands: the middle maps near the middle, never out of range.
        assertEquals(2, equalizer.mapBand(4, 10))
        assertEquals(4, equalizer.mapBand(9, 10))
    }
}
