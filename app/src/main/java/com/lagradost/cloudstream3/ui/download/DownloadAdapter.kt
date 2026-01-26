package com.lagradost.cloudstream3.ui.download

import android.text.format.Formatter.formatShortFileSize
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.CheckBox
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
import com.lagradost.cloudstream3.utils.ImageLoader.loadImage
import com.lagradost.cloudstream3.utils.VideoDownloadHelper

const val DOWNLOAD_ACTION_PLAY_FILE = 0
const val DOWNLOAD_ACTION_DELETE_FILE = 1
const val DOWNLOAD_ACTION_RESUME_DOWNLOAD = 2
const val DOWNLOAD_ACTION_PAUSE_DOWNLOAD = 3
const val DOWNLOAD_ACTION_DOWNLOAD = 4
const val DOWNLOAD_ACTION_LONG_CLICK = 5

const val DOWNLOAD_ACTION_GO_TO_CHILD = 0
const val DOWNLOAD_ACTION_LOAD_RESULT = 1

sealed class VisualDownloadCached {
    abstract val currentBytes: Long
    abstract val totalBytes: Long
    abstract val data: VideoDownloadHelper.DownloadCached
    abstract var isSelected: Boolean

    data class Child(
        override val currentBytes: Long,
        override val totalBytes: Long,
        override val data: VideoDownloadHelper.DownloadEpisodeCached,
        override var isSelected: Boolean,
    ) : VisualDownloadCached()

    data class Header(
        override val currentBytes: Long,
        override val totalBytes: Long,
        override val data: VideoDownloadHelper.DownloadHeaderCached,
        override var isSelected: Boolean,
        val child: VideoDownloadHelper.DownloadEpisodeCached?,
        val currentOngoingDownloads: Int,
        val totalDownloads: Int,
    ) : VisualDownloadCached()
}

data class DownloadClickEvent(
    val action: Int,
    val data: VideoDownloadHelper.DownloadEpisodeCached
)

data class DownloadHeaderClickEvent(
    val action: Int,
    val data: VideoDownloadHelper.DownloadHeaderCached
)

