package com.vortexa.core.playback

import android.media.audiofx.Equalizer
import android.util.Log

/**
 * Thin, defensive wrapper over the platform equalizer.
 *
 * Devices disagree wildly: some expose three bands, some ten, some refuse the
 * effect entirely. Everything is best-effort — a missing equalizer must cost the
 * user nothing but a disabled switch, never a crash or a silent stream.
 */
class PlaybackEqualizer {

    private var effect: Equalizer? = null
    private var sessionId: Int = 0

    /** Number of bands the device actually exposes, 0 when unavailable. */
    var bandCount: Int = 0
        private set

    var available: Boolean = false
        private set

    fun attach(audioSessionId: Int) {
        if (audioSessionId == sessionId && effect != null) return
        release()
        if (audioSessionId == 0) return
        sessionId = audioSessionId

        runCatching {
            val created = Equalizer(0, audioSessionId)
            created.enabled = false
            effect = created
            bandCount = created.numberOfBands.toInt()
            available = bandCount > 0
        }.onFailure {
            Log.w(TAG, "Equalizer unavailable on this device: ${it.message}")
            effect = null
            bandCount = 0
            available = false
        }
    }

    /** Maps the app's five logical bands onto the device's real band count. */
    fun apply(enabled: Boolean, bands: List<Int>) {
        val equalizer = effect ?: return
        runCatching {
            if (!enabled) {
                equalizer.enabled = false
                return
            }
            val clamped = EqualizerProfile.clampBands(bands)
            for (band in 0 until bandCount) {
                val level = clamped[mapBand(band, bandCount)]
                val min = runCatching { equalizer.bandLevelRange?.get(0)?.toInt() }.getOrNull() ?: -1500
                val max = runCatching { equalizer.bandLevelRange?.get(1)?.toInt() }.getOrNull() ?: 1500
                equalizer.setBandLevel(band.toShort(), level.coerceIn(min, max).toShort())
            }
            equalizer.enabled = true
        }.onFailure {
            Log.w(TAG, "Could not apply equalizer settings: ${it.message}")
        }
    }

    /** 5 logical bands -> device band index, mirrored when the device has fewer. */
    fun mapBand(deviceBand: Int, deviceBandCount: Int): Int {
        if (deviceBandCount <= 1) return 0
        // Integer scaling keeps pairs of device bands on the same logical band
        // (10 device bands -> 0,0,1,1,2,2,3,3,4,4) instead of drifting.
        return ((deviceBand * EqualizerProfile.BAND_COUNT) / deviceBandCount)
            .coerceIn(0, EqualizerProfile.BAND_COUNT - 1)
    }

    fun release() {
        runCatching { effect?.release() }
        effect = null
        available = false
        bandCount = 0
        sessionId = 0
    }

    private companion object {
        const val TAG = "PlaybackEqualizer"
    }
}
