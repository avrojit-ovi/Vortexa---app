package com.vortexa.core.playback

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector

/**
 * A codec selector that can be told to prefer software decoders.
 *
 * Media3 has no switch for this on a live player, so the flag is read every time
 * a track is initialised: flipping it changes the next video, not the one that
 * is already running.
 */
@UnstableApi
class DecoderCodecSelector : MediaCodecSelector {

    @Volatile
    var preferSoftware: Boolean = false

    override fun getDecoderInfos(
        mimeType: String,
        requiresSecureDecoder: Boolean,
        requiresTunnelingDecoder: Boolean,
    ): List<MediaCodecInfo> {
        val hardwareFirst = MediaCodecSelector.DEFAULT.getDecoderInfos(
            mimeType,
            requiresSecureDecoder,
            requiresTunnelingDecoder,
        )
        if (!preferSoftware) return hardwareFirst
        // Stable partition: software decoders first, hardware kept as fallback
        // so a file with no software decoder still plays.
        return hardwareFirst.sortedBy { if (it.hardwareAccelerated) 1 else 0 }
    }
}
