package com.lagradost.cloudstream3.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.lagradost.cloudstream3.recivers.VideoDownloadRestartReceiver
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.DataStore.getKeys
import com.lagradost.cloudstream3.utils.VideoDownloadManager

const val RESTART_ALL_DOWNLOADS_AND_QUEUE = 1
const val RESTART_NONE = 0
const val START_VALUE_KEY = "start_value"

class VideoDownloadKeepAliveService : Service() {
    override fun onBind(p0: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val startValue = intent?.getIntExtra(START_VALUE_KEY, RESTART_NONE) ?: RESTART_NONE
        Log.i("Service", "Restarted with start value of $startValue")

        if (startValue == RESTART_ALL_DOWNLOADS_AND_QUEUE) {
            val keys = this.getKeys(VideoDownloadManager.KEY_RESUME_PACKAGES)
            val resumePkg = keys.mapNotNull { k -> this.getKey<VideoDownloadManager.DownloadResumePackage>(k) }

            for (pkg in resumePkg) { // ADD ALL CURRENT DOWNLOADS
                VideoDownloadManager.downloadFromResume(this, pkg)
            }

            // ADD QUEUE
            val resumeQueue =
                this.getKey<List<VideoDownloadManager.DownloadQueueResumePackage>>(VideoDownloadManager.KEY_RESUME_QUEUE_PACKAGES)
            if (resumeQueue != null && resumeQueue.isNotEmpty()) {
                val sorted = resumeQueue.sortedBy { item -> item.index }
                for (queueItem in sorted) {
                    VideoDownloadManager.downloadFromResume(this, queueItem.pkg)
                }
            }
        }

        return START_STICKY//super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        val broadcastIntent = Intent()
        broadcastIntent.action = "restart_service"
        broadcastIntent.setClass(this, VideoDownloadRestartReceiver::class.java)
        this.sendBroadcast(broadcastIntent)
        super.onDestroy()
    }
}