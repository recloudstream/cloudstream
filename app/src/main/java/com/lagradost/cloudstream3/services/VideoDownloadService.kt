package com.lagradost.cloudstream3.services
import android.app.Service
import android.content.Intent
import android.os.IBinder
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

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        downloadScope.coroutineContext.cancel()
        super.onDestroy()
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
