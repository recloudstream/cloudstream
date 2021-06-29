package com.lagradost.cloudstream3.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import com.bumptech.glide.Glide
import com.google.android.exoplayer2.database.ExoDatabaseProvider
import com.google.android.exoplayer2.offline.DownloadManager
import com.google.android.exoplayer2.offline.DownloadRequest
import com.google.android.exoplayer2.offline.DownloadService
import com.google.android.exoplayer2.offline.DownloadService.sendAddDownload
import com.google.android.exoplayer2.offline.StreamKey
import com.google.android.exoplayer2.scheduler.Requirements
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory
import com.google.android.exoplayer2.upstream.cache.NoOpCacheEvictor
import com.google.android.exoplayer2.upstream.cache.SimpleCache
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.UIHelper.colorFromAttribute
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import java.io.File
import java.util.concurrent.Executor


const val CHANNEL_ID = "cloudstream3.general"
const val CHANNEL_NAME = "Downloads"
const val CHANNEL_DESCRIPT = "The download notification channel"

object VideoDownloadManager {
    @DrawableRes
    const val imgDone = R.drawable.rddone

    @DrawableRes
    const val imgDownloading = R.drawable.rdload

    @DrawableRes
    const val imgPaused = R.drawable.rdpause

    @DrawableRes
    const val imgStopped = R.drawable.rderror

    @DrawableRes
    const val imgError = R.drawable.rderror

    @DrawableRes
    const val pressToPauseIcon = R.drawable.ic_baseline_pause_24

    @DrawableRes
    const val pressToResumeIcon = R.drawable.ic_baseline_play_arrow_24

    @DrawableRes
    const val pressToStopIcon = R.drawable.exo_icon_stop

    enum class DownloadType {
        IsPaused,
        IsDownloading,
        IsDone,
        IsFailed,
        IsStopped,
    }

    enum class DownloadActionType {
        Pause,
        Resume,
        Stop,
    }

