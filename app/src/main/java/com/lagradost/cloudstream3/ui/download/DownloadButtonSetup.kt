package com.lagradost.cloudstream3.ui.download

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.DialogInterface
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.ContentLoadingProgressBar
import androidx.fragment.app.FragmentActivity
import com.google.android.material.button.MaterialButton
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.UIHelper.popupMenuNoIcons
import com.lagradost.cloudstream3.ui.player.PlayerFragment
import com.lagradost.cloudstream3.ui.player.UriData
import com.lagradost.cloudstream3.utils.Coroutines
import com.lagradost.cloudstream3.utils.DataStoreHelper.getViewPos
import com.lagradost.cloudstream3.utils.VideoDownloadHelper
import com.lagradost.cloudstream3.utils.VideoDownloadManager

object DownloadButtonSetup {
    fun handleDownloadClick(activity: Activity?, headerName: String?, click: DownloadClickEvent) {
        val id = click.data.id
        when (click.action) {
            DOWNLOAD_ACTION_DELETE_FILE -> {
                activity?.let { ctx ->
                    val builder: AlertDialog.Builder = AlertDialog.Builder(ctx)
                    val dialogClickListener =
                        DialogInterface.OnClickListener { _, which ->
                            when (which) {
                                DialogInterface.BUTTON_POSITIVE -> {
                                    VideoDownloadManager.deleteFileAndUpdateSettings(ctx, id)
                                }
                                DialogInterface.BUTTON_NEGATIVE -> {
                                }
                            }
                        }

                    builder.setTitle("Delete File") //TODO FIX NAME
                    builder.setMessage("This will permanently delete ${click.data.name ?: "Episode ${click.data.episode}"}\nAre you sure?")
                        .setTitle("Delete")
                        .setPositiveButton("Delete", dialogClickListener)
                        .setNegativeButton("Cancel", dialogClickListener)
                        .show()
                }
            }
            DOWNLOAD_ACTION_PAUSE_DOWNLOAD -> {
                VideoDownloadManager.downloadEvent.invoke(
                    Pair(click.data.id, VideoDownloadManager.DownloadActionType.Pause)
                )
            }
            DOWNLOAD_ACTION_RESUME_DOWNLOAD -> {
                activity?.let { ctx ->
                    val pkg = VideoDownloadManager.getDownloadResumePackage(ctx, id)
                    if (pkg != null) {
                        VideoDownloadManager.downloadFromResume(ctx, pkg)
                    } else {
                        VideoDownloadManager.downloadEvent.invoke(
                            Pair(click.data.id, VideoDownloadManager.DownloadActionType.Resume)
                        )
                    }
                }
            }
            DOWNLOAD_ACTION_PLAY_FILE -> {
                activity?.let { act ->
                    val info =
                        VideoDownloadManager.getDownloadFileInfoAndUpdateSettings(act, click.data.id)
                            ?: return

                    (act as FragmentActivity).supportFragmentManager.beginTransaction()
                        .setCustomAnimations(
                            R.anim.enter_anim,
                            R.anim.exit_anim,
                            R.anim.pop_enter,
                            R.anim.pop_exit
                        )
                        .add(
                            R.id.homeRoot,
                            PlayerFragment.newInstance(
                                UriData(
                                    info.path.toString(),
                                    click.data.id,
                                    headerName ?: "null",
                                    if (click.data.episode <= 0) null else click.data.episode,
                                    click.data.season
                                ),
                                act.getViewPos(click.data.id)?.position ?: 0
                            )
                        )
                        .commit()
                }
            }
        }
    }

