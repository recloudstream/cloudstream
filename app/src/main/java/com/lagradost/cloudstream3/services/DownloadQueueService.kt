package com.lagradost.cloudstream3.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
import android.os.Build.VERSION.SDK_INT
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.PendingIntentCompat
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.utils.AppContextUtils.createNotificationChannel
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.UIHelper.colorFromAttribute
import com.lagradost.cloudstream3.utils.downloader.DownloadQueueManager
import com.lagradost.cloudstream3.utils.downloader.VideoDownloadManager
import kotlinx.coroutines.delay

class DownloadQueueService : Service() {
    companion object {
        const val TAG = "DownloadQueueService"
        const val DOWNLOAD_QUEUE_CHANNEL_ID = "cloudstream3.download.queue"
        const val DOWNLOAD_QUEUE_CHANNEL_NAME = "Download queue service"
        const val DOWNLOAD_QUEUE_CHANNEL_DESCRIPTION = "App download queue notification."
        const val DOWNLOAD_QUEUE_NOTIFICATION_ID = 917194232 // Random unique
        var isRunning = false

        fun getIntent(
            context: Context,
        ): Intent {
            return Intent(context, DownloadQueueService::class.java)
        }
    }

    private var downloadInstances: MutableList<VideoDownloadManager.EpisodeDownloadInstance> =
        mutableListOf()

    private val baseNotification by lazy {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent =
            PendingIntentCompat.getActivity(this, 0, intent, 0, false)

        val activeDownloads = resources.getQuantityString(R.plurals.downloads_active, 0).format(0)
        val activeQueue = resources.getQuantityString(R.plurals.downloads_queued, 0).format(0)

        NotificationCompat.Builder(this, DOWNLOAD_QUEUE_CHANNEL_ID)
            .setOngoing(true) // Make it persistent
            .setAutoCancel(false)
            .setColorized(false)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            // If low priority then the notification might not show :(
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setColor(this.colorFromAttribute(R.attr.colorPrimary))
            .setContentText(activeDownloads)
            .setSubText(activeQueue)
            .setContentIntent(pendingIntent)
            .setSmallIcon(R.drawable.download_icon_load)
    }


    private fun updateNotification(context: Context, downloads: Int, queued: Int) {
        val activeDownloads =
            resources.getQuantityString(R.plurals.downloads_active, downloads).format(downloads)
        val activeQueue =
            resources.getQuantityString(R.plurals.downloads_queued, queued).format(queued)

        val newNotification = baseNotification
            .setContentText(activeDownloads)
            .setSubText(activeQueue)
            .build()

        NotificationManagerCompat.from(context)
            .notify(DOWNLOAD_QUEUE_NOTIFICATION_ID, newNotification)
    }

    override fun onCreate() {
        isRunning = true
        Log.d(TAG, "Download queue service started.")
        this.createNotificationChannel(
            DOWNLOAD_QUEUE_CHANNEL_ID,
            DOWNLOAD_QUEUE_CHANNEL_NAME,
            DOWNLOAD_QUEUE_CHANNEL_DESCRIPTION
        )
        if (SDK_INT >= 29) {
            startForeground(
                DOWNLOAD_QUEUE_NOTIFICATION_ID,
                baseNotification.build(),
                FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(DOWNLOAD_QUEUE_NOTIFICATION_ID, baseNotification.build())
        }

        val context = this.applicationContext

        ioSafe {
            while (isRunning && (DownloadQueueManager.queue.isNotEmpty() || downloadInstances.isNotEmpty())) {
                // Remove any completed or failed works
                downloadInstances =
                    downloadInstances.filterNot { it.isCompleted || it.isFailed }.toMutableList()

                val maxDownloads = VideoDownloadManager.maxConcurrentDownloads(context)
                val currentDownloads = downloadInstances.size

                val newDownloads = minOf(
                    // Cannot exceed the max downloads
                    maxOf(0, maxDownloads - currentDownloads),
                    // Cannot start more downloads than the queue size
                    DownloadQueueManager.queue.size
                )

                repeat(newDownloads) {
                    val downloadInstance = DownloadQueueManager.popQueue(context) ?: return@repeat
                    downloadInstance.startDownload()
                    downloadInstances.add(downloadInstance)
                }

                // The downloads actually displayed to the user with a notification
                val currentVisualDownloads =
                    VideoDownloadManager.currentDownloads.size + downloadInstances.count {
                        VideoDownloadManager.currentDownloads.contains(it.downloadQueueWrapper.id)
                            .not()
                    }
                // Just the queue
                val currentVisualQueue = DownloadQueueManager.queue.size

                updateNotification(context, currentVisualDownloads, currentVisualQueue)

                // Arbitrary delay to prevent hogging the CPU, decrease to make the queue feel slightly more responsive
                delay(500)
            }
            stopSelf()
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "Download queue service stopped.")
        isRunning = false
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY // We want the service restarted if its killed
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTimeout(reason: Int) {
        stopSelf()
        Log.e(TAG, "Service stopped due to timeout: $reason")
    }

}
