package com.lagradost.cloudstream3.services

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
import android.os.Build.VERSION.SDK_INT
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.PendingIntentCompat
import androidx.core.net.toUri
import androidx.work.*
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.APIHolder.getApiFromNameNull
import com.lagradost.cloudstream3.APIHolder.unixTime
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.plugins.PluginManager
import com.lagradost.cloudstream3.utils.txt
import com.lagradost.cloudstream3.utils.AppContextUtils.createNotificationChannel
import com.lagradost.cloudstream3.utils.AppContextUtils.getApiDubstatusSettings
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
            .setSmallIcon(com.google.android.gms.cast.framework.R.drawable.quantum_ic_refresh_white_24)
            .setProgress(0, 0, true)

    private val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun updateProgress(max: Int, progress: Int, indeterminate: Boolean) {
        notificationManager.notify(
            SUBSCRIPTION_NOTIFICATION_ID, progressNotificationBuilder
                .setProgress(max, progress, indeterminate)
                .build()
        )
    }
    @Suppress("DEPRECATION_ERROR")
    override suspend fun doWork(): Result {
        try {
//        println("Update subscriptions!")
            context.createNotificationChannel(
                SUBSCRIPTION_CHANNEL_ID,
                SUBSCRIPTION_CHANNEL_NAME,
                SUBSCRIPTION_CHANNEL_DESCRIPTION
            )

            val foregroundInfo = if (SDK_INT >= 29)
                ForegroundInfo(
                    SUBSCRIPTION_NOTIFICATION_ID,
                    progressNotificationBuilder.build(),
                    FOREGROUND_SERVICE_TYPE_DATA_SYNC
                ) else ForegroundInfo(SUBSCRIPTION_NOTIFICATION_ID, progressNotificationBuilder.build(),)
            setForeground(foregroundInfo)

            val subscriptions = getAllSubscriptions()

            if (subscriptions.isEmpty()) {
                WorkManager.getInstance(context).cancelWorkById(this.id)
                return Result.success()
            }

            val max = subscriptions.size
            var progress = 0

            updateProgress(max, progress, true)

            // We need all plugins loaded.
            PluginManager.___DO_NOT_CALL_FROM_A_PLUGIN_loadAllOnlinePlugins(context)
            PluginManager.___DO_NOT_CALL_FROM_A_PLUGIN_loadAllLocalPlugins(context, false)

            subscriptions.amap { savedData ->
                try {
                    val id = savedData.id ?: return@amap null
                    val api = getApiFromNameNull(savedData.apiName) ?: return@amap null

                    // Reasonable timeout to prevent having this worker run forever.
                    val loadResponse = withTimeoutOrNull(60_000) {
                        api.load(savedData.url)
                    } ?: return@amap null

                    val dubPreference =
                        getDub(id) ?: if (
                            context.getApiDubstatusSettings().contains(DubStatus.Dubbed)
                        ) {
                            DubStatus.Dubbed
                        } else {
                            DubStatus.Subbed
                        }

                    var season = 0
                    val response = loadResponse as? EpisodeResponse ?: return@amap null
                    val latestEpisodes = response.getLatestEpisodes()
                    val latestPreferredEpisode = latestEpisodes[dubPreference]
                    val nextAiring = response.nextAiring

                    if (nextAiring != null && nextAiring.unixTime > unixTime) {
                        EpisodeAlertManager.scheduleEpisodeAlert(
                            subscribedData = savedData,
                            nextAiring = nextAiring,
                            episodeResponse = response,
                            apiName = api.name,
                            context = applicationContext,
                        )
                        updateProgress(max, ++progress, false)

                        // Early return to prevent notifying users of unavailable episodes
                        // on the rare occasion latestPreferredEpisode changes for meta providers
                        return@amap Unit
                    }

                    when (loadResponse) {
                        is TvSeriesLoadResponse -> {
                            season = loadResponse.episodes.maxOf { it.season ?: Int.MIN_VALUE }
                        }

                        is AnimeLoadResponse -> {
                            loadResponse.episodes[dubPreference]?.let { episodes ->
                                season = episodes.maxOf { it.season ?: Int.MIN_VALUE }
                            }
                        }
                    }

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
                        EpisodeAlertManager.showEpisodeNotification(
                            id = id,
                            season = season,
                            episode = latestEpisode,
                            savedData = savedData,
                            apiName = api.name,
                            context = context
                        )
                    }

                    // You can probably get some issues here since this is async but it does not matter much.
                    updateProgress(max, ++progress, false)
                } catch (t: Throwable) {
                    logError(t)
                }
            }

            return Result.success()
        } catch (t: Throwable) {
            logError(t)
            // ye, while this is not correct, but because gods know why android just crashes
            // and this causes major battery usage as it retries it inf times. This is better, just
            // in case android decides to be android and fuck us
            return Result.success()
        }
    }
}

