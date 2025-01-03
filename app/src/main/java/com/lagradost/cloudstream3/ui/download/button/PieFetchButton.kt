package com.lagradost.cloudstream3.ui.download.button

import android.content.Context
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.MainThread
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.lagradost.cloudstream3.AcraApplication.Companion.removeKey
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.ui.download.DOWNLOAD_ACTION_DELETE_FILE
import com.lagradost.cloudstream3.ui.download.DOWNLOAD_ACTION_DOWNLOAD
import com.lagradost.cloudstream3.ui.download.DOWNLOAD_ACTION_LONG_CLICK
import com.lagradost.cloudstream3.ui.download.DOWNLOAD_ACTION_PAUSE_DOWNLOAD
import com.lagradost.cloudstream3.ui.download.DOWNLOAD_ACTION_PLAY_FILE
import com.lagradost.cloudstream3.ui.download.DOWNLOAD_ACTION_RESUME_DOWNLOAD
import com.lagradost.cloudstream3.ui.download.DownloadClickEvent
import com.lagradost.cloudstream3.utils.UIHelper.popupMenuNoIcons
import com.lagradost.cloudstream3.utils.VideoDownloadHelper
import com.lagradost.cloudstream3.utils.VideoDownloadManager
import com.lagradost.cloudstream3.utils.VideoDownloadManager.KEY_RESUME_PACKAGES

