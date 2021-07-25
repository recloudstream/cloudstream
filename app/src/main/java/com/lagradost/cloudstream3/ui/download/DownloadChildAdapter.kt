package com.lagradost.cloudstream3.ui.download

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.widget.ContentLoadingProgressBar
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.UIHelper.popupMenuNoIcons
import com.lagradost.cloudstream3.utils.Coroutines.runOnMainThread
import com.lagradost.cloudstream3.utils.DataStoreHelper.fixVisual
import com.lagradost.cloudstream3.utils.DataStoreHelper.getViewPos
import com.lagradost.cloudstream3.utils.VideoDownloadHelper
import com.lagradost.cloudstream3.utils.VideoDownloadManager
import com.lagradost.cloudstream3.utils.VideoDownloadManager.getDownloadState
import kotlinx.android.synthetic.main.download_child_episode.view.*

const val DOWNLOAD_ACTION_PLAY_FILE = 0
const val DOWNLOAD_ACTION_DELETE_FILE = 1
const val DOWNLOAD_ACTION_RESUME_DOWNLOAD = 2
const val DOWNLOAD_ACTION_PAUSE_DOWNLOAD = 3
const val DOWNLOAD_ACTION_DOWNLOAD = 4

data class VisualDownloadChildCached(
    val currentBytes: Long,
    val totalBytes: Long,
    val data: VideoDownloadHelper.DownloadEpisodeCached,
)

data class DownloadClickEvent(val action: Int, val data: VideoDownloadHelper.DownloadEpisodeCached)

class DownloadChildAdapter(
    var cardList: List<VisualDownloadChildCached>,
    private val clickCallback: (DownloadClickEvent) -> Unit,
) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return DownloadChildViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.download_child_episode, parent, false),
            clickCallback
        )
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is DownloadChildViewHolder -> {
                holder.bind(cardList[position])
            }
        }
    }

    override fun getItemCount(): Int {
        return cardList.size
    }

    class DownloadChildViewHolder
    constructor(
        itemView: View,
        private val clickCallback: (DownloadClickEvent) -> Unit,
    ) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.download_child_episode_text
        private val extraInfo: TextView = itemView.download_child_episode_text_extra
        private val holder: CardView = itemView.download_child_episode_holder
        private val progressBar: ContentLoadingProgressBar = itemView.download_child_episode_progress
        private val progressBarDownload: ContentLoadingProgressBar = itemView.download_child_episode_progress_downloaded
        private val downloadImage: ImageView = itemView.download_child_episode_download

        @SuppressLint("SetTextI18n")
        fun bind(card: VisualDownloadChildCached) {
            val d = card.data

            val posDur = itemView.context.getViewPos(d.id)
            if (posDur != null) {
                val visualPos = posDur.fixVisual()
                progressBar.max = (visualPos.duration / 1000).toInt()
                progressBar.progress = (visualPos.position / 1000).toInt()
                progressBar.visibility = View.VISIBLE
            } else {
                progressBar.visibility = View.GONE
            }

            title.text = d.name ?: "Episode ${d.episode}" //TODO FIX
            DownloadButtonSetup.setUpButton(
                card.currentBytes,
                card.totalBytes,
                progressBarDownload,
                downloadImage,
                extraInfo,
                card.data,
                clickCallback
            )

            holder.setOnClickListener {
                clickCallback.invoke(DownloadClickEvent(DOWNLOAD_ACTION_PLAY_FILE, d))
            }
        }
    }
}