class EpisodeAlertWorker(
    val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {
    companion object {
        const val SUBSCRIPTION_TAG = "SUBSCRIPTION_TAG"
        const val SUBSCRIPTION_ID = "subscription_id"
        const val EPISODE_NO = "episode_no"
        const val SEASON_NO = "season_no"
        const val API_NAME = "api_name"
    }

    override suspend fun doWork(): Result {

        val subscriptionId = inputData.getInt(SUBSCRIPTION_ID, -1)
        val episode = inputData.getInt(EPISODE_NO, -1)
        val season = inputData.getInt(SEASON_NO, -1)
        val apiName = inputData.getString(API_NAME) ?: return Result.success()

        // Final check to ensure user is still subscribed before notifying
        val savedData = DataStoreHelper.getSubscribedData(subscriptionId) ?: return Result.success()
        val id = savedData.id ?: return Result.success()

        EpisodeAlertManager.showEpisodeNotification(
            id = id,
            season = season,
            episode = episode,
            savedData = savedData,
            apiName = apiName,
            context = applicationContext
        )

        return Result.success()
    }
}

class EpisodeAlertManager {
    companion object {
        fun scheduleEpisodeAlert(
            subscribedData: DataStoreHelper.SubscribedData,
            nextAiring: NextAiring?,
            episodeResponse: EpisodeResponse?,
            apiName: String,
            context: Context
        ) {
            val now = unixTime

            when {
                nextAiring == null -> {
                    DataStoreHelper.updateSubscribedData(
                        subscribedData.id,
                        subscribedData,
                        episodeResponse
                    )
                }

                nextAiring.unixTime > now && nextAiring.unixTime < now + 31_556_926L -> {
                    val episodeKey = "${nextAiring.season ?: ""}_${nextAiring.episode}"
                    val uniqueWorkName = "${apiName}_${subscribedData.id}_$episodeKey"
                    val delay = nextAiring.unixTime - now

                    // Work manager's replace policy will take care of rescheduling if the air time changes
                    enqueueEpisodeAlertWorker(
                        subscribedData = subscribedData,
                        nextAiring = nextAiring,
                        apiName = apiName,
                        uniqueWorkName = uniqueWorkName,
                        delay = delay,
                        context = context,
                    )

                    DataStoreHelper.updateSubscribedData(
                        subscribedData.id,
                        subscribedData,
                        episodeResponse
                    )
                }
            }
        }

        private fun enqueueEpisodeAlertWorker(
            subscribedData: DataStoreHelper.SubscribedData,
            nextAiring: NextAiring,
            apiName: String,
            uniqueWorkName: String,
            delay: Long,
            context: Context
        ) {
            val inputData = workDataOf(
                EpisodeAlertWorker.SUBSCRIPTION_ID to subscribedData.id,
                EpisodeAlertWorker.EPISODE_NO to nextAiring.episode,
                EpisodeAlertWorker.SEASON_NO to nextAiring.season,
                EpisodeAlertWorker.API_NAME to apiName,
            )

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<EpisodeAlertWorker>()
                .setInitialDelay(delay, TimeUnit.SECONDS)
                .addTag(EpisodeAlertWorker.SUBSCRIPTION_TAG)
                .setInputData(inputData)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                uniqueWorkName,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        suspend fun showEpisodeNotification(
            id: Int,
            season: Int,
            episode: Int,
            savedData: DataStoreHelper.SubscribedData,
            apiName: String,
            context: Context
        ) {
            val updateHeader = savedData.name
            val posterUrl = savedData.posterUrl
            val posterHeaders = savedData.posterHeaders
            val intent = Intent(context, MainActivity::class.java).apply {
                data = savedData.url.toUri()
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra(MainActivity.API_NAME_EXTRA_KEY, apiName)
            }

            val pendingIntent =
                PendingIntentCompat.getActivity(context, 0, intent, 0, false)

            val updateDescription = if (season > 0) {
                txt(
                    R.string.subscription_season_episode_released,
                    season,
                    episode,
                ).asString(context)
            } else {
                txt(
                    R.string.subscription_episode_released,
                    episode,
                ).asString(context)
            }

            val notificationBuilder =
                NotificationCompat.Builder(context, SUBSCRIPTION_CHANNEL_ID)
                    .setColorized(true)
                    .setOnlyAlertOnce(true)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setColor(context.colorFromAttribute(R.attr.colorPrimary))
                    .setSmallIcon(R.drawable.ic_cloudstream_monochrome_big)
                    .setContentTitle(updateHeader)
                    .setContentText(updateDescription)
                    .setContentIntent(pendingIntent)

            val poster = ioWork {
                posterUrl?.let { url ->
                    context.getImageBitmapFromUrl(url, posterHeaders)
                }
            }
            notificationBuilder.setLargeIcon(poster)

            NotificationManagerCompat.from(context)
                .notify(id, notificationBuilder.build())
        }
    }
}