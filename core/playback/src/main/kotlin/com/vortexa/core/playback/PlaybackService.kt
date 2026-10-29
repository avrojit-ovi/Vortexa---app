package com.vortexa.core.playback

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * One player for the whole app, owned by a service rather than a screen.
 *
 * Video and music both talk to it through a [MediaController], which is why
 * playback survives leaving the screen, why the notification and headset
 * buttons work, and why there is only one audio focus holder.
 */
@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var player: ExoPlayer? = null
    private var session: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var sleepTimer: Job? = null

    override fun onCreate() {
        super.onCreate()

        val exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SLEEP_TIMER -> startSleepTimer(intent.getIntExtra(EXTRA_MINUTES, 0))
            ACTION_CANCEL_SLEEP_TIMER -> cancelSleepTimer()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    /** Pauses playback after [minutes]; 0 or less cancels. */
    private fun startSleepTimer(minutes: Int) {
        sleepTimer?.cancel()
        if (minutes <= 0) return
        sleepTimer = scope.launch {
            delay(minutes * 60_000L)
            player?.pause()
        }
    }

    private fun cancelSleepTimer() {
        sleepTimer?.cancel()
        sleepTimer = null
    }

    /** A swipe-away should not kill audio that is actually playing. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val exoPlayer = player
        if (exoPlayer == null || !exoPlayer.playWhenReady || exoPlayer.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        sleepTimer?.cancel()
        session?.run {
            player.clearMediaItems()
            release()
        }
        session = null
        player?.release()
        player = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_SLEEP_TIMER = "com.vortexa.core.playback.SLEEP_TIMER"
        const val ACTION_CANCEL_SLEEP_TIMER = "com.vortexa.core.playback.CANCEL_SLEEP_TIMER"
        const val EXTRA_MINUTES = "minutes"

        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "vortexa.playback"

        fun sleepTimer(context: Context, minutes: Int) {
            val intent = Intent(context, PlaybackService::class.java).apply {
                action = ACTION_SLEEP_TIMER
                putExtra(EXTRA_MINUTES, minutes)
            }
            // The service is already running while audio plays; a plain start
            // avoids the foreground-service deadline when it is not.
            context.startService(intent)
        }

        fun cancelSleepTimer(context: Context) {
            val intent = Intent(context, PlaybackService::class.java).apply {
                action = ACTION_CANCEL_SLEEP_TIMER
            }
            context.startService(intent)
        }

        /** Connects a controller to the running session (or starts one). */
        fun connect(context: Context): com.google.common.util.concurrent.ListenableFuture<MediaController> {
            val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            return MediaController.Builder(context, token).buildAsync()
        }
    }
}
