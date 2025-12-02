package com.lagradost.cloudstream3.ui.download.queue


import android.text.format.Formatter.formatShortFileSize
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.databinding.DownloadQueueItemBinding
import com.lagradost.cloudstream3.ui.BaseAdapter
import com.lagradost.cloudstream3.ui.BaseDiffCallback
import com.lagradost.cloudstream3.ui.ViewHolderState
import com.lagradost.cloudstream3.ui.download.DownloadButtonSetup.handleDownloadClick
import com.lagradost.cloudstream3.ui.download.VisualDownloadCached
import com.lagradost.cloudstream3.ui.download.button.DownloadStatusTell
import com.lagradost.cloudstream3.ui.download.queue.DownloadQueueAdapter.Companion.DOWNLOAD_SEPARATOR_TAG
import com.lagradost.cloudstream3.utils.AppContextUtils.getNameFull
import com.lagradost.cloudstream3.utils.DOWNLOAD_EPISODE_CACHE
import com.lagradost.cloudstream3.utils.DataStore.getFolderName
import com.lagradost.cloudstream3.utils.DataStoreHelper.fixVisual
import com.lagradost.cloudstream3.utils.DataStoreHelper.getViewPos
import com.lagradost.cloudstream3.utils.downloader.DownloadObjects
import com.lagradost.cloudstream3.utils.downloader.DownloadObjects.DownloadQueueWrapper
import com.lagradost.cloudstream3.utils.downloader.VideoDownloadManager
import com.lagradost.cloudstream3.utils.downloader.VideoDownloadManager.getDownloadFileInfo

/** An item in the adapter can either be a separator or a real item */
class DownloadAdapterItem(val item: DownloadQueueAdapterInfo?) {
    val isSeparator = item == null
}


