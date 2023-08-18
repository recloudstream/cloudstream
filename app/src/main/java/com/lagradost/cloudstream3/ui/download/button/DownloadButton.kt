package com.lagradost.cloudstream3.ui.download.button

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import androidx.core.view.isVisible
import com.google.android.material.button.MaterialButton
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.download.DownloadClickEvent
import com.lagradost.cloudstream3.utils.VideoDownloadHelper

class DownloadButton(context: Context, attributeSet: AttributeSet) :
    PieFetchButton(context, attributeSet) {

    var mainText: TextView? = null
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        progressText = findViewById(R.id.result_movie_download_text_precentage)
        mainText = findViewById(R.id.result_movie_download_text)
    }

    override fun setStatus(status: DownloadStatusTell?) {
        mainText?.post {
            val txt = when (status) {
                DownloadStatusTell.IsPaused -> R.string.download_paused
                DownloadStatusTell.IsDownloading -> R.string.downloading
                DownloadStatusTell.IsDone -> R.string.downloaded
                else -> R.string.download
            }
            mainText?.setText(txt)
        }
        super.setStatus(status)

    }

    override fun setDefaultClickListener(
        card: VideoDownloadHelper.DownloadEpisodeCached,
        textView: TextView?,
        callback: (DownloadClickEvent) -> Unit
    ) {
        this.setDefaultClickListener(
            this.findViewById<MaterialButton>(R.id.download_movie_button),
            textView,
            card,
            callback
        )
    }

    @SuppressLint("SetTextI18n")
    override fun updateViewOnDownload(metadata: DownloadMetadata) {
        super.updateViewOnDownload(metadata)

        val isVis = metadata.progressPercentage > 0
        progressText?.isVisible = isVis
        if (isVis)
            progressText?.text = "${metadata.progressPercentage}%"
    }
}