    private var hasCreatedNotChanel = false
    private fun Context.createNotificationChannel() {
        hasCreatedNotChanel = true
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = CHANNEL_NAME //getString(R.string.channel_name)
            val descriptionText = CHANNEL_DESCRIPT//getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            // Register the channel with the system
            val notificationManager: NotificationManager =
                this.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private val cachedBitmaps = hashMapOf<String, Bitmap>()
    private fun Context.getImageBitmapFromUrl(url: String): Bitmap? {
        if (cachedBitmaps.containsKey(url)) {
            return cachedBitmaps[url]
        }

        val bitmap = Glide.with(this)
            .asBitmap()
            .load(url).into(720, 720)
            .get()
        if (bitmap != null) {
            cachedBitmaps[url] = bitmap
        }
        return null
    }

    fun createNotification(
        context: Context,
        text: String,
        source: String,
        ep: ResultEpisode,
        state: DownloadType,
        progress: Long,
        total: Long,
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            data = source.toUri()
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent: PendingIntent = PendingIntent.getActivity(context, 0, intent, 0)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setAutoCancel(true)
            .setColorized(true)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setColor(context.colorFromAttribute(R.attr.colorPrimary))
            .setContentText(text)
            .setSmallIcon(
                when (state) {
                    DownloadType.IsDone -> imgDone
                    DownloadType.IsDownloading -> imgDownloading
                    DownloadType.IsPaused -> imgPaused
                    DownloadType.IsFailed -> imgError
                    DownloadType.IsStopped -> imgStopped
                }
            )
            .setContentIntent(pendingIntent)

        if (state == DownloadType.IsDownloading || state == DownloadType.IsPaused) {
            builder.setProgress(total.toInt(), progress.toInt(), false)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (ep.poster != null) {
                val poster = context.getImageBitmapFromUrl(ep.poster)
                if (poster != null)
                    builder.setLargeIcon(poster)
            }
        }
        if ((state == DownloadType.IsDownloading || state == DownloadType.IsPaused) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val actionTypes: MutableList<DownloadActionType> = ArrayList()
            // INIT
            if (state == DownloadType.IsDownloading) {
                actionTypes.add(DownloadActionType.Pause)
                actionTypes.add(DownloadActionType.Stop)
            }

            if (state == DownloadType.IsPaused) {
                actionTypes.add(DownloadActionType.Resume)
                actionTypes.add(DownloadActionType.Stop)
            }

            // ADD ACTIONS
            for ((index, i) in actionTypes.withIndex()) {
                val _resultIntent = Intent(context, DownloadService::class.java)

                _resultIntent.putExtra(
                    "type", when (i) {
                        DownloadActionType.Resume -> "resume"
                        DownloadActionType.Pause -> "pause"
                        DownloadActionType.Stop -> "stop"
                    }
                )

                _resultIntent.putExtra("id", ep.id)

                val pending: PendingIntent = PendingIntent.getService(
                    context, 4337 + index + ep.id,
                    _resultIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT
                )

                builder.addAction(
                    NotificationCompat.Action(
                        when (i) {
                            DownloadActionType.Resume -> pressToResumeIcon
                            DownloadActionType.Pause -> pressToPauseIcon
                            DownloadActionType.Stop -> pressToStopIcon
                        }, when (i) {
                            DownloadActionType.Resume -> "Resume"
                            DownloadActionType.Pause -> "Pause"
                            DownloadActionType.Stop -> "Stop"
                        }, pending
                    )
                )
            }
        }

        if (!hasCreatedNotChanel) {
            context.createNotificationChannel()
        }

        with(NotificationManagerCompat.from(context)) {
            // notificationId is a unique int for each notification that you must define
            notify(ep.id, builder.build())
        }
    }

    //https://exoplayer.dev/downloading-media.html
    fun DownloadSingleEpisode(context: Context, source: String, ep: ResultEpisode, link: ExtractorLink) {
        val url = link.url
        val headers = mapOf("User-Agent" to USER_AGENT, "Referer" to link.referer)

        // Note: This should be a singleton in your app.
        val databaseProvider = ExoDatabaseProvider(context)

        val downloadDirectory = File(Environment.getExternalStorageDirectory().path + "/Download/" + (ep.name ?: "Episode ${ep.episode}")) // File(context.cacheDir, "video_${ep.id}")

        // A download cache should not evict media, so should use a NoopCacheEvictor.
        val downloadCache = SimpleCache(
            downloadDirectory,
            NoOpCacheEvictor(),
            databaseProvider)

        // Create a factory for reading the data from the network.
        val dataSourceFactory = DefaultHttpDataSourceFactory()

        // Choose an executor for downloading data. Using Runnable::run will cause each download task to
        // download data on its own thread. Passing an executor that uses multiple threads will speed up
        // download tasks that can be split into smaller parts for parallel execution. Applications that
        // already have an executor for background downloads may wish to reuse their existing executor.
        val downloadExecutor = Executor { obj: Runnable -> obj.run() }


        // Create the download manager.
        val downloadManager = DownloadManager(
            context,
            databaseProvider,
            downloadCache,
            dataSourceFactory,
            downloadExecutor)

        val requirements = Requirements(Requirements.NETWORK)
        // Optionally, setters can be called to configure the download manager.
        downloadManager.requirements = requirements
        downloadManager.maxParallelDownloads = 3
        val builder = DownloadRequest.Builder(ep.id.toString(), link.url.toUri())

        val downloadRequest: DownloadRequest = builder.build()

        DownloadService.sendAddDownload(
            context,
            VideoDownloadService::class.java,
            downloadRequest,
            /* foreground= */ true)
/*
        val disposable = url.download(header = headers)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeBy(
                onNext = { progress ->
                    createNotification(
                        context,
                        "Downloading ${progress.downloadSizeStr()}/${progress.totalSizeStr()}",
                        source,
                        ep,
                        DownloadType.IsDownloading,
                        progress.downloadSize,
                        progress.totalSize
                    )
                },
                onComplete = {
                    createNotification(
                        context,
                        "Download Done",
                        source,
                        ep,
                        DownloadType.IsDone,
                        0, 0
                    )
                },
                onError = {
                    createNotification(
                        context,
                        "Download Failed",
                        source,
                        ep,
                        DownloadType.IsFailed,
                        0, 0
                    )
                }
            )*/
    }

    public fun DownloadEpisode(context: Context, source: String, ep: ResultEpisode, links: List<ExtractorLink>) {
        val validLinks = links.filter { !it.isM3u8 }
        if (validLinks.isNotEmpty()) {
            DownloadSingleEpisode(context, source, ep, validLinks.first())
        }
    }

}