class DownloadQueueAdapter(val fragment: Fragment) : BaseAdapter<DownloadAdapterItem, Unit>(
    diffCallback = BaseDiffCallback(
        itemSame = { a, b -> a.item?.queueWrapper?.id == b.item?.queueWrapper?.id },
        contentSame = { a, b ->
            a.item?.queueWrapper?.id == b.item?.queueWrapper?.id
        })
) {
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
        applyBinding(holder, item)
    }

    fun applyBinding(holder: ViewHolderState<Unit>, item: DownloadAdapterItem) {
        when (val binding = holder.view) {
            is DownloadQueueItemBinding -> {
                if (item.item == null) {
                    holder.itemView.tag = DOWNLOAD_SEPARATOR_TAG
                    bindSeparator(binding)
                } else {
                    holder.itemView.tag = null
                    bind(binding, item.item.queueWrapper, item.item.childCard)
                }
            }
        }
    }

    @JvmName("submitListShortcut")
    fun submitList(list: List<DownloadQueueAdapterInfo>) {
        submitList(list.map { DownloadAdapterItem(it) })
    }

    override fun submitList(list: Collection<DownloadAdapterItem>?, commitCallback: Runnable?) {
        val maxDownloads = fragment.context?.let { VideoDownloadManager.maxConcurrentDownloads(it) }
        val newList = list?.filterNot { it.isSeparator }?.toMutableList()?.apply {
            if (maxDownloads != null && list.size > maxDownloads) {
                add(maxDownloads, DownloadAdapterItem(null))
            }
        }

        super.submitList(newList, commitCallback)
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
        childCard: VisualDownloadCached.Child?
    ) {
        val context = binding.root.context
        val downloadInfo = getDownloadFileInfo(context, queueWrapper.id)

        val episodeCached =
            (queueWrapper.downloadItem?.resultId ?: childCard?.data?.parentId)?.toString()
                ?.let { folder ->
                    getKey<DownloadObjects.DownloadEpisodeCached>(
                        DOWNLOAD_EPISODE_CACHE,
                        getFolderName(folder, queueWrapper.id.toString())
                    )
                }

        binding.apply {
            separatorHolder.isGone = true
            downloadChildEpisodeHolder.isGone = false

            // The layout looks better if downloadChildEpisodeTextExtra is hidden when its empty, to make the other text centered.
            downloadChildEpisodeTextExtra.isVisible = false
            downloadChildEpisodeTextExtra.doOnTextChanged { text, _, _, _ ->
                downloadChildEpisodeTextExtra.isVisible = text == null || text.isNotEmpty()
            }

            val posDur = getViewPos(queueWrapper.id)
            downloadChildEpisodeProgress.apply {
                isVisible = posDur != null
                posDur?.let {
                    val visualPos = it.fixVisual()
                    max = (visualPos.duration / 1000).toInt()
                    progress = (visualPos.position / 1000).toInt()
                }
            }

            downloadButton.setPersistentId(queueWrapper.id)

            if (episodeCached != null) {
                downloadButton.setDefaultClickListener(
                    episodeCached, downloadChildEpisodeTextExtra
                ) {
                    handleDownloadClick(it)
//                    when (it.action) {
//                        DOWNLOAD_ACTION_DOWNLOAD -> {
//                            DownloadQueueManager.addToQueue(queueWrapper)
//                        }
//
//                        DOWNLOAD_ACTION_LONG_CLICK -> {
//
//                        }
//
//                        DOWNLOAD_ACTION_PAUSE_DOWNLOAD -> {
//                            VideoDownloadManager.downloadEvent.invoke(
//                                Pair(queueWrapper.id, VideoDownloadManager.DownloadActionType.Pause)
//                            )
//                        }
//                    }
                }
            }

            val status = VideoDownloadManager.downloadStatus[queueWrapper.id]
            downloadButton.resetView()
            downloadButton.setStatus(status)
//            val status = downloadButton.getStatus(item.id, card.currentBytes, card.totalBytes)

            if (status == DownloadStatusTell.IsDone) {
                // We do this here instead if we are finished downloading
                // so that we can use the value from the view model
                // rather than extra unneeded disk operations and to prevent a
                // delay in updating download icon state.
                if (downloadInfo != null) {
                    downloadButton.setProgress(downloadInfo.fileLength, downloadInfo.totalBytes)
                    downloadButton.applyMetaData(
                        queueWrapper.id,
                        downloadInfo.fileLength,
                        downloadInfo.totalBytes
                    )
                    downloadChildEpisodeTextExtra.text =
                        formatShortFileSize(
                            downloadChildEpisodeTextExtra.context,
                            downloadInfo.totalBytes
                        )
                }

                // We will let the view model handle this
                downloadButton.doSetProgress = false
                downloadButton.progressBar.progressDrawable =
                    downloadButton.getDrawableFromStatus(status)
                        ?.let { ContextCompat.getDrawable(downloadButton.context, it) }

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

            downloadChildEpisodeHolder.setOnClickListener {
//                onItemClickEvent.invoke(DownloadClickEvent(DOWNLOAD_ACTION_PLAY_FILE, data))
            }

//            downloadChildEpisodeHolder.apply {
//                when {
//                    setOnClickListener {
//                        onItemClickEvent.invoke(
//                            DownloadClickEvent(
//                                DOWNLOAD_ACTION_PLAY_FILE,
//                                data
//                            )
//                        )
//                    }
//                }
//
//            }

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
        val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN // Allow drag up/down
        val swipeFlags = 0 // Disable swipe functionality (set swipe flags if you need it)

        return makeMovementFlags(dragFlags, swipeFlags)
    }

    override fun onMove(
        recyclerView: RecyclerView,
        source: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        if (source.itemView.tag == DOWNLOAD_SEPARATOR_TAG) {
            println("TODO moved download separator.")
        }

        val fromPosition = source.absoluteAdapterPosition
        val toPosition = target.absoluteAdapterPosition
        adapter.notifyItemMoved(fromPosition, toPosition)
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

