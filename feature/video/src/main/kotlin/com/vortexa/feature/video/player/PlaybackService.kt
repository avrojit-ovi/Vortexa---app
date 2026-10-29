package com.vortexa.feature.video.player

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.vortexa.feature.video.R

/**
 * Owns the single player instance, outside the activity.
 *
 * That is what makes background play real: closing the player screen (or the
 * whole app) does not stop the audio, the notification keeps the transport
 * controls, and returning to the screen reconnects to the same position.
 */
@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var player: ExoPlayer? = null
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        val exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        val mediaSession = MediaSession.Builder(this, exoPlayer).build()

        // A private channel id + translated name, instead of the library default.
        val notification = DefaultMediaNotificationProvider(
            this,
            { NOTIFICATION_ID },
            CHANNEL_ID,
            R.string.playback_channel_name,
        ).apply { setSmallIcon(R.drawable.ic_stat_playback) }
        setMediaNotificationProvider(notification)

        player = exoPlayer
        session = mediaSession
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /** A swipe-away should not kill music that is actually playing. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val exoPlayer = player
        if (exoPlayer == null || !exoPlayer.playWhenReady || exoPlayer.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        session?.run {
            player.clearMediaItems()
            release()
        }
        session = null
        player?.release()
        player = null
        super.onDestroy()
    }

    private companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "vortexa.playback"
    }
}
