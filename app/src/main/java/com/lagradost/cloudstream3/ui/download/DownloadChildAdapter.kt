package com.lagradost.cloudstream3.ui.download

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.databinding.DownloadChildEpisodeBinding
import com.lagradost.cloudstream3.utils.AppUtils.getNameFull
import com.lagradost.cloudstream3.utils.DataStoreHelper.fixVisual
import com.lagradost.cloudstream3.utils.DataStoreHelper.getViewPos
import com.lagradost.cloudstream3.utils.VideoDownloadHelper

const val DOWNLOAD_ACTION_PLAY_FILE = 0
const val DOWNLOAD_ACTION_DELETE_FILE = 1
const val DOWNLOAD_ACTION_RESUME_DOWNLOAD = 2
const val DOWNLOAD_ACTION_PAUSE_DOWNLOAD = 3
const val DOWNLOAD_ACTION_DOWNLOAD = 4
const val DOWNLOAD_ACTION_LONG_CLICK = 5

data class VisualDownloadChildCached(
    val currentBytes: Long,
    val totalBytes: Long,
    val data: VideoDownloadHelper.DownloadEpisodeCached,
)

data class DownloadClickEvent(val action: Int, val data: VideoDownloadHelper.DownloadEpisodeCached)

class DownloadChildAdapter(
    var cardList: List<VisualDownloadChildCached>,
    private val clickCallback: (DownloadClickEvent) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return DownloadChildViewHolder(
            DownloadChildEpisodeBinding.inflate(LayoutInflater.from(parent.context), parent, false),
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
        val binding: DownloadChildEpisodeBinding,
        private val clickCallback: (DownloadClickEvent) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        /*private val title: TextView = itemView.download_child_episode_text
        private val extraInfo: TextView = itemView.download_child_episode_text_extra
        private val holder: CardView = itemView.download_child_episode_holder
        private val progressBar: ContentLoadingProgressBar = itemView.download_child_episode_progress
        private val progressBarDownload: ContentLoadingProgressBar = itemView.download_child_episode_progress_downloaded
        private val downloadImage: ImageView = itemView.download_child_episode_download*/


        fun bind(card: VisualDownloadChildCached) {
            val d = card.data

            val posDur = getViewPos(d.id)
            binding.downloadChildEpisodeProgress.apply {
                if (posDur != null) {
                    val visualPos = posDur.fixVisual()
                    max = (visualPos.duration / 1000).toInt()
                    progress = (visualPos.position / 1000).toInt()
                    visibility = View.VISIBLE
                } else {
                    visibility = View.GONE
                }
            }

            binding.downloadButton.setDefaultClickListener(card.data, binding.downloadChildEpisodeTextExtra, clickCallback)

            binding.downloadChildEpisodeText.apply {
                text = context.getNameFull(d.name, d.episode, d.season)
                isSelected = true // is needed for text repeating
            }


            binding.downloadChildEpisodeHolder.setOnClickListener {
                clickCallback.invoke(DownloadClickEvent(DOWNLOAD_ACTION_PLAY_FILE, d))
            }
        }
    }
}
