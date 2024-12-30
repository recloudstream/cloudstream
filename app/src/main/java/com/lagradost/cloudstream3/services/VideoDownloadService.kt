package com.lagradost.cloudstream3.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.lagradost.cloudstream3.utils.VideoDownloadManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class VideoDownloadService : Service() {

    private val downloadScope = CoroutineScope(Dispatchers.Default)

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            val id = intent.getIntExtra("id", -1)
            val type = intent.getStringExtra("type")
            if (id != -1 && type != null) {
                val state = when (type) {
                    "resume" -> VideoDownloadManager.DownloadActionType.Resume
                    "pause" -> VideoDownloadManager.DownloadActionType.Pause
                    "stop" -> VideoDownloadManager.DownloadActionType.Stop
                    else -> return START_NOT_STICKY
                }

                downloadScope.launch {
                    VideoDownloadManager.downloadEvent.invoke(Pair(id, state))
                }
            }
        }

        VideoDownloadManager.startServiceForeground(this)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        downloadScope.coroutineContext.cancel()
        super.onDestroy()
    }

    override fun onTimeout(reason: Int) {
        stopSelf()
        Log.e("VideoDownloadService", "Service stopped due to timeout: $reason")
    }
}
//    override fun onHandleIntent(intent: Intent?) {
//        if (intent != null) {
//            val id = intent.getIntExtra("id", -1)
//            val type = intent.getStringExtra("type")
//            if (id != -1 && type != null) {
//                val state = when (type) {
//                    "resume" -> VideoDownloadManager.DownloadActionType.Resume
//                    "pause" -> VideoDownloadManager.DownloadActionType.Pause
//                    "stop" -> VideoDownloadManager.DownloadActionType.Stop
//                    else -> return
//                }
//                VideoDownloadManager.downloadEvent.invoke(Pair(id, state))
//            }
//        }
//    }
//}
