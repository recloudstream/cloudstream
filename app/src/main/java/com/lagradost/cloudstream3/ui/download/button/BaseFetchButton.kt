package com.lagradost.cloudstream3.ui.download.button

import android.content.Context
import android.text.format.Formatter.formatShortFileSize
import android.util.AttributeSet
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.core.view.isVisible
import androidx.core.widget.ContentLoadingProgressBar
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.Coroutines.mainWork
import com.lagradost.cloudstream3.utils.VideoDownloadManager

typealias DownloadStatusTell = VideoDownloadManager.DownloadType

data class DownloadMetadata(
    var id: Int,
    var downloadedLength: Long,
    var totalLength: Long,
    var status: DownloadStatusTell? = null
) {
    val progressPercentage: Long
        get() = if (downloadedLength < 1024) 0 else maxOf(
            0,
            minOf(100, (downloadedLength * 100L) / (totalLength + 1))
        )
}

abstract class BaseFetchButton(context: Context, attributeSet: AttributeSet) :
    FrameLayout(context, attributeSet) {

    var persistentId: Int? = null // used to save sessions

    lateinit var progressBar: ContentLoadingProgressBar
    var progressText: TextView? = null

    /* val gid: String? get() = sessionIdToGid[persistentId]

    // used for resuming data
    var _lastRequestOverride: UriRequest? = null
    var lastRequest: UriRequest?
        get() = _lastRequestOverride ?: sessionIdToLastRequest[persistentId]
        set(value) {
            _lastRequestOverride = value
        }

    var files: List<AbstractClient.JsonFile> = emptyList() */
    protected var isZeroBytes: Boolean = true

    fun inflate(@LayoutRes layout: Int) {
        inflate(context, layout, this)
    }

    init {
        @Suppress("LeakingThis")
        resetViewData()
    }

    var doSetProgress = true

    open fun resetViewData() {
        // lastRequest = null
        isZeroBytes = true
        doSetProgress = true
        persistentId = null
    }

    var currentMetaData: DownloadMetadata =
        DownloadMetadata(0, 0, 0, null)

    fun setPersistentId(id: Int) {
        persistentId = id
        currentMetaData.id = id

        if (!doSetProgress) return

        ioSafe {
            val savedData = VideoDownloadManager.getDownloadFileInfoAndUpdateSettings(context, id)

            mainWork {
                if (savedData != null) {
                    val downloadedBytes = savedData.fileLength
                    val totalBytes = savedData.totalBytes

                    setProgress(downloadedBytes, totalBytes)
                    applyMetaData(id, downloadedBytes, totalBytes)
                } else run { resetView() }
            }
        }
    }

    abstract fun setStatus(status: VideoDownloadManager.DownloadType?)

    fun getStatus(id: Int, downloadedBytes: Long, totalBytes: Long): DownloadStatusTell {
        // some extra padding for just in case
        return VideoDownloadManager.downloadStatus[id]
            ?: if (downloadedBytes > 1024L && downloadedBytes + 1024L >= totalBytes) {
                DownloadStatusTell.IsDone
            } else DownloadStatusTell.IsPaused
    }

    fun applyMetaData(id: Int, downloadedBytes: Long, totalBytes: Long) {
        val status = getStatus(id, downloadedBytes, totalBytes)

        currentMetaData.apply {
            this.id = id
            this.downloadedLength = downloadedBytes
            this.totalLength = totalBytes
            this.status = status
        }
        setStatus(status)
    }

    open fun setProgress(downloadedBytes: Long, totalBytes: Long) {
        isZeroBytes = downloadedBytes == 0L
        progressBar.post {
            val steps = 10000L
            progressBar.max = steps.toInt()
            // div by zero error and 1 byte off is ok impo

            val progress = (downloadedBytes * steps / (totalBytes + 1L)).toInt()

            val animation = ProgressBarAnimation(
                progressBar,
                progressBar.progress.toFloat(),
                progress.toFloat()
            ).apply {
                fillAfter = true
                duration =
                    if (progress > progressBar.progress) // we don't want to animate backward changes in progress
                        100
                    else
                        0L
            }

            if (isZeroBytes) {
                progressText?.isVisible = false
            } else {
                if (doSetProgress) {
                    progressText?.apply {
                        val currentFormattedSizeString =
                            formatShortFileSize(context, downloadedBytes)
                        val totalFormattedSizeString = formatShortFileSize(context, totalBytes)
                        text =
                                // if (isTextPercentage) "%d%%".format(setCurrentBytes * 100L / setTotalBytes) else
                            context?.getString(R.string.download_size_format)
                                ?.format(currentFormattedSizeString, totalFormattedSizeString)
                    }
                }
            }

            progressBar.startAnimation(animation)
        }
    }

    fun downloadStatusEvent(data: Pair<Int, VideoDownloadManager.DownloadType>) {
        val (id, status) = data
        if (id == persistentId) {
            currentMetaData.status = status
            setStatus(status)
        }
    }

    /*fun downloadDeleteEvent(data: Int) {

    }*/

    /*fun downloadEvent(data: Pair<Int, VideoDownloadManager.DownloadActionType>) {
        val (id, action) = data

    }*/

    fun downloadProgressEvent(data: Triple<Int, Long, Long>) {
        val (id, bytesDownloaded, bytesTotal) = data
        if (id == persistentId) {
            currentMetaData.downloadedLength = bytesDownloaded
            currentMetaData.totalLength = bytesTotal

            setProgress(bytesDownloaded, bytesTotal)
        }
    }

    override fun onAttachedToWindow() {
        VideoDownloadManager.downloadStatusEvent += ::downloadStatusEvent
        // VideoDownloadManager.downloadDeleteEvent += ::downloadDeleteEvent
        // VideoDownloadManager.downloadEvent += ::downloadEvent
        VideoDownloadManager.downloadProgressEvent += ::downloadProgressEvent

        val pid = persistentId
        if (pid != null) {
            // refresh in case of onDetachedFromWindow -> onAttachedToWindow while still being ???????
            setPersistentId(pid)
        }

        super.onAttachedToWindow()
    }

    override fun onDetachedFromWindow() {
        VideoDownloadManager.downloadStatusEvent -= ::downloadStatusEvent
        // VideoDownloadManager.downloadDeleteEvent -= ::downloadDeleteEvent
        // VideoDownloadManager.downloadEvent -= ::downloadEvent
        VideoDownloadManager.downloadProgressEvent -= ::downloadProgressEvent

        super.onDetachedFromWindow()
    }

    /**
     * No checks required. Arg will always include a download with current id
     * */
    abstract fun updateViewOnDownload(metadata: DownloadMetadata)

    /**
     * Get a clean slate again, might be useful in recyclerview?
     * */
    abstract fun resetView()
}