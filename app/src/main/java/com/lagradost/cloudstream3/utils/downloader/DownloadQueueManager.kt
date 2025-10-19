package com.lagradost.cloudstream3.utils.downloader

import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import com.lagradost.cloudstream3.AcraApplication
import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.AcraApplication.Companion.getKeys
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import com.lagradost.cloudstream3.services.DownloadQueueService
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.downloader.VideoDownloadManager.KEY_RESUME_PACKAGES
import com.lagradost.cloudstream3.utils.downloader.VideoDownloadManager.downloadStatus
import com.lagradost.cloudstream3.utils.downloader.VideoDownloadManager.downloadStatusEvent
import com.lagradost.cloudstream3.utils.downloader.VideoDownloadManager.getDownloadResumePackage
import com.lagradost.cloudstream3.utils.downloader.VideoDownloadObjects.DownloadQueueWrapper
import kotlin.collections.filter
import kotlin.collections.forEach

// 1. Put a download on the queue
// 2. The queue manager starts a foreground service to handle the queue
// 3. The service starts work manager jobs to handle the downloads?
object DownloadQueueManager {
    private const val TAG = "DownloadQueueManager"
    private const val QUEUE_KEY = "download_queue_key"

    /** Start the queue, marks all queue objects as in progress.
     * Note that this may run twice without the service restarting
     * because MainActivity may be recreated. */
    fun init(context: Context) {
        ioSafe {
            val resumePackages =
                // We do not want to resume downloads already downloading
                resumeIds.filterNot { VideoDownloadManager.currentDownloads.contains(it) }
                    .mapNotNull { id ->
                        getDownloadResumePackage(context, id)?.toWrapper()
                    }

            synchronized(queue) {
                // Add resume packages to the first part of the queue, since they may have been removed from the queue when they started
                queue = (resumePackages + queue).distinctBy { it.id }.toTypedArray()
                // Make sure the download buttons display a pending status
                queue.forEach { obj ->
                    setQueueStatus(obj)
                }
                if (queue.any()) {
                    startQueueService(context)
                }
            }
        }
    }

    val resumeIds: Set<Int>
        get() {
            return getKeys(KEY_RESUME_PACKAGES)?.mapNotNull {
                it.substringAfter("$KEY_RESUME_PACKAGES/").toIntOrNull()
            }?.toSet()
                ?: emptySet()
        }

    /** Persistent queue */
    var queue: Array<DownloadQueueWrapper>
        get() {
            return getKey<Array<DownloadQueueWrapper>>(QUEUE_KEY) ?: emptyArray()
        }
        private set(value) {
            setKey(QUEUE_KEY, value)
        }

    /** Adds an object to the internal persistent queue. It does not re-add an existing item.  */
    private fun add(downloadQueueWrapper: DownloadQueueWrapper) {
        synchronized(queue) {
            val localQueue = queue

            // Do not add the same episode twice
            if (localQueue.any { it.id == downloadQueueWrapper.id }) {
                return
            }

            queue = localQueue + downloadQueueWrapper
        }
    }

    /** Removes all objects with the same id from the internal persistent queue */
    private fun remove(downloadQueueWrapper: DownloadQueueWrapper) {
        synchronized(queue) {
            val localQueue = queue
            val id = downloadQueueWrapper.id
            queue = localQueue.filter { it.id != id }.toTypedArray()
        }
    }

    /** Start a real download from the first item in the queue */
    fun popQueue(context: Context): VideoDownloadManager.EpisodeDownloadInstance? {
        val first = synchronized(queue) {
            queue.firstOrNull()
        } ?: return null
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

    /** Add a new object to the queue */
    fun addToQueue(downloadQueueWrapper: DownloadQueueWrapper) {
        ioSafe {
            add(downloadQueueWrapper)
            setQueueStatus(downloadQueueWrapper)
            startQueueService(AcraApplication.context)
        }
    }
}