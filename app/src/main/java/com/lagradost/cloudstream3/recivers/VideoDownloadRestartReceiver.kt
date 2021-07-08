package com.lagradost.cloudstream3.recivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.lagradost.cloudstream3.services.RESTART_ALL_DOWNLOADS_AND_QUEUE
import com.lagradost.cloudstream3.services.START_VALUE_KEY
import com.lagradost.cloudstream3.services.VideoDownloadKeepAliveService


class VideoDownloadRestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        Log.i("Broadcast Listened", "Service tried to stop")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context?.startForegroundService(Intent(context, VideoDownloadKeepAliveService::class.java).putExtra(START_VALUE_KEY, RESTART_ALL_DOWNLOADS_AND_QUEUE))
        } else {
            context?.startService(Intent(context, VideoDownloadKeepAliveService::class.java).putExtra(START_VALUE_KEY, RESTART_ALL_DOWNLOADS_AND_QUEUE))
        }
    }
}