class DownloadAdapter(
    private val onHeaderClickEvent: (DownloadHeaderClickEvent) -> Unit,
    private val onItemClickEvent: (DownloadClickEvent) -> Unit,
    private val onItemSelectionChanged: (Int, Boolean) -> Unit,
) : ListAdapter<VisualDownloadCached, DownloadAdapter.DownloadViewHolder>(DiffCallback()) {

    private var isMultiDeleteState: Boolean = false

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_CHILD = 1
    }

    inner class DownloadViewHolder(
        private val binding: ViewBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(card: VisualDownloadCached?) {
            when (binding) {
                is DownloadHeaderEpisodeBinding -> bindHeader(card as? VisualDownloadCached.Header)
                is DownloadChildEpisodeBinding -> bindChild(card as? VisualDownloadCached.Child)
            }
        }

        private fun bindHeader(card: VisualDownloadCached.Header?) {
            if (binding !is DownloadHeaderEpisodeBinding || card == null) return

            val data = card.data
            binding.apply {
                episodeHolder.apply {
                    if (isMultiDeleteState) {
                        setOnClickListener {
                            toggleIsChecked(deleteCheckbox, data.id)
                        }
                    }

                    setOnLongClickListener {
                        toggleIsChecked(deleteCheckbox, data.id)
                        true
                    }
                }

                downloadHeaderPoster.apply {
                    loadImage(data.poster)
                    if (isMultiDeleteState) {
                        setOnClickListener {
                            toggleIsChecked(deleteCheckbox, data.id)
                        }
                    } else {
                        setOnClickListener {
                            onHeaderClickEvent.invoke(
                                DownloadHeaderClickEvent(
                                    DOWNLOAD_ACTION_LOAD_RESULT,
                                    data
                                )
                            )
                        }
                    }

                    setOnLongClickListener {
                        toggleIsChecked(deleteCheckbox, data.id)
                        true
                    }
                }
                downloadHeaderTitle.text = data.name
                val formattedSize = formatShortFileSize(itemView.context, card.totalBytes)

                if (card.child != null) {
                    handleChildDownload(card, formattedSize)
                } else handleParentDownload(card, formattedSize)

                if (isMultiDeleteState) {
                    deleteCheckbox.setOnCheckedChangeListener { _, isChecked ->
                        onItemSelectionChanged.invoke(data.id, isChecked)
                    }
                } else deleteCheckbox.setOnCheckedChangeListener(null)

                deleteCheckbox.apply {
                    isVisible = isMultiDeleteState
                    isChecked = card.isSelected
                }
            }
        }

        private fun DownloadHeaderEpisodeBinding.handleChildDownload(
            card: VisualDownloadCached.Header,
            formattedSize: String
        ) {
            card.child ?: return
            downloadHeaderGotoChild.isVisible = false

            val posDur = getViewPos(card.data.id)
            downloadHeaderEpisodeProgress.apply {
                isVisible = posDur != null
                posDur?.let {
                    val visualPos = it.fixVisual()
                    max = (visualPos.duration / 1000).toInt()
                    progress = (visualPos.position / 1000).toInt()
                }
            }

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
                downloadHeaderInfo.text = formattedSize
            } else {
                // We need to make sure we restore the correct progress
                // when we refresh data in the adapter.
                downloadButton.resetView()
                val drawable = downloadButton.getDrawableFromStatus(status)?.let {
                    ContextCompat.getDrawable(downloadButton.context, it)
                }
                downloadButton.statusView.setImageDrawable(drawable)
                downloadButton.progressBar.progressDrawable =
                    ContextCompat.getDrawable(
                        downloadButton.context,
                        downloadButton.progressDrawable
                    )
            }

            downloadButton.setDefaultClickListener(card.child, downloadHeaderInfo, onItemClickEvent)
            downloadButton.isVisible = !isMultiDeleteState

            if (!isMultiDeleteState) {
                episodeHolder.setOnClickListener {
                    onItemClickEvent.invoke(
                        DownloadClickEvent(
                            DOWNLOAD_ACTION_PLAY_FILE,
                            card.child
                        )
                    )
                }
            }
        }

        private fun DownloadHeaderEpisodeBinding.handleParentDownload(
            card: VisualDownloadCached.Header,
            formattedSize: String
        ) {
            downloadButton.isVisible = false
            downloadHeaderEpisodeProgress.isVisible = false
            downloadHeaderGotoChild.isVisible = !isMultiDeleteState

            try {
                downloadHeaderInfo.text =
                    downloadHeaderInfo.context.getString(R.string.extra_info_format).format(
                        card.totalDownloads,
                        downloadHeaderInfo.context.resources.getQuantityString(
                            R.plurals.episodes,
                            card.totalDownloads
                        ),
                        formattedSize
                    )
            } catch (e: Exception) {
                downloadHeaderInfo.text = null
                logError(e)
            }

            if (!isMultiDeleteState) {
                episodeHolder.setOnClickListener {
                    onHeaderClickEvent.invoke(
                        DownloadHeaderClickEvent(
                            DOWNLOAD_ACTION_GO_TO_CHILD,
                            card.data
                        )
                    )
                }
            }
        }

        private fun bindChild(card: VisualDownloadCached.Child?) {
            if (binding !is DownloadChildEpisodeBinding || card == null) return

            val data = card.data
            binding.apply {
                val posDur = getViewPos(data.id)
                downloadChildEpisodeProgress.apply {
                    isVisible = posDur != null
                    posDur?.let {
                        val visualPos = it.fixVisual()
                        max = (visualPos.duration / 1000).toInt()
                        progress = (visualPos.position / 1000).toInt()
                    }
                }

                val status = downloadButton.getStatus(data.id, card.currentBytes, card.totalBytes)
                if (status == DownloadStatusTell.IsDone) {
                    // We do this here instead if we are finished downloading
                    // so that we can use the value from the view model
                    // rather than extra unneeded disk operations and to prevent a
                    // delay in updating download icon state.
                    downloadButton.setProgress(card.currentBytes, card.totalBytes)
                    downloadButton.applyMetaData(data.id, card.currentBytes, card.totalBytes)
                    // We will let the view model handle this
                    downloadButton.doSetProgress = false
                    downloadButton.progressBar.progressDrawable =
                        downloadButton.getDrawableFromStatus(status)
                            ?.let { ContextCompat.getDrawable(downloadButton.context, it) }
                    downloadChildEpisodeTextExtra.text =
                        formatShortFileSize(downloadChildEpisodeTextExtra.context, card.totalBytes)
                } else {
                    // We need to make sure we restore the correct progress
                    // when we refresh data in the adapter.
                    downloadButton.resetView()
                    val drawable = downloadButton.getDrawableFromStatus(status)?.let {
                        ContextCompat.getDrawable(downloadButton.context, it)
                    }
                    downloadButton.statusView.setImageDrawable(drawable)
                    downloadButton.progressBar.progressDrawable =
                        ContextCompat.getDrawable(
                            downloadButton.context,
                            downloadButton.progressDrawable
                        )
                }

                downloadButton.setDefaultClickListener(
                    data,
                    downloadChildEpisodeTextExtra,
                    onItemClickEvent
                )
                downloadButton.isVisible = !isMultiDeleteState

                downloadChildEpisodeText.apply {
                    text = context.getNameFull(data.name, data.episode, data.season)
                    isSelected = true // Needed for text repeating
                }

                downloadChildEpisodeHolder.setOnClickListener {
                    onItemClickEvent.invoke(DownloadClickEvent(DOWNLOAD_ACTION_PLAY_FILE, data))
                }

                downloadChildEpisodeHolder.apply {
                    when {
                        isMultiDeleteState -> {
                            setOnClickListener {
                                toggleIsChecked(deleteCheckbox, data.id)
                            }
                        }

                        else -> {
                            setOnClickListener {
                                onItemClickEvent.invoke(
                                    DownloadClickEvent(
                                        DOWNLOAD_ACTION_PLAY_FILE,
                                        data
                                    )
                                )
                            }
                        }
                    }

                    setOnLongClickListener {
                        toggleIsChecked(deleteCheckbox, data.id)
                        true
                    }
                }

                if (isMultiDeleteState) {
                    deleteCheckbox.setOnCheckedChangeListener { _, isChecked ->
                        onItemSelectionChanged.invoke(data.id, isChecked)
                    }
                } else deleteCheckbox.setOnCheckedChangeListener(null)

                deleteCheckbox.apply {
                    isVisible = isMultiDeleteState
                    isChecked = card.isSelected
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
        return DownloadViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DownloadViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is VisualDownloadCached.Child -> VIEW_TYPE_CHILD
            is VisualDownloadCached.Header -> VIEW_TYPE_HEADER
            else -> throw IllegalArgumentException("Invalid data type at position $position")
        }
    }

    fun setIsMultiDeleteState(value: Boolean) {
        if (isMultiDeleteState == value) return
        isMultiDeleteState = value
        notifyItemRangeChanged(0, itemCount)
    }

    fun notifyAllSelected() {
        currentList.indices.forEach { index ->
            if (!currentList[index].isSelected) {
                notifyItemChanged(index)
            }
        }
    }

    fun notifySelectionStates() {
        currentList.indices.forEach { index ->
            if (currentList[index].isSelected) {
                notifyItemChanged(index)
            }
        }
    }

    private fun toggleIsChecked(checkbox: CheckBox, itemId: Int) {
        val isChecked = !checkbox.isChecked
        checkbox.isChecked = isChecked
        onItemSelectionChanged.invoke(itemId, isChecked)
    }

    class DiffCallback : DiffUtil.ItemCallback<VisualDownloadCached>() {
        override fun areItemsTheSame(
            oldItem: VisualDownloadCached,
            newItem: VisualDownloadCached
        ): Boolean {
            return oldItem.data.id == newItem.data.id
        }

        override fun areContentsTheSame(
            oldItem: VisualDownloadCached,
            newItem: VisualDownloadCached
        ): Boolean {
            return oldItem == newItem
        }
    }
}