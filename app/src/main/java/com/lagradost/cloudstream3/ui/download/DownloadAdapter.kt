package com.lagradost.cloudstream3.ui.download

import android.annotation.SuppressLint
import android.text.format.Formatter.formatShortFileSize
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.DownloadChildEpisodeBinding
import com.lagradost.cloudstream3.databinding.DownloadHeaderEpisodeBinding
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.AppUtils.getNameFull
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.DataStoreHelper.fixVisual
import com.lagradost.cloudstream3.utils.UIHelper.setImage
import com.lagradost.cloudstream3.utils.VideoDownloadHelper

const val DOWNLOAD_ACTION_PLAY_FILE = 0
const val DOWNLOAD_ACTION_DELETE_FILE = 1
const val DOWNLOAD_ACTION_RESUME_DOWNLOAD = 2
const val DOWNLOAD_ACTION_PAUSE_DOWNLOAD = 3
const val DOWNLOAD_ACTION_DOWNLOAD = 4
const val DOWNLOAD_ACTION_LONG_CLICK = 5

abstract class VisualDownloadCached(
    open val currentBytes: Long,
    open val totalBytes: Long,
    open val data: VideoDownloadHelper.DownloadCached
) {

    // Just to be extra-safe with areContentsTheSame
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VisualDownloadCached) return false

        if (currentBytes != other.currentBytes) return false
        if (totalBytes != other.totalBytes) return false
        if (data != other.data) return false

        return true
    }

    override fun hashCode(): Int {
        var result = currentBytes.hashCode()
        result = 31 * result + totalBytes.hashCode()
        result = 31 * result + data.hashCode()
        return result
    }
}

data class VisualDownloadChildCached(
    override val currentBytes: Long,
    override val totalBytes: Long,
    override val data: VideoDownloadHelper.DownloadEpisodeCached,
): VisualDownloadCached(currentBytes, totalBytes, data)

data class VisualDownloadHeaderCached(
    override val currentBytes: Long,
    override val totalBytes: Long,
    override val data: VideoDownloadHelper.DownloadHeaderCached,
    val child: VideoDownloadHelper.DownloadEpisodeCached?,
    val currentOngoingDownloads: Int,
    val totalDownloads: Int,
): VisualDownloadCached(currentBytes, totalBytes, data)

data class DownloadClickEvent(
    val action: Int,
    val data: VideoDownloadHelper.DownloadEpisodeCached
)

data class DownloadHeaderClickEvent(
    val action: Int,
    val data: VideoDownloadHelper.DownloadHeaderCached
)

class DownloadAdapter(
    private val clickCallback: (DownloadHeaderClickEvent) -> Unit,
    private val mediaClickCallback: (DownloadClickEvent) -> Unit,
) : ListAdapter<VisualDownloadCached, DownloadAdapter.DownloadViewHolder>(DiffCallback()) {

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_CHILD = 1
    }

    inner class DownloadViewHolder(
        private val binding: ViewBinding,
        private val clickCallback: (DownloadHeaderClickEvent) -> Unit,
        private val mediaClickCallback: (DownloadClickEvent) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        @SuppressLint("SetTextI18n")
        fun bind(card: VisualDownloadCached?) {
            when (binding) {
                is DownloadHeaderEpisodeBinding -> binding.apply {
                    if (card == null || card !is VisualDownloadHeaderCached) return@apply
                    val d = card.data

                    downloadHeaderPoster.apply {
                        setImage(d.poster)
                        setOnClickListener {
                            clickCallback.invoke(DownloadHeaderClickEvent(1, d))
                        }
                    }

                    downloadHeaderTitle.text = d.name
                    val mbString = formatShortFileSize(itemView.context, card.totalBytes)

                    if (card.child != null) {
                        downloadHeaderGotoChild.isVisible = false

                        downloadButton.setDefaultClickListener(card.child, downloadHeaderInfo, mediaClickCallback)
                        downloadButton.isVisible = true

                        episodeHolder.setOnClickListener {
                            mediaClickCallback.invoke(
                                DownloadClickEvent(
                                    DOWNLOAD_ACTION_PLAY_FILE,
                                    card.child
                                )
                            )
                        }
                    } else {
                        downloadButton.isVisible = false
                        downloadHeaderGotoChild.isVisible = true

                        try {
                            downloadHeaderInfo.text =
                                downloadHeaderInfo.context.getString(R.string.extra_info_format)
                                    .format(
                                        card.totalDownloads,
                                        if (card.totalDownloads == 1) downloadHeaderInfo.context.getString(
                                            R.string.episode
                                        ) else downloadHeaderInfo.context.getString(
                                            R.string.episodes
                                        ),
                                        mbString
                                    )
                        } catch (t: Throwable) {
                            // You probably formatted incorrectly
                            downloadHeaderInfo.text = "Error"
                            logError(t)
                        }

                        episodeHolder.setOnClickListener {
                            clickCallback.invoke(DownloadHeaderClickEvent(0, d))
                        }
                    }
                }

                is DownloadChildEpisodeBinding -> binding.apply {
                    if (card == null || card !is VisualDownloadChildCached) return@apply
                    val d = card.data

                    val posDur = DataStoreHelper.getViewPos(d.id)
                    downloadChildEpisodeProgress.apply {
                        if (posDur != null) {
                            val visualPos = posDur.fixVisual()
                            max = (visualPos.duration / 1000).toInt()
                            progress = (visualPos.position / 1000).toInt()
                            isVisible = true
                        } else isVisible = false
                    }

                    downloadButton.setDefaultClickListener(card.data, downloadChildEpisodeTextExtra, mediaClickCallback)

                    downloadChildEpisodeText.apply {
                        text = context.getNameFull(d.name, d.episode, d.season)
                        isSelected = true // Needed for text repeating
                    }

                    downloadChildEpisodeHolder.setOnClickListener {
                        mediaClickCallback.invoke(DownloadClickEvent(DOWNLOAD_ACTION_PLAY_FILE, d))
                    }
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DownloadViewHolder {
        val binding = when (viewType) {
            VIEW_TYPE_HEADER -> {
                DownloadHeaderEpisodeBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            }
            VIEW_TYPE_CHILD -> {
                DownloadChildEpisodeBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
        return DownloadViewHolder(binding, clickCallback, mediaClickCallback)
    }

    override fun onBindViewHolder(holder: DownloadViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun getItemViewType(position: Int): Int {
        val card = getItem(position)
        return if (card is VisualDownloadChildCached) VIEW_TYPE_CHILD else VIEW_TYPE_HEADER
    }

    class DiffCallback : DiffUtil.ItemCallback<VisualDownloadCached>() {
        override fun areItemsTheSame(oldItem: VisualDownloadCached, newItem: VisualDownloadCached): Boolean {
            return oldItem.data.id == newItem.data.id
        }

        override fun areContentsTheSame(oldItem: VisualDownloadCached, newItem: VisualDownloadCached): Boolean {
            return oldItem == newItem
        }
    }
}