package com.lagradost.cloudstream3.utils.downloader

import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import com.lagradost.cloudstream3.AcraApplication
import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.AcraApplication.Companion.getKeys
import com.lagradost.cloudstream3.AcraApplication.Companion.removeKeys
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import com.lagradost.cloudstream3.services.DownloadQueueService
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.downloader.DownloadObjects.DownloadQueueWrapper
import com.lagradost.cloudstream3.utils.downloader.VideoDownloadManager.KEY_PRE_RESUME
import com.lagradost.cloudstream3.utils.downloader.VideoDownloadManager.KEY_RESUME_PACKAGES
import com.lagradost.cloudstream3.utils.downloader.VideoDownloadManager.downloadStatus
import com.lagradost.cloudstream3.utils.downloader.VideoDownloadManager.downloadStatusEvent
import com.lagradost.cloudstream3.utils.downloader.VideoDownloadManager.getDownloadFileInfo
import com.lagradost.cloudstream3.utils.downloader.VideoDownloadManager.getDownloadResumePackage
import com.lagradost.cloudstream3.utils.downloader.VideoDownloadManager.getPreDownloadResumePackage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet

// 1. Put a download on the queue
// 2. The queue manager starts a foreground service to handle the queue
// 3. The service starts work manager jobs to handle the downloads?
object DownloadQueueManager {
    private const val TAG = "DownloadQueueManager"
    private const val QUEUE_KEY = "download_queue_key"

    private val _queue: MutableStateFlow<Array<DownloadQueueWrapper>> by lazy {
        /** Persistent queue */
        val currentValue = getKey<Array<DownloadQueueWrapper>>(QUEUE_KEY) ?: emptyArray()
        MutableStateFlow(currentValue)
    }

    val queue: StateFlow<Array<DownloadQueueWrapper>> by lazy { _queue }

    /** Start the queue, marks all queue objects as in progress.
     * Note that this may run twice without the service restarting
     * because MainActivity may be recreated. */
    fun init(context: Context) {
        ioSafe {
            _queue.collect { queue ->
                setKey(QUEUE_KEY, queue)
            }
        }

        ioSafe {
            val resumePackages =
                // We do not want to resume downloads already downloading
                getResumeIds().filterNot { VideoDownloadManager.currentDownloads.value.contains(it) }
                    .mapNotNull { id ->
                        getDownloadResumePackage(context, id)?.toWrapper()
                    }

            val resumeQueue =
                getPreResumeIds().filterNot {
                    VideoDownloadManager.currentDownloads.value.contains(it)
                }
                    .mapNotNull { id ->
                        getPreDownloadResumePackage(context, id)
                    }

            val newQueue = _queue.updateAndGet { localQueue ->
                // Add resume packages to the first part of the queue, since they may have been removed from the queue when they started
                (resumePackages + resumeQueue + localQueue).distinctBy { it.id }.toTypedArray()
            }

            // Once added to the queue they can be safely removed
            removeKeys(KEY_PRE_RESUME)

            // Make sure the download buttons display a pending status
            newQueue.forEach { obj ->
                setQueueStatus(obj)
            }
            if (newQueue.any()) {
                startQueueService(context)
            }
        }
    }

    fun getResumeIds(): Set<Int> {
        return getKeys(KEY_RESUME_PACKAGES)?.mapNotNull {
            it.substringAfter("$KEY_RESUME_PACKAGES/").toIntOrNull()
        }?.toSet()
            ?: emptySet()
    }

    /** Downloads not yet started, but forcefully stopped by app closure. */
    private fun getPreResumeIds(): Set<Int> {
        return getKeys(KEY_PRE_RESUME)?.mapNotNull {
            it.substringAfter("$KEY_PRE_RESUME/").toIntOrNull()
        }?.toSet()
            ?: emptySet()
    }

    /** Adds an object to the internal persistent queue. It does not re-add an existing item.  */
    private fun add(downloadQueueWrapper: DownloadQueueWrapper) {
        Log.d(TAG, "Download added to queue: $downloadQueueWrapper")
        _queue.update { localQueue ->
            // Do not add the same episode twice
            if (localQueue.any { it.id == downloadQueueWrapper.id }) {
                return@update localQueue
            }
            localQueue + downloadQueueWrapper
        }
    }

    /** Removes all objects with the same id from the internal persistent queue */
    private fun remove(downloadQueueWrapper: DownloadQueueWrapper) {
        Log.d(TAG, "Download removed from the queue: $downloadQueueWrapper")
        _queue.update { localQueue ->
            val id = downloadQueueWrapper.id
            localQueue.filter { it.id != id }.toTypedArray()
        }
    }

    /** Start a real download from the first item in the queue */
    fun popQueue(context: Context): VideoDownloadManager.EpisodeDownloadInstance? {
        val first = queue.value.firstOrNull() ?: return null

        remove(first)

        val downloadInstance = VideoDownloadManager.EpisodeDownloadInstance(context, first)

        return downloadInstance
    }

    /** Marks the item as in queue for the download button */
    private fun setQueueStatus(downloadQueueWrapper: DownloadQueueWrapper) {
        downloadStatusEvent.invoke(
            Pair(
                downloadQueueWrapper.id,
                VideoDownloadManager.DownloadType.IsPending
            )
        )
        downloadStatus[downloadQueueWrapper.id] = VideoDownloadManager.DownloadType.IsPending
    }

    private fun startQueueService(context: Context?) {
        if (context == null) {
            Log.d(TAG, "Cannot start download queue service, null context.")
            return
        }
        // Do not restart the download queue service
        // TODO Prevent issues where the service is closed right as a user starts a new download
        if (DownloadQueueService.isRunning) {
            return
        }
        ioSafe {
            val intent = DownloadQueueService.getIntent(context)
            ContextCompat.startForegroundService(context, intent)
        }
    }

    /** Add a new object to the queue. Will not queue completed downloads or current downloads. */
    fun addToQueue(downloadQueueWrapper: DownloadQueueWrapper) {
        ioSafe {
            val context = AcraApplication.context ?: return@ioSafe
            val fileInfo = getDownloadFileInfo(context, downloadQueueWrapper.id)
            val isComplete = fileInfo != null && (fileInfo.fileLength <= fileInfo.totalBytes)
            // Do not queue completed files!
            if (isComplete) return@ioSafe
            // Do not queue already downloading files
            if (VideoDownloadManager.currentDownloads.value.contains(downloadQueueWrapper.id)) {
                return@ioSafe
            }

            add(downloadQueueWrapper)
            setQueueStatus(downloadQueueWrapper)
            startQueueService(context)
        }
    }
}