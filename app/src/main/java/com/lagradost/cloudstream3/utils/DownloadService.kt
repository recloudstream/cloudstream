package com.lagradost.cloudstream3.utils

import android.app.IntentService
import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import com.google.android.exoplayer2.offline.Download
import com.google.android.exoplayer2.offline.DownloadManager
import com.google.android.exoplayer2.offline.DownloadService
import com.google.android.exoplayer2.scheduler.PlatformScheduler
import com.google.android.exoplayer2.scheduler.Scheduler
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.UIHelper.colorFromAttribute
import java.lang.Exception

private const val JOB_ID = 1
private const val FOREGROUND_NOTIFICATION_ID = 1

class VideoDownloadService : DownloadService(
    FOREGROUND_NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    CHANNEL_ID,
    R.string.exo_download_notification_channel_name,  /* channelDescriptionResourceId= */
    0) {
    override fun getDownloadManager(): DownloadManager {
        val ctx = this
        return ExoPlayerHelper.downloadManager.apply {
            requirements = DownloadManager.DEFAULT_REQUIREMENTS
            maxParallelDownloads = 3
            addListener(
                object : DownloadManager.Listener {

                    override fun onDownloadChanged(
                        downloadManager: DownloadManager,
                        download: Download,
                        finalException: Exception?,
                    ) {
                        val intent = Intent(ctx, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }

                        val pendingIntent: PendingIntent = PendingIntent.getActivity(ctx, 0, intent, 0)
                        val builder = NotificationCompat.Builder(ctx, CHANNEL_ID)
                            .setAutoCancel(true)
                            .setColorized(true)
                            .setAutoCancel(true)
                            .setOnlyAlertOnce(true)
                            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                            .setColor(colorFromAttribute(R.attr.colorPrimary))
                            .setContentText("${download.bytesDownloaded} / ${download.contentLength}")
                            .setSmallIcon(
                                VideoDownloadManager.imgDownloading
                            )
                            .setProgress((download.bytesDownloaded / 1000).toInt(),
                                (download.contentLength / 1000).toInt(),
                                false) // in case the size is over 2gb / 1000
                            .setContentIntent(pendingIntent)
                        builder.build()
                        with(NotificationManagerCompat.from(ctx)) {
                            // notificationId is a unique int for each notification that you must define
                            notify(download.request.id.hashCode(), builder.build())
                        }
                        super.onDownloadChanged(downloadManager, download, finalException)
                    }
                }
            )
        }
    }

    override fun getScheduler(): Scheduler =
        PlatformScheduler(this, JOB_ID)

    override fun getForegroundNotification(downloads: MutableList<Download>): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent: PendingIntent = PendingIntent.getActivity(this, 0, intent, 0)
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setAutoCancel(true)
            .setColorized(true)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setColor(colorFromAttribute(R.attr.colorPrimary))
            .setContentText("Downloading ${downloads.size} item${if (downloads.size == 1) "" else "s"}")
            .setSmallIcon(
                VideoDownloadManager.imgDownloading
            )
            .setContentIntent(pendingIntent)
        return builder.build()
    }
}