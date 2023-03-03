package com.lagradost.cloudstream3.services

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.work.*
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.APIHolder.getApiDubstatusSettings
import com.lagradost.cloudstream3.APIHolder.getApiFromNameNull
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.plugins.PluginManager
import com.lagradost.cloudstream3.ui.result.txt
import com.lagradost.cloudstream3.utils.AppUtils.createNotificationChannel
import com.lagradost.cloudstream3.utils.Coroutines.ioWork
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.DataStoreHelper.getAllSubscriptions
import com.lagradost.cloudstream3.utils.DataStoreHelper.getDub
import com.lagradost.cloudstream3.utils.UIHelper.colorFromAttribute
import com.lagradost.cloudstream3.utils.VideoDownloadManager.getImageBitmapFromUrl
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit

const val SUBSCRIPTION_CHANNEL_ID = "cloudstream3.subscriptions"
const val SUBSCRIPTION_WORK_NAME = "work_subscription"
const val SUBSCRIPTION_CHANNEL_NAME = "Subscriptions"
const val SUBSCRIPTION_CHANNEL_DESCRIPTION = "Notifications for new episodes on subscribed shows"
const val SUBSCRIPTION_NOTIFICATION_ID = 938712897 // Random unique

class SubscriptionWorkManager(val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {
    companion object {
        fun enqueuePeriodicWork(context: Context?) {
            if (context == null) return

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val periodicSyncDataWork =
                PeriodicWorkRequest.Builder(SubscriptionWorkManager::class.java, 6, TimeUnit.HOURS)
                    .addTag(SUBSCRIPTION_WORK_NAME)
                    .setConstraints(constraints)
                    .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                SUBSCRIPTION_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicSyncDataWork
            )

            // Uncomment below for testing

//            val oneTimeSyncDataWork =
//                OneTimeWorkRequest.Builder(SubscriptionWorkManager::class.java)
//                    .addTag(SUBSCRIPTION_WORK_NAME)
//                    .setConstraints(constraints)
//                    .build()
//
//            WorkManager.getInstance(context).enqueue(oneTimeSyncDataWork)
        }
    }

    private val progressNotificationBuilder =
        NotificationCompat.Builder(context, SUBSCRIPTION_CHANNEL_ID)
            .setAutoCancel(false)
            .setColorized(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setColor(context.colorFromAttribute(R.attr.colorPrimary))
            .setContentTitle(context.getString(R.string.subscription_in_progress_notification))
            .setSmallIcon(R.drawable.quantum_ic_refresh_white_24)
            .setProgress(0, 0, true)

    private val updateNotificationBuilder =
        NotificationCompat.Builder(context, SUBSCRIPTION_CHANNEL_ID)
            .setColorized(true)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setColor(context.colorFromAttribute(R.attr.colorPrimary))
            .setSmallIcon(R.drawable.ic_cloudstream_monochrome_big)

    private val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun updateProgress(max: Int, progress: Int, indeterminate: Boolean) {
        notificationManager.notify(
            SUBSCRIPTION_NOTIFICATION_ID, progressNotificationBuilder
                .setProgress(max, progress, indeterminate)
                .build()
        )
    }

    override suspend fun doWork(): Result {
//        println("Update subscriptions!")
        context.createNotificationChannel(
            SUBSCRIPTION_CHANNEL_ID,
            SUBSCRIPTION_CHANNEL_NAME,
            SUBSCRIPTION_CHANNEL_DESCRIPTION
        )

        setForeground(
            ForegroundInfo(
                SUBSCRIPTION_NOTIFICATION_ID,
                progressNotificationBuilder.build()
            )
        )

        val subscriptions = getAllSubscriptions()

        if (subscriptions.isEmpty()) {
            WorkManager.getInstance(context).cancelWorkById(this.id)
            return Result.success()
        }

        val max = subscriptions.size
        var progress = 0

        updateProgress(max, progress, true)

        // We need all plugins loaded.
        PluginManager.loadAllOnlinePlugins(context)
        PluginManager.loadAllLocalPlugins(context, false)

        subscriptions.apmap { savedData ->
            try {
                val id = savedData.id ?: return@apmap null
                val api = getApiFromNameNull(savedData.apiName) ?: return@apmap null

                // Reasonable timeout to prevent having this worker run forever.
                val response = withTimeoutOrNull(60_000) {
                    api.load(savedData.url) as? EpisodeResponse
                } ?: return@apmap null

                val dubPreference =
                    getDub(id) ?: if (
                        context.getApiDubstatusSettings().contains(DubStatus.Dubbed)
                    ) {
                        DubStatus.Dubbed
                    } else {
                        DubStatus.Subbed
                    }

                val latestEpisodes = response.getLatestEpisodes()
                val latestPreferredEpisode = latestEpisodes[dubPreference]

                val (shouldUpdate, latestEpisode) = if (latestPreferredEpisode != null) {
                    val latestSeenEpisode =
                        savedData.lastSeenEpisodeCount[dubPreference] ?: Int.MIN_VALUE
                    val shouldUpdate = latestPreferredEpisode > latestSeenEpisode
                    shouldUpdate to latestPreferredEpisode
                } else {
                    val latestEpisode = latestEpisodes[DubStatus.None] ?: Int.MIN_VALUE
                    val latestSeenEpisode =
                        savedData.lastSeenEpisodeCount[DubStatus.None] ?: Int.MIN_VALUE
                    val shouldUpdate = latestEpisode > latestSeenEpisode
                    shouldUpdate to latestEpisode
                }

                DataStoreHelper.updateSubscribedData(
                    id,
                    savedData,
                    response
                )

                if (shouldUpdate) {
                    val updateHeader = savedData.name
                    val updateDescription = txt(
                        R.string.subscription_episode_released,
                        latestEpisode,
                        savedData.name
                    ).asString(context)

                    val intent = Intent(context, MainActivity::class.java).apply {
                        data = savedData.url.toUri()
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }

                    val pendingIntent =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            PendingIntent.getActivity(
                                context,
                                0,
                                intent,
                                PendingIntent.FLAG_IMMUTABLE
                            )
                        } else {
                            PendingIntent.getActivity(context, 0, intent, 0)
                        }

                    val poster = ioWork {
                        savedData.posterUrl?.let { url ->
                            context.getImageBitmapFromUrl(
                                url,
                                savedData.posterHeaders
                            )
                        }
                    }

                    val updateNotification =
                        updateNotificationBuilder.setContentTitle(updateHeader)
                            .setContentText(updateDescription)
                            .setContentIntent(pendingIntent)
                            .setLargeIcon(poster)
                            .build()

                    notificationManager.notify(id, updateNotification)
                }

                // You can probably get some issues here since this is async but it does not matter much.
                updateProgress(max, ++progress, false)
            } catch (_: Throwable) {
            }
        }

        return Result.success()
    }
}