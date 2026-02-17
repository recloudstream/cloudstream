package com.lagradost.cloudstream3.ui.download

import android.content.Context
import android.content.DialogInterface
import android.os.Environment
import android.os.StatFs
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.api.Log
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.isEpisodeBased
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.launchSafe
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.services.DownloadQueueService
import com.lagradost.cloudstream3.services.DownloadQueueService.Companion.downloadInstances
import com.lagradost.cloudstream3.utils.AppContextUtils.getNameFull
import com.lagradost.cloudstream3.utils.AppContextUtils.setDefaultFocus
import com.lagradost.cloudstream3.utils.ConsistentLiveData
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.Coroutines.ioWork
import com.lagradost.cloudstream3.utils.DOWNLOAD_EPISODE_CACHE
import com.lagradost.cloudstream3.utils.DOWNLOAD_EPISODE_CACHE_BACKUP
import com.lagradost.cloudstream3.utils.DOWNLOAD_HEADER_CACHE
import com.lagradost.cloudstream3.utils.DOWNLOAD_HEADER_CACHE_BACKUP
import com.lagradost.cloudstream3.utils.DataStore.getFolderName
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.DataStore.getKeys
import com.lagradost.cloudstream3.utils.DataStore.getSharedPrefs
import com.lagradost.cloudstream3.utils.DataStoreHelper.getAllResumeStateIds
import com.lagradost.cloudstream3.utils.DataStoreHelper.getLastWatched
import com.lagradost.cloudstream3.utils.ResourceLiveData
import com.lagradost.cloudstream3.utils.downloader.DownloadObjects
import com.lagradost.cloudstream3.utils.downloader.DownloadQueueManager
import com.lagradost.cloudstream3.utils.downloader.VideoDownloadManager.deleteFilesAndUpdateSettings
import com.lagradost.cloudstream3.utils.downloader.VideoDownloadManager.getDownloadFileInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DownloadViewModel : ViewModel() {
    companion object {
        const val TAG = "DownloadViewModel"
    }

    private val _headerCards =
        ResourceLiveData<List<VisualDownloadCached.Header>>(Resource.Loading())
    val headerCards: LiveData<Resource<List<VisualDownloadCached.Header>>> = _headerCards

    private val _childCards = ResourceLiveData<List<VisualDownloadCached.Child>>(Resource.Loading())
    val childCards: LiveData<Resource<List<VisualDownloadCached.Child>>> = _childCards

    private val _usedBytes = ConsistentLiveData<Long>()
    val usedBytes: LiveData<Long> = _usedBytes

    private val _availableBytes = ConsistentLiveData<Long>()
    val availableBytes: LiveData<Long> = _availableBytes

    private val _downloadBytes = ConsistentLiveData<Long>()
    val downloadBytes: LiveData<Long> = _downloadBytes

    private val _selectedBytes = ConsistentLiveData<Long>(0)
    val selectedBytes: LiveData<Long> = _selectedBytes

    private val _selectedItemIds = ConsistentLiveData<Set<Int>?>(null)
    val selectedItemIds: LiveData<Set<Int>?> = _selectedItemIds


    fun cancelSelection() {
        updateSelectedItems { null }
    }

    fun addSelected(itemId: Int) {
        updateSelectedItems { it?.plus(itemId) ?: setOf(itemId) }
    }

    fun removeSelected(itemId: Int) {
        updateSelectedItems { it?.minus(itemId) ?: emptySet() }
    }

    fun selectAllHeaders() {
        updateSelectedItems {
            _headerCards.success.orEmpty()
                .map { item -> item.data.id }.toSet()
        }
    }

    fun selectAllChildren() {
        updateSelectedItems {
            _childCards.success.orEmpty()
                .map { item -> item.data.id }.toSet()
        }
    }

    fun clearSelectedItems() {
        // We need this to be done immediately
        // so we can't use postValue
        updateSelectedItems { emptySet() }
    }

    fun isAllChildrenSelected(): Boolean {
        val currentSelected = selectedItemIds.value ?: return false
        val children = _childCards.success.orEmpty()
        return currentSelected.size == children.size && children.all { it.data.id in currentSelected }
    }

    fun isAllHeadersSelected(): Boolean {
        val currentSelected = selectedItemIds.value ?: return false
        val headers = _headerCards.success.orEmpty()
        return currentSelected.size == headers.size && headers.all { it.data.id in currentSelected }
    }

    private fun updateSelectedItems(action: (Set<Int>?) -> Set<Int>?) {
        val currentSelected = action(selectedItemIds.value)
        _selectedItemIds.postValue(currentSelected)
        postHeaders()
        postChildren()
        updateSelectedBytes()
    }

    private fun updateSelectedBytes() = viewModelScope.launchSafe {
        val selectedItemsList = getSelectedItemsData() ?: return@launchSafe
        val totalSelectedBytes = selectedItemsList.sumOf { it.totalBytes }
        _selectedBytes.postValue(totalSelectedBytes)
    }


    fun removeRedundantEpisodeKeys(context: Context, keys: List<Pair<Int, Int>>) {
        val settingsManager = context.getSharedPrefs()
        ioSafe {
            settingsManager.edit {
                keys.forEach { (parentId, childId) ->
                    Log.i(TAG, "Removing download episode key: ${parentId}/${childId}")
                    val oldPath = getFolderName(
                        getFolderName(
                            DOWNLOAD_EPISODE_CACHE,
                            parentId.toString()
                        ),
                        childId.toString()
                    )
                    val newPath = getFolderName(
                        getFolderName(
                            DOWNLOAD_EPISODE_CACHE_BACKUP,
                            parentId.toString()
                        ),
                        childId.toString()
                    )

                    val oldPref = settingsManager.getString(oldPath, null)
                    // Cowardly future backup solution in case the key removal fails in some edge case.
                    // This and all backup keys may be removed in a future update if the key removal is proven to be robust.
                    this.putString(newPath, oldPref)
                    this.remove(oldPath)
                }
            }
        }
    }

    fun removeRedundantHeaderKeys(
        context: Context,
        cached: List<DownloadObjects.DownloadHeaderCached>,
        totalBytesUsedByChild: Map<Int, Long>,
        totalDownloads: Map<Int, Int>
    ) {
        val settingsManager = context.getSharedPrefs()
        ioSafe {
           // Do not remove headers used by resume watching
            val resumeWatchingIds =
                getAllResumeStateIds()?.mapNotNull { id ->
                    getLastWatched(id)?.parentId
                }?.toSet() ?: emptySet()

            settingsManager.edit {
                cached.forEach { header ->
                    val downloads = totalDownloads[header.id] ?: 0
                    val bytes = totalBytesUsedByChild[header.id] ?: 0

                    if ( (downloads <= 0 || bytes <= 0) && !resumeWatchingIds.contains(header.id) ) {
                        Log.i(TAG, "Removing download header key: ${header.id}")
                        val oldPAth = getFolderName(DOWNLOAD_HEADER_CACHE, header.id.toString())
                        val newPath =
                            getFolderName(DOWNLOAD_HEADER_CACHE_BACKUP, header.id.toString())
                        val oldPref = settingsManager.getString(oldPAth, null)
                        // Cowardly future backup solution in case the key removal fails in some edge case.
                        // This and all backup keys may be removed in a future update if the key removal is proven to be robust.
                        this.putString(newPath, oldPref)
                        this.remove(oldPAth)
                    }
                }
            }
        }
    }

    fun updateHeaderList(context: Context) = viewModelScope.launchSafe {
        // Do not push loading as it interrupts the UI
        //_headerCards.postValue(Resource.Loading())

        val visual = ioWork {
            val children = context.getKeys(DOWNLOAD_EPISODE_CACHE)
                .mapNotNull { context.getKey<DownloadObjects.DownloadEpisodeCached>(it) }
                .distinctBy { it.id } // Remove duplicates

            val isCurrentlyDownloading =
                DownloadQueueService.isRunning || downloadInstances.value.isNotEmpty() || DownloadQueueManager.queue.value.isNotEmpty()

            val downloadStats =
                calculateDownloadStats(context, children)

            val cached = context.getKeys(DOWNLOAD_HEADER_CACHE)
                .mapNotNull { context.getKey<DownloadObjects.DownloadHeaderCached>(it) }

            // Download stats and header keys may change when downloading.
            // To prevent the downloader and key removal from colliding, simply do not prune keys when downloading.
            if (!isCurrentlyDownloading) {
                removeRedundantHeaderKeys(
                    context,
                    cached,
                    downloadStats.totalBytesUsedByChild,
                    downloadStats.totalDownloads
                )
            }
            // calculateDownloadStats already performed checks when creating redundantDownloads, no extra check required
            removeRedundantEpisodeKeys(context, downloadStats.redundantDownloads)

            createVisualDownloadList(
                context,
                cached,
                downloadStats.totalBytesUsedByChild,
                downloadStats.currentBytesUsedByChild,
                downloadStats.totalDownloads
            )
        }

        updateStorageStats(visual)
        postHeaders(visual)
    }

    fun postHeaders(newValue: List<VisualDownloadCached.Header>? = null) {
        val newValue = newValue ?: _headerCards.success ?: return
        val selection = selectedItemIds.value ?: emptySet()
        _headerCards.postValue(Resource.Success(newValue.map {
            it.copy(
                isSelected = selection.contains(
                    it.data.id
                )
            )
        }))
    }

    fun postChildren(newValue: List<VisualDownloadCached.Child>? = null) {
        val newValue = newValue ?: _childCards.success ?: return
        val selection = selectedItemIds.value ?: emptySet()
        _childCards.postValue(Resource.Success(newValue.map {
            it.copy(
                isSelected = selection.contains(
                    it.data.id
                )
            )
        }))
    }

    private data class DownloadStats(
        val totalBytesUsedByChild: Map<Int, Long>,
        val currentBytesUsedByChild: Map<Int, Long>,
        val totalDownloads: Map<Int, Int>,
        /** Parent ID to child ID. Keys to be removed. */
        val redundantDownloads: List<Pair<Int, Int>>
    )

    private fun calculateDownloadStats(
        context: Context,
        children: List<DownloadObjects.DownloadEpisodeCached>
    ): DownloadStats {
        // parentId : bytes
        val totalBytesUsedByChild = mutableMapOf<Int, Long>()
        // parentId : bytes
        val currentBytesUsedByChild = mutableMapOf<Int, Long>()
        // parentId : downloadsCount
        val totalDownloads = mutableMapOf<Int, Int>()
        val redundantDownloads = mutableListOf<Pair<Int, Int>>()

        children.forEach { child ->
            val childFile = getDownloadFileInfo(context, child.id)

            if (childFile == null) {
                // It may not be a redundant child if something is currently downloading.
                // DOWNLOAD_EPISODE_CACHE gets created before KEY_DOWNLOAD_INFO in the downloader
                // leading to valid situations where getDownloadFileInfo is null, but we do not want to remove DOWNLOAD_EPISODE_CACHE
                if (!DownloadQueueService.isRunning && downloadInstances.value.isEmpty() && DownloadQueueManager.queue.value.isEmpty()) {
                    redundantDownloads.add(child.parentId to child.id)
                }
                return@forEach
            }
            if (childFile.fileLength <= 1) return@forEach

            val len = childFile.totalBytes
            val flen = childFile.fileLength

            totalBytesUsedByChild.merge(child.parentId, len, Long::plus)
            currentBytesUsedByChild.merge(child.parentId, flen, Long::plus)
            totalDownloads.merge(child.parentId, 1, Int::plus)
        }
        return DownloadStats(
            totalBytesUsedByChild,
            currentBytesUsedByChild,
            totalDownloads,
            redundantDownloads
        )
    }

    private fun createVisualDownloadList(
        context: Context,
        cached: List<DownloadObjects.DownloadHeaderCached>,
        totalBytesUsedByChild: Map<Int, Long>,
        currentBytesUsedByChild: Map<Int, Long>,
        totalDownloads: Map<Int, Int>
    ): List<VisualDownloadCached.Header> {
        return cached.mapNotNull {
            val downloads = totalDownloads[it.id] ?: 0
            val bytes = totalBytesUsedByChild[it.id] ?: 0
            val currentBytes = currentBytesUsedByChild[it.id] ?: 0

            if (bytes <= 0 || downloads <= 0) {
                return@mapNotNull null
            }

            val isSelected = selectedItemIds.value?.contains(it.id) ?: false
            val movieEpisode =
                if (it.type.isEpisodeBased()) null else context.getKey<DownloadObjects.DownloadEpisodeCached>(
                    DOWNLOAD_EPISODE_CACHE,
                    getFolderName(it.id.toString(), it.id.toString())
                )

            VisualDownloadCached.Header(
                currentBytes = currentBytes,
                totalBytes = bytes,
                data = it,
                child = movieEpisode,
                currentOngoingDownloads = 0,
                totalDownloads = downloads,
                isSelected = isSelected,
            )
            // Prevent order being almost completely random,
            // making things difficult to find.
        }.sortedWith(compareBy<VisualDownloadCached.Header> {
            // Sort by isEpisodeBased() ascending. We put those that
            // are episode based at the bottom for UI purposes and to
            // make it easier to find by grouping them together.
            it.data.type.isEpisodeBased()
        }.thenBy {
            // Then we sort alphabetically by name (case-insensitive).
            // Again, we do this to make things easier to find.
            it.data.name.lowercase()
        })
    }

    fun updateChildList(context: Context, folder: String) = viewModelScope.launchSafe {
        _childCards.postValue(Resource.Loading()) // always push loading

        val visual = withContext(Dispatchers.IO) {
            context.getKeys(folder).mapNotNull { key ->
                context.getKey<DownloadObjects.DownloadEpisodeCached>(key)
            }.mapNotNull {
                val isSelected = selectedItemIds.value?.contains(it.id) ?: false
                val info = getDownloadFileInfo(context, it.id) ?: return@mapNotNull null
                VisualDownloadCached.Child(
                    currentBytes = info.fileLength,
                    totalBytes = info.totalBytes,
                    isSelected = isSelected,
                    data = it,
                )
            }
        }.sortedWith(
            compareBy(
                // Sort by season first, and then by episode number,
                // to ensure sorting is consistent.
                { it.data.season ?: 0 },
                { it.data.episode }
            ))

        postChildren(visual)
    }

    private fun removeItems(idsToRemove: Set<Int>) = viewModelScope.launchSafe {
        _selectedItemIds.postValue(null)
        postHeaders(_headerCards.success?.filter { it.data.id !in idsToRemove })
        postChildren(_childCards.success?.filter { it.data.id !in idsToRemove })
    }

    private fun updateStorageStats(visual: List<VisualDownloadCached.Header>) {
        try {
            val stat = StatFs(Environment.getExternalStorageDirectory().path)
            val localBytesAvailable = stat.availableBytes
            val localTotalBytes = stat.blockSizeLong * stat.blockCountLong
            val localDownloadedBytes = visual.sumOf { it.totalBytes }
            val localUsedBytes = localTotalBytes - localBytesAvailable
            _usedBytes.postValue(localUsedBytes)
            _availableBytes.postValue(localBytesAvailable)
            _downloadBytes.postValue(localDownloadedBytes)
        } catch (t: Throwable) {
            _downloadBytes.postValue(0)
            logError(t)
        }
    }

    fun handleMultiDelete(context: Context) = viewModelScope.launchSafe {
        val selectedItemsList = getSelectedItemsData().orEmpty()
        val deleteData = processSelectedItems(context, selectedItemsList)
        val message = buildDeleteMessage(context, deleteData)
        showDeleteConfirmationDialog(context, message, deleteData.ids, deleteData.parentIds)
    }

    fun handleSingleDelete(
        context: Context,
        itemId: Int
    ) = viewModelScope.launchSafe {
        val itemData = getItemDataFromId(itemId)
        val deleteData = processSelectedItems(context, itemData)
        val message = buildDeleteMessage(context, deleteData)
        showDeleteConfirmationDialog(context, message, deleteData.ids, deleteData.parentIds)
    }

    private fun processSelectedItems(
        context: Context,
        selectedItemsList: List<VisualDownloadCached>
    ): DeleteData {
        val names = mutableListOf<String>()
        val seriesNames = mutableListOf<String>()

        val ids = mutableSetOf<Int>()
        val parentIds = mutableSetOf<Int>()

        var parentName: String? = null

        selectedItemsList.forEach { item ->
            when (item) {
                is VisualDownloadCached.Header -> {
                    if (item.data.type.isEpisodeBased()) {
                        val episodes = context.getKeys(DOWNLOAD_EPISODE_CACHE)
                            .mapNotNull {
                                context.getKey<DownloadObjects.DownloadEpisodeCached>(
                                    it
                                )
                            }
                            .filter { it.parentId == item.data.id }
                            .map { it.id }
                        ids.addAll(episodes)
                        parentIds.add(item.data.id)

                        val episodeInfo = "${item.data.name} (${item.totalDownloads} ${
                            context.resources.getQuantityString(
                                R.plurals.episodes,
                                item.totalDownloads
                            ).lowercase()
                        })"
                        seriesNames.add(episodeInfo)
                    } else {
                        ids.add(item.data.id)
                        names.add(item.data.name)
                    }
                }

                is VisualDownloadCached.Child -> {
                    ids.add(item.data.id)
                    val parent = context.getKey<DownloadObjects.DownloadHeaderCached>(
                        DOWNLOAD_HEADER_CACHE,
                        item.data.parentId.toString()
                    )
                    parentName = parent?.name
                    names.add(
                        context.getNameFull(
                            item.data.name,
                            item.data.episode,
                            item.data.season
                        )
                    )
                }
            }
        }

        return DeleteData(ids, parentIds, seriesNames, names, parentName)
    }

    private fun buildDeleteMessage(
        context: Context,
        data: DeleteData
    ): String {
        val formattedNames = data.names.sortedBy { it.lowercase() }
            .joinToString(separator = "\n") { "• $it" }
        val formattedSeriesNames = data.seriesNames.sortedBy { it.lowercase() }
            .joinToString(separator = "\n") { "• $it" }

        return when {
            data.seriesNames.isNotEmpty() && data.names.isEmpty() -> {
                context.getString(R.string.delete_message_series_only).format(formattedSeriesNames)
            }

            data.ids.count() == 1 -> {
                context.getString(R.string.delete_message).format(
                    data.names.firstOrNull()
                )
            }

            data.parentName != null && data.names.isNotEmpty() -> {
                context.getString(R.string.delete_message_series_episodes)
                    .format(data.parentName, formattedNames)
            }

            data.seriesNames.isNotEmpty() -> {
                val seriesSection = context.getString(R.string.delete_message_series_section)
                    .format(formattedSeriesNames)
                context.getString(R.string.delete_message_multiple)
                    .format(formattedNames) + "\n\n" + seriesSection
            }

            else -> context.getString(R.string.delete_message_multiple).format(formattedNames)
        }
    }

    private fun showDeleteConfirmationDialog(
        context: Context,
        message: String,
        ids: Set<Int>,
        parentIds: Set<Int>
    ) {
        val builder = AlertDialog.Builder(context)
        val dialogClickListener =
            DialogInterface.OnClickListener { _, which ->
                when (which) {
                    DialogInterface.BUTTON_POSITIVE -> {
                        viewModelScope.launchSafe {
                            deleteFilesAndUpdateSettings(context, ids, this) { successfulIds ->
                                // We always remove parent because if we are deleting from here
                                // and we have it as non-empty, it was triggered on
                                // parent header card
                                removeItems(successfulIds + parentIds)
                            }
                        }
                    }

                    DialogInterface.BUTTON_NEGATIVE -> {
                        // Do nothing on cancel
                    }
                }
            }

        try {
            val title = if (ids.count() == 1) {
                R.string.delete_file
            } else R.string.delete_files
            builder.setTitle(title)
                .setMessage(message)
                .setPositiveButton(R.string.delete, dialogClickListener)
                .setNegativeButton(R.string.cancel, dialogClickListener)
                .show().setDefaultFocus()
        } catch (e: Exception) {
            logError(e)
        }
    }

    private fun getSelectedItemsData(): List<VisualDownloadCached>? {
        val headers = _headerCards.success.orEmpty()
        val children = _childCards.success.orEmpty()

        return selectedItemIds.value?.mapNotNull { id ->
            headers.find { it.data.id == id } ?: children.find { it.data.id == id }
        }
    }

    private fun getItemDataFromId(itemId: Int): List<VisualDownloadCached> {
        return (_headerCards.success.orEmpty() + _childCards.success.orEmpty()).filter { it.data.id == itemId }
    }

    fun clearChildren() {
        _childCards.postValue(Resource.Loading())
    }

    private data class DeleteData(
        val ids: Set<Int>,
        val parentIds: Set<Int>,
        val seriesNames: List<String>,
        val names: List<String>,
        val parentName: String?
    )
}