    fun setUpDownloadButton(
        setupCurrentBytes: Long?,
        setupTotalBytes: Long?,
        progressBar: ContentLoadingProgressBar,
        textView: TextView?,
        data: VideoDownloadHelper.DownloadEpisodeCached,
        downloadView: View,
        downloadImageChangeCallback: (Pair<Int, String>) -> Unit,
        clickCallback: (DownloadClickEvent) -> Unit,
    ): () -> Unit {
        var lastState: VideoDownloadManager.DownloadType? = null
        var currentBytes = setupCurrentBytes ?: 0
        var totalBytes = setupTotalBytes ?: 0
        var needImageUpdate = false

        fun changeDownloadImage(state: VideoDownloadManager.DownloadType) {
            lastState = state
            if (currentBytes <= 0) needImageUpdate = true
            val img = if (currentBytes > 0) {
                when (state) {
                    VideoDownloadManager.DownloadType.IsPaused -> Pair(
                        R.drawable.ic_baseline_play_arrow_24,
                        "Download Paused"
                    )
                    VideoDownloadManager.DownloadType.IsDownloading -> Pair(R.drawable.netflix_pause, "Downloading")
                    else -> Pair(R.drawable.ic_baseline_delete_outline_24, "Downloaded")
                }
            } else {
                Pair(R.drawable.netflix_download, "Download")
            }
            downloadImageChangeCallback.invoke(img)
        }

        @SuppressLint("SetTextI18n")
        fun fixDownloadedBytes(setCurrentBytes: Long, setTotalBytes: Long, animate: Boolean) {
            currentBytes = setCurrentBytes
            totalBytes = setTotalBytes

            if (currentBytes == 0L) {
                changeDownloadImage(VideoDownloadManager.DownloadType.IsStopped)
                textView?.visibility = View.GONE
                progressBar?.visibility = View.GONE
            } else {
                if (lastState == VideoDownloadManager.DownloadType.IsStopped) {
                    changeDownloadImage(VideoDownloadManager.getDownloadState(data.id))
                }
                textView?.visibility = View.VISIBLE
                progressBar?.visibility = View.VISIBLE
                val currentMbString = "%.1f".format(setCurrentBytes / 1000000f)
                val totalMbString = "%.1f".format(setTotalBytes / 1000000f)

                textView?.text =
                    "${currentMbString}MB / ${totalMbString}MB"

                progressBar?.let { bar ->
                    bar.max = (setTotalBytes / 1000).toInt()

                    if (animate) {
                        val animation: ObjectAnimator = ObjectAnimator.ofInt(
                            bar,
                            "progress",
                            bar.progress,
                            (setCurrentBytes / 1000).toInt()
                        )
                        animation.duration = 500
                        animation.setAutoCancel(true)
                        animation.interpolator = DecelerateInterpolator()
                        animation.start()
                    } else {
                        bar.progress = (setCurrentBytes / 1000).toInt()
                    }
                }
            }
        }

        fixDownloadedBytes(currentBytes, totalBytes, false)
        changeDownloadImage(VideoDownloadManager.getDownloadState(data.id))

        val downloadProgressEventListener = { downloadData: Triple<Int, Long, Long> ->
            if (data.id == downloadData.first) {
                if (downloadData.second != currentBytes || downloadData.third != totalBytes) { // TO PREVENT WASTING UI TIME
                    Coroutines.runOnMainThread {
                        fixDownloadedBytes(downloadData.second, downloadData.third, true)
                    }
                }
            }
        }

        val downloadStatusEventListener = { downloadData: Pair<Int, VideoDownloadManager.DownloadType> ->
            if (data.id == downloadData.first) {
                if (lastState != downloadData.second || needImageUpdate) { // TO PREVENT WASTING UI TIME
                    Coroutines.runOnMainThread {
                        changeDownloadImage(downloadData.second)
                    }
                }
            }
        }

        VideoDownloadManager.downloadProgressEvent += downloadProgressEventListener
        VideoDownloadManager.downloadStatusEvent += downloadStatusEventListener

        downloadView.setOnClickListener {
            if (currentBytes <= 0) {
                clickCallback.invoke(DownloadClickEvent(DOWNLOAD_ACTION_DOWNLOAD, data))
            } else {
                val list = arrayListOf(
                    Pair(DOWNLOAD_ACTION_PLAY_FILE, R.string.popup_play_file),
                    Pair(DOWNLOAD_ACTION_DELETE_FILE, R.string.popup_delete_file),
                )

                // DON'T RESUME A DOWNLOADED FILE lastState != VideoDownloadManager.DownloadType.IsDone &&
                if ((currentBytes * 100 / totalBytes) < 98) {
                    list.add(
                        if (lastState == VideoDownloadManager.DownloadType.IsDownloading)
                            Pair(DOWNLOAD_ACTION_PAUSE_DOWNLOAD, R.string.popup_pause_download)
                        else
                            Pair(DOWNLOAD_ACTION_RESUME_DOWNLOAD, R.string.popup_resume_download)
                    )
                }

                it.popupMenuNoIcons(
                    list
                ) {
                    clickCallback.invoke(DownloadClickEvent(itemId, data))
                }
            }
        }

        return {
            VideoDownloadManager.downloadProgressEvent -= downloadProgressEventListener
            VideoDownloadManager.downloadStatusEvent -= downloadStatusEventListener
        }
    }

    fun setUpMaterialButton(
        setupCurrentBytes: Long?,
        setupTotalBytes: Long?,
        progressBar: ContentLoadingProgressBar,
        downloadButton: MaterialButton,
        textView: TextView?,
        data: VideoDownloadHelper.DownloadEpisodeCached,
        clickCallback: (DownloadClickEvent) -> Unit,
    ): () -> Unit {
        return setUpDownloadButton(setupCurrentBytes, setupTotalBytes, progressBar, textView, data, downloadButton, {
            downloadButton?.setIconResource(it.first)
            downloadButton?.text = it.second
        }, clickCallback)
    }

    fun setUpButton(
        setupCurrentBytes: Long?,
        setupTotalBytes: Long?,
        progressBar: ContentLoadingProgressBar,
        downloadImage: ImageView,
        textView: TextView?,
        data: VideoDownloadHelper.DownloadEpisodeCached,
        clickCallback: (DownloadClickEvent) -> Unit,
    ): () -> Unit {
        return setUpDownloadButton(setupCurrentBytes, setupTotalBytes, progressBar, textView, data, downloadImage, {
            downloadImage?.setImageResource(it.first)
        }, clickCallback)
    }
}