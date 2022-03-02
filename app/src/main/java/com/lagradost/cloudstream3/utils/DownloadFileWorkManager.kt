package com.lagradost.cloudstream3.utils

import android.app.Notification
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.lagradost.cloudstream3.AcraApplication.Companion.removeKey
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.Coroutines.main
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.VideoDownloadManager.WORK_KEY_INFO
import com.lagradost.cloudstream3.utils.VideoDownloadManager.WORK_KEY_PACKAGE
import com.lagradost.cloudstream3.utils.VideoDownloadManager.downloadCheck
import com.lagradost.cloudstream3.utils.VideoDownloadManager.downloadEpisode
import com.lagradost.cloudstream3.utils.VideoDownloadManager.downloadFromResume
import com.lagradost.cloudstream3.utils.VideoDownloadManager.downloadStatusEvent
import kotlinx.coroutines.delay

const val DOWNLOAD_CHECK = "DownloadCheck"

class DownloadFileWorkManager(val context: Context, private val workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val key = workerParams.inputData.getString("key")
        try {
            println("KEY $key")
            if (key == DOWNLOAD_CHECK) {
                downloadCheck(applicationContext, ::handleNotification)?.let {
                    awaitDownload(it)
                }
            } else if (key != null) {
                val info = applicationContext.getKey<VideoDownloadManager.DownloadInfo>(WORK_KEY_INFO, key)
                val pkg =
                    applicationContext.getKey<VideoDownloadManager.DownloadResumePackage>(WORK_KEY_PACKAGE, key)
                if (info != null) {
                    downloadEpisode(
                        applicationContext,
                        info.source,
                        info.folder,
                        info.ep,
                        info.links,
                        ::handleNotification
                    )
                    awaitDownload(info.ep.id)
                } else if (pkg != null) {
                    downloadFromResume(applicationContext, pkg, ::handleNotification)
                    awaitDownload(pkg.item.ep.id)
                }
                removeKeys(key)
            }
            return Result.success()
        } catch (e: Exception) {
            logError(e)
            if (key != null) {
                removeKeys(key)
            }
            return Result.failure()
        }
    }

    private fun removeKeys(key: String) {
        removeKey(WORK_KEY_INFO, key)
        removeKey(WORK_KEY_PACKAGE, key)
    }

    private suspend fun awaitDownload(id: Int) {
        var isDone = false
        val listener = { (localId, localType): Pair<Int, VideoDownloadManager.DownloadType> ->
            if (id == localId) {
                when (localType) {
                    VideoDownloadManager.DownloadType.IsDone, VideoDownloadManager.DownloadType.IsFailed, VideoDownloadManager.DownloadType.IsStopped -> {
                        isDone = true
                    }
                    else -> Unit
                }
            }
        }
        downloadStatusEvent += listener
        while (!isDone) {
            println("AWAITING $id")
            delay(1000)
        }
        downloadStatusEvent -= listener
    }

    private fun handleNotification(id: Int, notification: Notification) {
        main {
            setForegroundAsync(ForegroundInfo(id, notification))
        }
    }
}