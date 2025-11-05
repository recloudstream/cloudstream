package com.lagradost.cloudstream3

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

object NotificationHelper {
    private const val CHANNEL_ID = "updates_channel"
    private const val CHANNEL_NAME = "App Updates"
    private const val NOTIF_ID = 1001

    fun createUpdateNotification(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT)
            channel.setShowBadge(false)
            nm.createNotificationChannel(channel)
        }

        val updateIntent = Intent(context, UpdateActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            updateIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            else
                PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notif: Notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Update available")
            .setContentText("Tap to update now")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        nm.notify(NOTIF_ID, notif)
    }

    fun clearUpdateNotification(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIF_ID)
    }
}
