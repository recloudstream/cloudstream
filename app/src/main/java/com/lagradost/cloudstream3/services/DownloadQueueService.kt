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
import com.lagradost.cloudstream3.CloudStreamApp.Companion.removeKey
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.MainActivity.Companion.lastError
import com.lagradost.cloudstream3.MainActivity.Companion.setLastError
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.mvvm.debugAssert
import com.lagradost.cloudstream3.mvvm.debugWarning
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.safe
import com.lagradost.cloudstream3.plugins.PluginManager
import com.lagradost.cloudstream3.utils.AppContextUtils.createNotificationChannel
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.UIHelper.colorFromAttribute
import com.lagradost.cloudstream3.utils.downloader.DownloadQueueManager
import com.lagradost.cloudstream3.utils.downloader.VideoDownloadManager
import com.lagradost.cloudstream3.utils.downloader.VideoDownloadManager.KEY_RESUME_IN_QUEUE
import com.lagradost.cloudstream3.utils.downloader.VideoDownloadManager.KEY_RESUME_PACKAGES
import com.lagradost.cloudstream3.utils.downloader.VideoDownloadManager.downloadEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.system.measureTimeMillis
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class DownloadQueueService : Service() {
    companion object {
        const val TAG = "DownloadQueueService"
        const val DOWNLOAD_QUEUE_CHANNEL_ID = "cloudstream3.download.queue"
        const val DOWNLOAD_QUEUE_CHANNEL_NAME = "Download queue service"
        const val DOWNLOAD_QUEUE_CHANNEL_DESCRIPTION = "App download queue notification."
        const val DOWNLOAD_QUEUE_NOTIFICATION_ID = 917194232 // Random unique
        @Volatile
        var isRunning = false

        fun getIntent(
            context: Context,
        ): Intent {
            return Intent(context, DownloadQueueService::class.java)
        }

        private val _downloadInstances: MutableStateFlow<List<VideoDownloadManager.EpisodeDownloadInstance>> =
            MutableStateFlow(emptyList())

        /** Flow of all active downloads, not queued. May temporarily contain completed or failed EpisodeDownloadInstances.
         * Completed or failed instances are automatically removed by the download queue service.
         *
         */
        val downloadInstances: StateFlow<List<VideoDownloadManager.EpisodeDownloadInstance>> =
            _downloadInstances

        private val totalDownloadFlow =
            downloadInstances.combine(DownloadQueueManager.queue) { instances, queue ->
                instances to queue
            }
                .combine(VideoDownloadManager.currentDownloads) { (instances, queue), currentDownloads ->
                    Triple(instances, queue, currentDownloads)
                }
    }


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

        safe {
            NotificationManagerCompat.from(context)
                .notify(DOWNLOAD_QUEUE_NOTIFICATION_ID, newNotification)
        }
    }

    // We always need to listen to events, even before the download is launched.
    // Stopping link loading is an event which can trigger before downloading.
    val downloadEventListener = { event: Pair<Int, VideoDownloadManager.DownloadActionType> ->
        when (event.second) {
            VideoDownloadManager.DownloadActionType.Stop -> {
                removeKey(KEY_RESUME_PACKAGES, event.first.toString())
                removeKey(KEY_RESUME_IN_QUEUE, event.first.toString())
                DownloadQueueManager.cancelDownload(event.first)
            }

            else -> {}
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    override fun onCreate() {
        isRunning = true
        val context: Context = this // To make code more readable

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

        downloadEvent += downloadEventListener

        val queueJob = ioSafe {
            // Ensure this is up to date to prevent race conditions with MainActivity launches
            setLastError(context)
            // Early return, to prevent waiting for plugins in safe mode
            if (lastError != null) return@ioSafe

            // Try to ensure all plugins are loaded before starting the downloader.
            // To prevent infinite stalls we use a timeout of 15 seconds, it is judged as long enough
            val timeout = 15.seconds
            val timeTaken = withTimeoutOrNull(timeout) {
                measureTimeMillis {
                    while (!(PluginManager.loadedOnlinePlugins && PluginManager.loadedLocalPlugins)) {
                        delay(100.milliseconds)
                    }
                }
            }

            debugWarning({ timeTaken == null || timeTaken > 3_000 }, {
                "Abnormally long downloader startup time of: ${timeTaken ?: timeout.inWholeMilliseconds}ms"
            })
            debugAssert({ timeTaken == null }, { "Downloader startup should not time out" })

            totalDownloadFlow
                .takeWhile { (instances, queue) ->
                    // Stop if destroyed
                    isRunning
                            // Run as long as there is a queue to process
                            && (instances.isNotEmpty() || queue.isNotEmpty())
                            // Run as long as there are no app crashes
                            && lastError == null
                }
                .collect { (_, queue, currentDownloads) ->
                    // Remove completed or failed
                    val newInstances = _downloadInstances.updateAndGet { currentInstances ->
                        currentInstances.filterNot { it.isCompleted || it.isFailed || it.isCancelled }
                    }

                    val maxDownloads = VideoDownloadManager.maxConcurrentDownloads(context)
                    val currentInstanceCount = newInstances.size

                    val newDownloads = minOf(
                        // Cannot exceed the max downloads
                        maxOf(0, maxDownloads - currentInstanceCount),
                        // Cannot start more downloads than the queue size
                        queue.size
                    )

                    // Cant start multiple downloads at once. If this is rerun it may start too many downloads.
                    if (newDownloads > 0) {
                        _downloadInstances.update { instances ->
                            val downloadInstance = DownloadQueueManager.popQueue(context)
                            if (downloadInstance != null) {
                                downloadInstance.startDownload()
                                instances + downloadInstance
                            } else {
                                instances
                            }
                        }
                    }

                    // The downloads actually displayed to the user with a notification
                    val currentVisualDownloads =
                        currentDownloads.size + newInstances.count {
                            currentDownloads.contains(it.downloadQueueWrapper.id)
                                .not()
                        }
                    // Just the queue
                    val currentVisualQueue = queue.size

                    updateNotification(context, currentVisualDownloads, currentVisualQueue)
                }
        }

        // Stop self regardless of job outcome
        queueJob.invokeOnCompletion { throwable ->
            if (throwable != null) {
                logError(throwable)
            }
            safe {
                stopSelf()
            }
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "Download queue service stopped.")
        downloadEvent -= downloadEventListener
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
