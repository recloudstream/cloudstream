package com.lagradost.cloudstream3.ui.download.queue


import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getKey
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.DownloadQueueItemBinding
import com.lagradost.cloudstream3.ui.BaseAdapter
import com.lagradost.cloudstream3.ui.BaseDiffCallback
import com.lagradost.cloudstream3.ui.ViewHolderState
import com.lagradost.cloudstream3.ui.download.DOWNLOAD_ACTION_CANCEL_PENDING
import com.lagradost.cloudstream3.ui.download.DOWNLOAD_ACTION_DELETE_FILE
import com.lagradost.cloudstream3.ui.download.DOWNLOAD_ACTION_PAUSE_DOWNLOAD
import com.lagradost.cloudstream3.ui.download.DOWNLOAD_ACTION_RESUME_DOWNLOAD
import com.lagradost.cloudstream3.ui.download.DownloadButtonSetup.handleDownloadClick
import com.lagradost.cloudstream3.ui.download.DownloadClickEvent
import com.lagradost.cloudstream3.ui.download.queue.DownloadQueueAdapter.Companion.DOWNLOAD_SEPARATOR_TAG
import com.lagradost.cloudstream3.utils.AppContextUtils.getNameFull
import com.lagradost.cloudstream3.utils.DOWNLOAD_EPISODE_CACHE
import com.lagradost.cloudstream3.utils.DataStore.getFolderName
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.UIHelper.popupMenuNoIcons
import com.lagradost.cloudstream3.utils.downloader.DownloadObjects
import com.lagradost.cloudstream3.utils.downloader.DownloadObjects.DownloadQueueWrapper
import com.lagradost.cloudstream3.utils.downloader.DownloadQueueManager
import com.lagradost.cloudstream3.utils.downloader.VideoDownloadManager
import com.lagradost.cloudstream3.utils.downloader.VideoDownloadManager.KEY_DOWNLOAD_INFO

/** An item in the adapter can either be a separator or a real item.
 * isCurrentlyDownloading is used to fully update items as opposed to just moving them. */
class DownloadAdapterItem(val item: DownloadQueueWrapper?) {
    val isSeparator = item == null
}


