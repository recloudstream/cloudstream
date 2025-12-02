package com.lagradost.cloudstream3.ui.download.queue

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.AcraApplication
import com.lagradost.cloudstream3.mvvm.launchSafe
import com.lagradost.cloudstream3.services.DownloadQueueService.Companion.downloadInstances
import com.lagradost.cloudstream3.ui.download.VisualDownloadCached
import com.lagradost.cloudstream3.utils.DOWNLOAD_EPISODE_CACHE
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.DataStore.getKeys
import com.lagradost.cloudstream3.utils.downloader.DownloadObjects
import com.lagradost.cloudstream3.utils.downloader.DownloadQueueManager
import com.lagradost.cloudstream3.utils.downloader.DownloadQueueManager.queue
import com.lagradost.cloudstream3.utils.downloader.VideoDownloadManager
import com.lagradost.cloudstream3.utils.downloader.VideoDownloadManager.getDownloadFileInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** An item in the adapter can either be a separator or a real item */
data class DownloadQueueAdapterInfo(
    val queueWrapper: DownloadObjects.DownloadQueueWrapper,
    val childCard: VisualDownloadCached.Child?,
)

class DownloadQueueViewModel : ViewModel() {
    private val _childCards = MutableLiveData<List<DownloadQueueAdapterInfo>>()
    val childCards: LiveData<List<DownloadQueueAdapterInfo>> = _childCards
    private val totalDownloadFlow =
        downloadInstances.combine(DownloadQueueManager.queue) { instances, queue ->
            val current = instances.map { it.downloadQueueWrapper }
            current + queue
        }.combine(VideoDownloadManager.currentDownloads) { total, _ ->
            // We want to update the flow when currentDownloads updates, but we do not care about its value
            total
        }

    init {
        viewModelScope.launch {
            totalDownloadFlow.collect { queue ->
                updateChildList(queue)
            }
        }
    }

    fun updateChildList(queue: List<DownloadObjects.DownloadQueueWrapper>) =
        viewModelScope.launchSafe {
            // Gets all the cached episodes and associates those with QueueWrappers
            // after that it merges it to a total list with all QueueWrappers (with and without cached episodes)
            val context = AcraApplication.context ?: return@launchSafe
            val totalQueue = queue.associate { it.id to DownloadQueueAdapterInfo(it, null) }

            val childCards = withContext(Dispatchers.IO) {
                context.getKeys(DOWNLOAD_EPISODE_CACHE)
                    .mapNotNull { key ->
                        // Filter away IDs not currently in queue
                        val childKey = key.substringAfterLast("/").toIntOrNull()
                        if (childKey == null || !totalQueue.contains(childKey)) {
                            return@mapNotNull null
                        }

                        context.getKey<DownloadObjects.DownloadEpisodeCached>(key)
                            ?: return@mapNotNull null
                    }
                    .mapNotNull { episode ->
                        val info =
                            getDownloadFileInfo(context, episode.id) ?: return@mapNotNull null
                        val child = VisualDownloadCached.Child(
                            currentBytes = info.fileLength,
                            totalBytes = info.totalBytes,
                            isSelected = false,
                            data = episode,
                        )
                        val item = totalQueue[episode.id] ?: return@mapNotNull null
                        episode.id to DownloadQueueAdapterInfo(
                            item.queueWrapper,
                            child
                        )
                    }.toMap()
            } + totalQueue

            val list = childCards.values.toList()

            _childCards.postValue(list)
        }

}