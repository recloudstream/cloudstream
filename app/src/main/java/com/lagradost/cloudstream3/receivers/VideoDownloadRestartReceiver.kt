package com.lagradost.cloudstream3.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class VideoDownloadRestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        Log.i("Broadcast Listened", "Service tried to stop")

//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            context?.startForegroundService(Intent(context, VideoDownloadKeepAliveService::class.java).putExtra(START_VALUE_KEY, RESTART_ALL_DOWNLOADS_AND_QUEUE))
//        } else {
//            context?.startService(Intent(context, VideoDownloadKeepAliveService::class.java).putExtra(START_VALUE_KEY, RESTART_ALL_DOWNLOADS_AND_QUEUE))
//        }
    }
}