open class PieFetchButton(context: Context, attributeSet: AttributeSet) :
    BaseFetchButton(context, attributeSet) {

    private var waitingAnimation: Int = 0
    private var animateWaiting: Boolean = false
    private var activeOutline: Int = 0
    private var nonActiveOutline: Int = 0

    private var iconInit: Int = 0
    private var iconError: Int = 0
    private var iconComplete: Int = 0
    private var iconActive: Int = 0
    private var iconWaiting: Int = 0
    private var iconRemoved: Int = 0
    private var iconPaused: Int = 0
    private var hideWhenIcon: Boolean = true

    var progressDrawable: Int = 0

    var overrideLayout: Int? = null

    companion object {
        val fillArray = arrayOf(
            R.drawable.circular_progress_bar_clockwise,
            R.drawable.circular_progress_bar_counter_clockwise,
            R.drawable.circular_progress_bar_small_to_large,
            R.drawable.circular_progress_bar_top_to_bottom,
        )
    }

    private var progressBarBackground: View
    var statusView: ImageView

    open fun onInflate() {}

    init {
        context.obtainStyledAttributes(attributeSet, R.styleable.PieFetchButton, 0, 0).apply {
            try {
                inflate(
                    overrideLayout ?: getResourceId(
                        R.styleable.PieFetchButton_download_layout,
                        R.layout.download_button_view
                    )
                )
            } catch (e: Exception) {
                Log.e(
                    "PieFetchButton", "Error inflating PieFetchButton, " +
                            "check that you have declared the required aria2c attrs: aria2c_icon_scale aria2c_icon_color aria2c_outline_color aria2c_fill_color"
                )
                throw e
            }


            progressBar = findViewById(R.id.progress_downloaded)
            progressBarBackground = findViewById(R.id.progress_downloaded_background)
            statusView = findViewById(R.id.image_download_status)

            animateWaiting = getBoolean(
                R.styleable.PieFetchButton_download_animate_waiting,
                true
            )
            hideWhenIcon = getBoolean(
                R.styleable.PieFetchButton_download_hide_when_icon,
                true
            )

            waitingAnimation = getResourceId(
                R.styleable.PieFetchButton_download_waiting_animation,
                R.anim.rotate_around_center_point
            )

            activeOutline = getResourceId(
                R.styleable.PieFetchButton_download_outline_active, R.drawable.circle_shape
            )

            nonActiveOutline = getResourceId(
                R.styleable.PieFetchButton_download_outline_non_active,
                R.drawable.circle_shape_dotted
            )
            iconInit = getResourceId(
                R.styleable.PieFetchButton_download_icon_init, R.drawable.netflix_download
            )
            iconError = getResourceId(
                R.styleable.PieFetchButton_download_icon_paused, R.drawable.download_icon_error
            )
            iconComplete = getResourceId(
                R.styleable.PieFetchButton_download_icon_complete, R.drawable.download_icon_done
            )
            iconPaused = getResourceId(
                R.styleable.PieFetchButton_download_icon_paused, 0 // R.drawable.download_icon_pause
            )
            iconActive = getResourceId(
                R.styleable.PieFetchButton_download_icon_active, 0 // R.drawable.download_icon_load
            )
            iconWaiting = getResourceId(
                R.styleable.PieFetchButton_download_icon_waiting, 0
            )
            iconRemoved = getResourceId(
                R.styleable.PieFetchButton_download_icon_removed, R.drawable.netflix_download
            )

            val fillIndex = getInt(R.styleable.PieFetchButton_download_fill, 0)

            progressDrawable = getResourceId(
                R.styleable.PieFetchButton_download_fill_override, fillArray[fillIndex]
            )

            progressBar.progressDrawable = ContextCompat.getDrawable(context, progressDrawable)

            recycle()
        }
        resetView()
        onInflate()
    }

    private var currentStatus: DownloadStatusTell? = null
    /*private fun getActivity(): Activity? {
        var context = context
        while (context is ContextWrapper) {
            if (context is Activity) {
                return context
            }
            context = context.baseContext
        }
        return null
    }

    fun callback(event : DownloadClickEvent) {
        handleDownloadClick(
            getActivity(),
            event
        )
    }*/

    protected fun setDefaultClickListener(
        view: View, textView: TextView?, card: VideoDownloadHelper.DownloadEpisodeCached,
        callback: (DownloadClickEvent) -> Unit
    ) {
        this.progressText = textView
        this.setPersistentId(card.id)
        view.setOnClickListener {
            if (isZeroBytes) {
                removeKey(KEY_RESUME_PACKAGES, card.id.toString())
                callback(DownloadClickEvent(DOWNLOAD_ACTION_DOWNLOAD, card))
                // callback.invoke(DownloadClickEvent(DOWNLOAD_ACTION_DOWNLOAD, data))
            } else {
                val list = arrayListOf(
                    Pair(DOWNLOAD_ACTION_PLAY_FILE, R.string.popup_play_file),
                    Pair(DOWNLOAD_ACTION_DELETE_FILE, R.string.popup_delete_file),
                )

                currentMetaData.apply {
                    // DON'T RESUME A DOWNLOADED FILE lastState != VideoDownloadManager.DownloadType.IsDone &&
                    if (progressPercentage < 98) {
                        list.add(
                            if (status == VideoDownloadManager.DownloadType.IsDownloading)
                                Pair(DOWNLOAD_ACTION_PAUSE_DOWNLOAD, R.string.popup_pause_download)
                            else
                                Pair(
                                    DOWNLOAD_ACTION_RESUME_DOWNLOAD,
                                    R.string.popup_resume_download
                                )
                        )
                    }
                }


                it.popupMenuNoIcons(
                    list
                ) {
                    callback(DownloadClickEvent(itemId, card))
                    // callback.invoke(DownloadClickEvent(itemId, data))
                }
            }
        }

        view.setOnLongClickListener {
            callback(DownloadClickEvent(DOWNLOAD_ACTION_LONG_CLICK, card))

            // clickCallback.invoke(DownloadClickEvent(DOWNLOAD_ACTION_LONG_CLICK, data))
            return@setOnLongClickListener true
        }
    }

    open fun setDefaultClickListener(
        card: VideoDownloadHelper.DownloadEpisodeCached,
        textView: TextView?,
        callback: (DownloadClickEvent) -> Unit
    ) {
        setDefaultClickListener(this, textView, card, callback)
    }

    /* open fun setDefaultClickListener(requestGetter: suspend BaseFetchButton.() -> List<UriRequest>) {
        this.setOnClickListener {
            when (this.currentStatus) {
                null -> {
                    setStatus(DownloadStatusTell.IsPending)
                    ioThread {
                        val request = requestGetter.invoke(this)
                        if (request.size == 1) {
                            performDownload(request.first())
                        } else if (request.isNotEmpty()) {
                            performFailQueueDownload(request)
                        }
                    }
                }
                DownloadStatusTell.Paused -> {
                    resumeDownload()
                }
                DownloadStatusTell.Active -> {
                    pauseDownload()
                }
                DownloadStatusTell.Error -> {
                    redownload()
                }
                else -> {}
            }
        }
    } */

    @MainThread
    private fun setStatusInternal(status: DownloadStatusTell?) {
        val isPreActive = isZeroBytes && status == DownloadStatusTell.IsDownloading
        if (animateWaiting && (status == DownloadStatusTell.IsPending || isPreActive)) {
            val animation = AnimationUtils.loadAnimation(context, waitingAnimation)
            progressBarBackground.startAnimation(animation)
        } else {
            progressBarBackground.clearAnimation()
        }

        val progressDrawable =
            if (status == DownloadStatusTell.IsDownloading && !isPreActive) activeOutline else nonActiveOutline

        progressBarBackground.background =
            ContextCompat.getDrawable(context, progressDrawable)

        val drawable =
            getDrawableFromStatus(status)?.let { ContextCompat.getDrawable(this.context, it) }
        statusView.setImageDrawable(drawable)
        val isDrawable = drawable != null

        statusView.isVisible = isDrawable
        val hide = hideWhenIcon && isDrawable
        if (hide) {
            progressBar.clearAnimation()
            progressBarBackground.clearAnimation()
        }
        progressBarBackground.isGone = hide
        progressBar.isGone = hide
    }

    /** Also sets currentStatus */
    override fun setStatus(status: DownloadStatusTell?) {
        currentStatus = status

        // Runs on the main thread, but also instant if it already is
        if (Looper.myLooper() == Looper.getMainLooper()) {
            try {
                setStatusInternal(status)
            } catch (t: Throwable) {
                logError(t) // Just in case setStatusInternal throws because thread
                progressBarBackground.post {
                    setStatusInternal(status)
                }
            }
        } else {
            progressBarBackground.post {
                setStatusInternal(status)
            }
        }
    }

    override fun resetView() {
        setStatus(null)
        currentMetaData = DownloadMetadata(0, 0, 0, null)
        isZeroBytes = true
        doSetProgress = true
        progressBar.progress = 0
    }

    override fun updateViewOnDownload(metadata: DownloadMetadata) {

        val newStatus = metadata.status

        if (newStatus == null) {
            resetView()
            return
        }

        val isDone =
            newStatus == DownloadStatusTell.IsDone || (metadata.downloadedLength > 1024 && metadata.downloadedLength + 1024 >= metadata.totalLength)

        if (isDone)
            setStatus(DownloadStatusTell.IsDone)
        else {
            setProgress(metadata.downloadedLength, metadata.totalLength)
            setStatus(newStatus)
        }
    }

    open fun getDrawableFromStatus(status: DownloadStatusTell?): Int? = when (status) {
        DownloadStatusTell.IsPaused -> iconPaused
        DownloadStatusTell.IsPending -> iconWaiting
        DownloadStatusTell.IsDownloading -> iconActive
        DownloadStatusTell.IsFailed -> iconError
        DownloadStatusTell.IsDone -> iconComplete
        DownloadStatusTell.IsStopped -> iconRemoved
        else -> iconInit
    }.takeIf { it != 0 }
}