class DownloadQueueAdapter(val fragment: Fragment) : BaseAdapter<DownloadAdapterItem, Unit>(
    diffCallback = BaseDiffCallback(
        itemSame = { a, b -> a.item?.id == b.item?.id },
        contentSame = { a, b ->
            a.item == b.item
        })
) {
    var currentDownloads = 0

    companion object {
        val DOWNLOAD_SEPARATOR_TAG = "DOWNLOAD_SEPARATOR_TAG"
    }

    override fun onCreateContent(parent: ViewGroup): ViewHolderState<Unit> {
        val inflater = LayoutInflater.from(parent.context)
        val binding = DownloadQueueItemBinding.inflate(inflater, parent, false)
        return ViewHolderState(binding)
    }

    override fun onBindContent(
        holder: ViewHolderState<Unit>,
        item: DownloadAdapterItem,
        position: Int
    ) {
        when (val binding = holder.view) {
            is DownloadQueueItemBinding -> {
                if (item.item == null) {
                    holder.itemView.tag = DOWNLOAD_SEPARATOR_TAG
                    bindSeparator(binding)
                } else {
                    holder.itemView.tag = null
                    bind(binding, item.item)
                }
            }
        }
    }

    fun submitQueue(newQueue: DownloadAdapterQueue) {
        val index = newQueue.currentDownloads.size
        val current = newQueue.currentDownloads
        val queue = newQueue.queue
        currentDownloads = current.size

        val newList =
            (current + queue).distinctBy { it.id }.map { DownloadAdapterItem(it) }.toMutableList()
                .apply {
                    // Only add the separator if it actually separates something
                    if (index < this.size) {
                        add(index, DownloadAdapterItem(null))
                    }
                }
        submitList(newList)
    }

    fun bindSeparator(binding: DownloadQueueItemBinding) {
        binding.apply {
            separatorHolder.isGone = false
            downloadChildEpisodeHolder.isGone = true
        }
    }

    fun bind(
        binding: DownloadQueueItemBinding,
        queueWrapper: DownloadQueueWrapper,
    ) {
        val context = binding.root.context

        binding.apply {
            separatorHolder.isGone = true
            downloadChildEpisodeHolder.isGone = false

            // Only set the child-text if child and parent are not the same
            // This prevents setting movie titles twice
            if (queueWrapper.id != queueWrapper.parentId) {
                val mainName = queueWrapper.downloadItem?.resultName ?: queueWrapper.resumePackage?.item?.ep?.mainName
                downloadChildEpisodeTextExtra.text = mainName
            } else {
                downloadChildEpisodeTextExtra.text = null
            }

            downloadChildEpisodeTextExtra.isGone = downloadChildEpisodeTextExtra.text.isNullOrBlank()

            val status = VideoDownloadManager.downloadStatus[queueWrapper.id]

            downloadButton.setOnClickListener { view ->
                val episodeCached =
                    getKey<DownloadObjects.DownloadEpisodeCached>(
                        DOWNLOAD_EPISODE_CACHE,
                        getFolderName(queueWrapper.parentId.toString(), queueWrapper.id.toString())
                    )

                val downloadInfo = context.getKey<DownloadObjects.DownloadedFileInfo>(
                    KEY_DOWNLOAD_INFO,
                    queueWrapper.id.toString()
                )

                val isCurrentlyDownloading = queueWrapper.isCurrentlyDownloading()

                val actionList = arrayListOf<Pair<Int,Int>>()

                if (isCurrentlyDownloading && episodeCached != null) {
                    // KEY_DOWNLOAD_INFO is used in the file deletion, and is required to exist to delete anything
                    if (downloadInfo != null) {
                        actionList.add(Pair(DOWNLOAD_ACTION_DELETE_FILE, R.string.popup_delete_file))
                    } else {
                        actionList.add(Pair(DOWNLOAD_ACTION_CANCEL_PENDING, R.string.cancel))
                    }

                    val currentStatus = VideoDownloadManager.downloadStatus[queueWrapper.id]

                    when (currentStatus) {
                        VideoDownloadManager.DownloadType.IsDownloading -> {
                            actionList.add(
                                Pair(
                                    DOWNLOAD_ACTION_PAUSE_DOWNLOAD,
                                    R.string.popup_pause_download
                                )
                            )
                        }

                        VideoDownloadManager.DownloadType.IsPaused -> {
                            actionList.add(
                                Pair(
                                    DOWNLOAD_ACTION_RESUME_DOWNLOAD,
                                    R.string.popup_resume_download
                                )
                            )
                        }

                        else -> {}
                    }

                    view.popupMenuNoIcons(
                        actionList
                    ) {
                        handleDownloadClick(DownloadClickEvent(itemId, episodeCached))
                    }
                } else {
                    actionList.add(Pair(DOWNLOAD_ACTION_CANCEL_PENDING, R.string.cancel))

                    view.popupMenuNoIcons(
                        actionList
                    ) {
                        when (itemId) {
                            DOWNLOAD_ACTION_CANCEL_PENDING -> {
                                DownloadQueueManager.cancelDownload(queueWrapper.id)
                            }
                        }
                    }
                }
            }

            downloadButton.resetView()
            downloadButton.setStatus(status)
            downloadButton.setPersistentId(queueWrapper.id)

            downloadChildEpisodeText.apply {
                val name = queueWrapper.downloadItem?.episode?.name
                    ?: queueWrapper.resumePackage?.item?.ep?.name
                val episode =
                    queueWrapper.downloadItem?.episode?.episode
                        ?: queueWrapper.resumePackage?.item?.ep?.episode
                val season =
                    queueWrapper.downloadItem?.episode?.season
                        ?: queueWrapper.resumePackage?.item?.ep?.season
                text = context.getNameFull(name, episode, season)
                isSelected = true // Needed for text repeating
            }
        }
    }
}


class DragAndDropTouchHelper(adapter: DownloadQueueAdapter) :
    ItemTouchHelper(
        DragAndDropTouchHelperCallback(adapter)
    )

private class DragAndDropTouchHelperCallback(private val adapter: DownloadQueueAdapter) :
    ItemTouchHelper.Callback() {
    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        val item = adapter.getItem(viewHolder.absoluteAdapterPosition)
        val isDownloading = item.item?.isCurrentlyDownloading() == true
        val dragFlags = if (item.isSeparator || isDownloading) {
            0
        } else {
            ItemTouchHelper.UP or ItemTouchHelper.DOWN // Allow drag up/down
        }

        val swipeFlags = 0 // Disable swipe functionality
        return makeMovementFlags(dragFlags, swipeFlags)
    }

    override fun onMove(
        recyclerView: RecyclerView,
        source: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        val fromPosition = source.absoluteAdapterPosition
        val toPosition = target.absoluteAdapterPosition
        val separatorPosition = adapter.currentDownloads

        val toPositionNoSeparator =
            if (separatorPosition < toPosition) toPosition - separatorPosition else toPosition

        if (source.itemView.tag == DOWNLOAD_SEPARATOR_TAG) {
            return false
        } else {
            adapter.getItem(fromPosition).item?.let { downloadQueueInfo ->
                DownloadQueueManager.reorderItem(
                    downloadQueueInfo,
                    toPositionNoSeparator - 1
                )
            }
        }

        return true
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {

    }

    override fun isLongPressDragEnabled(): Boolean {
        return true // Enable drag with long press
    }

    override fun isItemViewSwipeEnabled(): Boolean {
        return false // Disable swipe by default
    }
}