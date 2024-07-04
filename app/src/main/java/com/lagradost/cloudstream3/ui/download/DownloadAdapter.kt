package com.lagradost.cloudstream3.ui.download

import android.annotation.SuppressLint
import android.text.format.Formatter.formatShortFileSize
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.DownloadChildEpisodeBinding
import com.lagradost.cloudstream3.databinding.DownloadHeaderEpisodeBinding
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.ui.download.button.DownloadStatusTell
import com.lagradost.cloudstream3.utils.AppContextUtils.getNameFull
import com.lagradost.cloudstream3.utils.DataStoreHelper.fixVisual
import com.lagradost.cloudstream3.utils.DataStoreHelper.getViewPos
import com.lagradost.cloudstream3.utils.UIHelper.setImage
import com.lagradost.cloudstream3.utils.VideoDownloadHelper

const val DOWNLOAD_ACTION_PLAY_FILE = 0
const val DOWNLOAD_ACTION_DELETE_FILE = 1
const val DOWNLOAD_ACTION_RESUME_DOWNLOAD = 2
const val DOWNLOAD_ACTION_PAUSE_DOWNLOAD = 3
const val DOWNLOAD_ACTION_DOWNLOAD = 4
const val DOWNLOAD_ACTION_LONG_CLICK = 5

const val DOWNLOAD_ACTION_GO_TO_CHILD = 0
const val DOWNLOAD_ACTION_LOAD_RESULT = 1

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

        fun bind(card: VisualDownloadCached?) {
            when (binding) {
                is DownloadHeaderEpisodeBinding -> bindHeader(card as? VisualDownloadHeaderCached)
                is DownloadChildEpisodeBinding -> bindChild(card as? VisualDownloadChildCached)
            }
        }

        @SuppressLint("SetTextI18n")
        private fun bindHeader(card: VisualDownloadHeaderCached?) {
            if (binding !is DownloadHeaderEpisodeBinding) return
            card ?: return
            val d = card.data

            binding.apply {
                downloadHeaderPoster.apply {
                    setImage(d.poster)
                    setOnClickListener {
                        clickCallback.invoke(DownloadHeaderClickEvent(DOWNLOAD_ACTION_LOAD_RESULT, d))
                    }
                }

                downloadHeaderTitle.text = d.name
                val formattedSizeString = formatShortFileSize(itemView.context, card.totalBytes)

                if (card.child != null) {
                    downloadHeaderGotoChild.isVisible = false

                    val status = downloadButton.getStatus(card.child.id, card.currentBytes, card.totalBytes)
                    if (status == DownloadStatusTell.IsDone) {
                        // We do this here instead if we are finished downloading
                        // so that we can use the value from the view model
                        // rather than extra unneeded disk operations and to prevent a
                        // delay in updating download icon state.
                        downloadButton.setProgress(card.currentBytes, card.totalBytes)
                        downloadButton.applyMetaData(card.child.id, card.currentBytes, card.totalBytes)
                        // We will let the view model handle this
                        downloadButton.doSetProgress = false
                        downloadButton.progressBar.progressDrawable =
                            downloadButton.getDrawableFromStatus(status)
                                ?.let { ContextCompat.getDrawable(downloadButton.context, it) }
                        downloadHeaderInfo.text = formattedSizeString
                    } else {
                        downloadButton.doSetProgress = true
                        downloadButton.progressBar.progressDrawable =
                            ContextCompat.getDrawable(downloadButton.context, downloadButton.progressDrawable)
                    }

                    downloadButton.setDefaultClickListener(card.child, downloadHeaderInfo, mediaClickCallback)
                    downloadButton.isVisible = true

                    episodeHolder.setOnClickListener {
                        mediaClickCallback.invoke(DownloadClickEvent(DOWNLOAD_ACTION_PLAY_FILE, card.child))
                    }
                } else {
                    downloadButton.isVisible = false
                    downloadHeaderGotoChild.isVisible = true

                    try {
                        downloadHeaderInfo.text = downloadHeaderInfo.context.getString(R.string.extra_info_format)
                            .format(
                                card.totalDownloads,
                                downloadHeaderInfo.context.resources.getQuantityString(
                                    R.plurals.episodes,
                                    card.totalDownloads
                                ),
                                formattedSizeString
                            )
                    } catch (e: Exception) {
                        // You probably formatted incorrectly
                        downloadHeaderInfo.text = "Error"
                        logError(e)
                    }

                    episodeHolder.setOnClickListener {
                        clickCallback.invoke(DownloadHeaderClickEvent(DOWNLOAD_ACTION_GO_TO_CHILD, d))
                    }
                }
            }
        }

        private fun bindChild(card: VisualDownloadChildCached?) {
            if (binding !is DownloadChildEpisodeBinding) return
            card ?: return
            val d = card.data

            binding.apply {
                val posDur = getViewPos(d.id)
                downloadChildEpisodeProgress.apply {
                    isVisible = posDur != null
                    posDur?.let {
                        val visualPos = it.fixVisual()
                        max = (visualPos.duration / 1000).toInt()
                        progress = (visualPos.position / 1000).toInt()
                    }
                }

                val status = downloadButton.getStatus(d.id, card.currentBytes, card.totalBytes)
                if (status == DownloadStatusTell.IsDone) {
                    // We do this here instead if we are finished downloading
                    // so that we can use the value from the view model
                    // rather than extra unneeded disk operations and to prevent a
                    // delay in updating download icon state.
                    downloadButton.setProgress(card.currentBytes, card.totalBytes)
                    downloadButton.applyMetaData(d.id, card.currentBytes, card.totalBytes)
                    // We will let the view model handle this
                    downloadButton.doSetProgress = false
                    downloadButton.progressBar.progressDrawable =
                        downloadButton.getDrawableFromStatus(status)
                            ?.let { ContextCompat.getDrawable(downloadButton.context, it) }
                    downloadChildEpisodeTextExtra.text = formatShortFileSize(downloadChildEpisodeTextExtra.context, card.totalBytes)
                } else {
                    downloadButton.doSetProgress = true
                    downloadButton.progressBar.progressDrawable =
                        ContextCompat.getDrawable(downloadButton.context, downloadButton.progressDrawable)
                }

                downloadButton.setDefaultClickListener(d, downloadChildEpisodeTextExtra, mediaClickCallback)
                downloadButton.isVisible = true

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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DownloadViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = when (viewType) {
            VIEW_TYPE_HEADER -> DownloadHeaderEpisodeBinding.inflate(inflater, parent, false)
            VIEW_TYPE_CHILD -> DownloadChildEpisodeBinding.inflate(inflater, parent, false)
            else -> throw IllegalArgumentException("Invalid view type")
        }
        return DownloadViewHolder(binding, clickCallback, mediaClickCallback)
    }

    override fun onBindViewHolder(holder: DownloadViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is VisualDownloadChildCached -> VIEW_TYPE_CHILD
            is VisualDownloadHeaderCached -> VIEW_TYPE_HEADER
            else -> throw IllegalArgumentException("Invalid data type at position $position")
        }
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