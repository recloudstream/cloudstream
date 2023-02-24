package com.lagradost.cloudstream3.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils.createNotificationChannel
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.UIHelper.colorFromAttribute
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.roundToInt


class PackageInstallerService : Service() {
    val receivers = mutableListOf<BroadcastReceiver>()

    private val baseNotification by lazy {
        val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE
        } else 0

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent =
            PendingIntent.getActivity(this, 0, intent, flag)

        NotificationCompat.Builder(this, UPDATE_CHANNEL_ID)
            .setAutoCancel(false)
            .setColorized(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            // If low priority then the notification might not show :(
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setColor(this.colorFromAttribute(R.attr.colorPrimary))
            .setContentTitle(getString(R.string.update_notification_downloading))
            .setContentIntent(pendingIntent)
            .setSmallIcon(R.drawable.rdload)
    }

    override fun onCreate() {
        this.createNotificationChannel(
            UPDATE_CHANNEL_ID,
            UPDATE_CHANNEL_NAME,
            UPDATE_CHANNEL_DESCRIPTION
        )
        startForeground(UPDATE_NOTIFICATION_ID, baseNotification.build())
    }

    private val updateLock = Mutex()

    private suspend fun downloadUpdate(url: String): Boolean {
        try {
            Log.d(LOG_TAG, "Downloading update: $url")

            // Delete all old updates
            ioSafe {
                val appUpdateName = "CloudStream"
                val appUpdateSuffix = "apk"

                this@PackageInstallerService.cacheDir.listFiles()?.filter {
                    it.name.startsWith(appUpdateName) && it.extension == appUpdateSuffix
                }?.forEach {
                    it.deleteOnExit()
                }
            }

            updateLock.withLock {
                updateNotificationProgress(
                    0f,
                    ApkInstaller.InstallProgressStatus.Downloading
                )

                val body = app.get(url).body
                val inputStream = body.byteStream()
                val installer = ApkInstaller(this)
                val totalSize = body.contentLength()
                var currentSize = 0

                installer.installApk(this, inputStream, totalSize, {
                    currentSize += it
                    // Prevent div 0
                    if (totalSize == 0L) return@installApk

                    val percentage = currentSize / totalSize.toFloat()
                    updateNotificationProgress(
                        percentage,
                        ApkInstaller.InstallProgressStatus.Downloading
                    )
                }) { status ->
                    updateNotificationProgress(0f, status)
                }
            }
            return true
        } catch (e: Exception) {
            updateNotificationProgress(0f, ApkInstaller.InstallProgressStatus.Failed)
            return false
        }
    }

    private fun updateNotificationProgress(
        percentage: Float,
        state: ApkInstaller.InstallProgressStatus
    ) {
//        Log.d(LOG_TAG, "Downloading app update progress $percentage | $state")
        val text = when (state) {
            ApkInstaller.InstallProgressStatus.Installing -> R.string.update_notification_installing
            ApkInstaller.InstallProgressStatus.Preparing, ApkInstaller.InstallProgressStatus.Downloading -> R.string.update_notification_downloading
            ApkInstaller.InstallProgressStatus.Failed -> R.string.update_notification_failed
        }

        val newNotification = baseNotification
            .setContentTitle(getString(text))
            .apply {
                if (state == ApkInstaller.InstallProgressStatus.Failed) {
                    setSmallIcon(R.drawable.rderror)
                    setAutoCancel(true)
                } else {
                    setProgress(
                        10000, (10000 * percentage).roundToInt(),
                        state != ApkInstaller.InstallProgressStatus.Downloading
                    )
                }
            }
            .build()

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Persistent notification on failure
        val id =
            if (state == ApkInstaller.InstallProgressStatus.Failed) UPDATE_NOTIFICATION_ID + 1 else UPDATE_NOTIFICATION_ID
        notificationManager.notify(id, newNotification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = intent?.getStringExtra(EXTRA_URL) ?: return START_NOT_STICKY
        ioSafe {
            downloadUpdate(url)
            // Close the service after the update is done
            // If no sleep then the install prompt may not appear and the notification
            // will disappear instantly
            delay(10_000)
            this@PackageInstallerService.stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        receivers.forEach {
            try {
                this.unregisterReceiver(it)
            } catch (_: IllegalArgumentException) {
                // Receiver not registered
            }
        }
        super.onDestroy()
    }

    override fun onBind(i: Intent?): IBinder? = null

    companion object {
        private const val EXTRA_URL = "EXTRA_URL"
        private const val LOG_TAG = "PackageInstallerService"

        const val UPDATE_CHANNEL_ID = "cloudstream3.updates"
        const val UPDATE_CHANNEL_NAME = "App Updates"
        const val UPDATE_CHANNEL_DESCRIPTION = "App updates notification channel"
        const val UPDATE_NOTIFICATION_ID = -68454136 // Random unique

        fun getIntent(
            context: Context,
            url: String,
        ): Intent {
            return Intent(context, PackageInstallerService::class.java)
                .putExtra(EXTRA_URL, url)
        }
    }
}
