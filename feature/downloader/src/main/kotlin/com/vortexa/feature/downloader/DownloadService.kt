package com.vortexa.feature.downloader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.vortexa.core.common.format.Formatters
import com.vortexa.core.database.entity.DownloadEntity
import com.vortexa.core.database.entity.DownloadStatus
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Keeps the process alive while downloads are running.
 *
 * The service is a thin shell: [DownloadManager] owns the transfers, Room owns
 * the state, and this service only promotes the process to foreground and
 * mirrors aggregate progress into one notification. It stops itself as soon as
 * nothing is queued or running, so no battery is spent in the background.
 */
@AndroidEntryPoint
class DownloadService : Service() {

    @Inject lateinit var manager: DownloadManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observer: Job? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE_ALL -> manager.pauseAll()
            ACTION_DISMISS -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }

        startForegroundSafely(buildNotification(active = 0, progress = 0f, text = "Preparing…"))

        if (observer == null) {
            observer = scope.launch {
                manager.observeAll().collectLatest { downloads ->
                    val active = downloads.filter { it.status == DownloadStatus.RUNNING || it.status == DownloadStatus.QUEUED }
                    if (active.isEmpty()) {
                        stopSelf()
                        return@collectLatest
                    }
                    notify(buildNotification(active.size, aggregateProgress(active), activeSummary(active)))
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        observer?.cancel()
        scope.launch { Unit }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundSafely(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun notify(notification: Notification) {
        if (NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
        }
    }

    private fun aggregateProgress(downloads: List<DownloadEntity>): Float {
        val total = downloads.sumOf { if (it.totalBytes > 0) it.totalBytes else 0L }
        if (total <= 0L) return 0f
        val done = downloads.sumOf { it.downloadedBytes }
        return (done.toFloat() / total).coerceIn(0f, 1f)
    }

    private fun activeSummary(downloads: List<DownloadEntity>): String {
        val speed = downloads.sumOf { manager.speeds.value[it.id] ?: 0L }
        val done = downloads.sumOf { it.downloadedBytes }
        val total = downloads.sumOf { if (it.totalBytes > 0) it.totalBytes else 0L }
        return if (total > 0) {
            "${Formatters.bytes(done)} / ${Formatters.bytes(total)} · ${Formatters.speed(speed)}"
        } else {
            Formatters.speed(speed)
        }
    }

    private fun buildNotification(active: Int, progress: Float, text: String): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val pauseAll = PendingIntent.getService(
            this,
            1,
            Intent(this, DownloadService::class.java).setAction(ACTION_PAUSE_ALL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(
                resources.getQuantityString(R.plurals.downloads_active, active, active),
            )
            .setContentText(text)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, getString(R.string.action_pause_all), pauseAll)
            .setProgress(100, (progress * 100).toInt(), totalUnknown(progress))
            .build()
    }

    private fun totalUnknown(progress: Float): Boolean = progress <= 0f

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_downloads),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_downloads_summary)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "vortexa_downloads"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_PAUSE_ALL = "com.vortexa.downloads.PAUSE_ALL"
        const val ACTION_DISMISS = "com.vortexa.downloads.DISMISS"

        fun ensureRunning(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, DownloadService::class.java),
            )
        }
    }
}
