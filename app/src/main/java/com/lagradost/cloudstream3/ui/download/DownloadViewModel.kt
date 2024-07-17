package com.lagradost.cloudstream3.ui.download

import android.content.Context
import android.content.DialogInterface
import android.os.Environment
import android.os.StatFs
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.isEpisodeBased
import com.lagradost.cloudstream3.isMovieType
import com.lagradost.cloudstream3.mvvm.launchSafe
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.AppContextUtils.getNameFull
import com.lagradost.cloudstream3.utils.AppContextUtils.setDefaultFocus
import com.lagradost.cloudstream3.utils.DOWNLOAD_EPISODE_CACHE
import com.lagradost.cloudstream3.utils.DOWNLOAD_HEADER_CACHE
import com.lagradost.cloudstream3.utils.DataStore.getFolderName
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.DataStore.getKeys
import com.lagradost.cloudstream3.utils.VideoDownloadHelper
import com.lagradost.cloudstream3.utils.VideoDownloadManager.deleteFilesAndUpdateSettings
import com.lagradost.cloudstream3.utils.VideoDownloadManager.getDownloadFileInfoAndUpdateSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DownloadViewModel : ViewModel() {

    private val _headerCards =
        MutableLiveData<List<VisualDownloadCached.Header>>().apply { listOf<VisualDownloadCached.Header>() }
    val headerCards: LiveData<List<VisualDownloadCached.Header>> = _headerCards

    private val _childCards =
        MutableLiveData<List<VisualDownloadCached.Child>>().apply { listOf<VisualDownloadCached.Child>() }
    val childCards: LiveData<List<VisualDownloadCached.Child>> = _childCards

    private val _usedBytes = MutableLiveData<Long>()
    val usedBytes: LiveData<Long> = _usedBytes

    private val _availableBytes = MutableLiveData<Long>()
    val availableBytes: LiveData<Long> = _availableBytes

    private val _downloadBytes = MutableLiveData<Long>()
    val downloadBytes: LiveData<Long> = _downloadBytes

    private val _selectedBytes = MutableLiveData<Long>(0)
    val selectedBytes: LiveData<Long> = _selectedBytes

    private val _isMultiDeleteState = MutableLiveData(false)
    val isMultiDeleteState: LiveData<Boolean> = _isMultiDeleteState

    private val _selectedItemIds = MutableLiveData<MutableSet<Int>>(mutableSetOf())
    val selectedItemIds: LiveData<MutableSet<Int>> = _selectedItemIds

    private var previousVisual: List<VisualDownloadCached>? = null

    fun setIsMultiDeleteState(value: Boolean) {
        _isMultiDeleteState.postValue(value)
    }

    fun addSelected(itemId: Int) {
        val currentSelected = selectedItemIds.value ?: mutableSetOf()
        if (!currentSelected.contains(itemId)) {
            currentSelected.add(itemId)
            _selectedItemIds.postValue(currentSelected)
            updateSelectedBytes()
            updateSelectedCards()
        }
    }

    fun removeSelected(itemId: Int) {
        selectedItemIds.value?.let { selected ->
            selected.remove(itemId)
            _selectedItemIds.postValue(selected)
            updateSelectedBytes()
            updateSelectedCards()
        }
    }

    fun selectAllItems() {
        val currentSelected = selectedItemIds.value ?: mutableSetOf()
        val items = (headerCards.value ?: emptyList()) + (childCards.value ?: emptyList())
        if (items.isEmpty()) return
        items.forEach { item ->
            if (!currentSelected.contains(item.data.id)) {
                currentSelected.add(item.data.id)
            }
        }
        _selectedItemIds.postValue(currentSelected)
        updateSelectedBytes()
        updateSelectedCards()
    }

    fun clearSelectedItems() {
        // We need this to be done immediately
        // so we can't use postValue
        _selectedItemIds.value = mutableSetOf()
        updateSelectedCards()
    }

    fun isAllSelected(): Boolean {
        val currentSelected = selectedItemIds.value ?: return false

        val headerItems = headerCards.value
        val childItems = childCards.value

        if (headerItems != null &&
            headerItems.count() == currentSelected.count() &&
            headerItems.map { it.data.id }.containsAll(currentSelected)
        ) return true

        if (childItems != null &&
            childItems.count() == currentSelected.count() &&
            childItems.map { it.data.id }.containsAll(currentSelected)
        ) return true

        return false
    }

    private fun updateSelectedBytes() = viewModelScope.launchSafe {
        val selectedItemsList = getSelectedItemsData() ?: return@launchSafe
        var totalSelectedBytes = 0L

        selectedItemsList.forEach { item ->
            totalSelectedBytes += item.totalBytes
        }

        _selectedBytes.postValue(totalSelectedBytes)
    }

    private fun updateSelectedCards() = viewModelScope.launchSafe {
        val currentSelected = selectedItemIds.value ?: return@launchSafe
        val updatedHeaderCards = headerCards.value?.toMutableList()
        val updatedChildCards = childCards.value?.toMutableList()

        updatedHeaderCards?.forEach { header ->
            header.isSelected = currentSelected.contains(header.data.id)
        }

        updatedChildCards?.forEach { child ->
            child.isSelected = currentSelected.contains(child.data.id)
        }

        _headerCards.postValue(updatedHeaderCards)
        _childCards.postValue(updatedChildCards)
    }

    fun updateList(context: Context) = viewModelScope.launchSafe {
        val children = withContext(Dispatchers.IO) {
            context.getKeys(DOWNLOAD_EPISODE_CACHE)
                .mapNotNull { context.getKey<VideoDownloadHelper.DownloadEpisodeCached>(it) }
                .distinctBy { it.id } // Remove duplicates
        }

        // parentId : bytes
        val totalBytesUsedByChild = HashMap<Int, Long>()
        // parentId : bytes
        val currentBytesUsedByChild = HashMap<Int, Long>()
        // parentId : downloadsCount
        val totalDownloads = HashMap<Int, Int>()

        // Gets all children downloads
        withContext(Dispatchers.IO) {
            children.forEach { c ->
                val childFile =
                    getDownloadFileInfoAndUpdateSettings(context, c.id) ?: return@forEach

                if (childFile.fileLength <= 1) return@forEach
                val len = childFile.totalBytes
                val flen = childFile.fileLength

                totalBytesUsedByChild[c.parentId] =
                    totalBytesUsedByChild[c.parentId]?.plus(len) ?: len
                currentBytesUsedByChild[c.parentId] =
                    currentBytesUsedByChild[c.parentId]?.plus(flen) ?: flen
                totalDownloads[c.parentId] = totalDownloads[c.parentId]?.plus(1) ?: 1
            }
        }

        val cached = withContext(Dispatchers.IO) { // Won't fetch useless keys
            totalDownloads.entries.filter { it.value > 0 }.mapNotNull {
                context.getKey<VideoDownloadHelper.DownloadHeaderCached>(
                    DOWNLOAD_HEADER_CACHE,
                    it.key.toString()
                )
            }
        }

        val visual = withContext(Dispatchers.IO) {
            cached.mapNotNull {
                val downloads = totalDownloads[it.id] ?: 0
                val bytes = totalBytesUsedByChild[it.id] ?: 0
                val currentBytes = currentBytesUsedByChild[it.id] ?: 0
                if (bytes <= 0 || downloads <= 0) return@mapNotNull null
                val isSelected = selectedItemIds.value?.contains(it.id) ?: false
                val movieEpisode =
                    if (!it.type.isMovieType()) null
                    else context.getKey<VideoDownloadHelper.DownloadEpisodeCached>(
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
            }.sortedBy {
                (it.child?.episode ?: 0) + (it.child?.season?.times(10000) ?: 0)
            } // Episode sorting by episode, lowest to highest
        }

        // Only update list if different from the previous one to prevent duplicate initialization
        if (visual != previousVisual) {
            previousVisual = visual
            updateStorageStats(visual)
            _headerCards.postValue(visual)
        }
    }

    fun updateChildList(
        context: Context,
        folder: String
    ) = viewModelScope.launchSafe {
        val data = withContext(Dispatchers.IO) { context.getKeys(folder) }
        val visual = withContext(Dispatchers.IO) {
            data.mapNotNull { key ->
                context.getKey<VideoDownloadHelper.DownloadEpisodeCached>(key)
            }.mapNotNull {
                val isSelected = selectedItemIds.value?.contains(it.id) ?: false
                val info = getDownloadFileInfoAndUpdateSettings(context, it.id)
                    ?: return@mapNotNull null
                VisualDownloadCached.Child(
                    currentBytes = info.fileLength,
                    totalBytes = info.totalBytes,
                    isSelected = isSelected,
                    data = it,
                )
            }
        }.sortedBy { it.data.episode + (it.data.season ?: 0) * 100000 }

        if (previousVisual != visual) {
            previousVisual = visual
            _childCards.postValue(visual)
        }
    }

    private fun updateStorageStats(visual: List<VisualDownloadCached.Header>) {
        try {
            val stat = StatFs(Environment.getExternalStorageDirectory().path)
            val localBytesAvailable = stat.availableBytes
            val localTotalBytes = stat.blockSizeLong * stat.blockCountLong
            val localDownloadedBytes = visual.sumOf { it.totalBytes }

            _usedBytes.postValue(localTotalBytes - localBytesAvailable - localDownloadedBytes)
            _availableBytes.postValue(localBytesAvailable)
            _downloadBytes.postValue(localDownloadedBytes)
        } catch (t: Throwable) {
            _downloadBytes.postValue(0)
            logError(t)
        }
    }

    fun handleMultiDelete(
        context: Context,
        onDeleteConfirm: () -> Unit
    ) = viewModelScope.launchSafe {
        val selectedItemsList = getSelectedItemsData() ?: emptyList()
        val deleteData = processSelectedItems(context, selectedItemsList)
        val message = buildDeleteMessage(context, deleteData)
        showDeleteConfirmationDialog(context, message, deleteData.ids, onDeleteConfirm)
    }

    fun handleSingleDelete(
        context: Context,
        itemId: Int,
        onDeleteConfirm: () -> Unit
    ) = viewModelScope.launchSafe {
        val itemData = getItemDataFromId(itemId)
        val deleteData = processSelectedItems(context, itemData)
        val message = buildDeleteMessage(context, deleteData)
        showDeleteConfirmationDialog(context, message, deleteData.ids, onDeleteConfirm)
    }

    private fun getSelectedItemsData(): List<VisualDownloadCached>? {
        val selectedIds = selectedItemIds.value ?: return null
        val headers = headerCards.value ?: emptyList()
        val children = childCards.value ?: emptyList()

        return (headers + children).filter { item ->
            selectedIds.contains(item.data.id)
        }
    }

    private fun getItemDataFromId(itemId: Int): List<VisualDownloadCached> {
        val headers = headerCards.value ?: emptyList()
        val children = childCards.value ?: emptyList()

        return (headers + children).filter { item ->
            item.data.id == itemId
        }
    }

    private fun processSelectedItems(
        context: Context,
        selectedItemsList: List<VisualDownloadCached>
    ): DeleteData {
        val ids = mutableListOf<Int>()
        val seriesNames = mutableListOf<String>()
        val names = mutableListOf<String>()
        var parentName: String? = null

        selectedItemsList.forEach { item ->
            when (item) {
                is VisualDownloadCached.Header -> {
                    if (item.data.type.isEpisodeBased()) {
                        val episodes = context.getKeys(DOWNLOAD_EPISODE_CACHE)
                            .mapNotNull {
                                context.getKey<VideoDownloadHelper.DownloadEpisodeCached>(
                                    it
                                )
                            }
                            .filter { it.parentId == item.data.id }
                            .map { it.id }
                        ids.addAll(episodes)

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
                    val parent = context.getKey<VideoDownloadHelper.DownloadHeaderCached>(
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

        return DeleteData(ids, seriesNames, names, parentName)
    }

    private fun buildDeleteMessage(
        context: Context,
        data: DeleteData
    ): String {
        val formattedNames = data.names.joinToString(separator = "\n") { "• $it" }
        val formattedSeriesNames = data.seriesNames.joinToString(separator = "\n") { "• $it" }

        return when {
            data.ids.count() == 1 -> {
                context.getString(R.string.delete_message).format(
                    data.names.firstOrNull()
                )
            }

            data.seriesNames.isNotEmpty() && data.names.isEmpty() -> {
                context.getString(R.string.delete_message_series_only).format(formattedSeriesNames)
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
        ids: List<Int>,
        onDeleteConfirm: () -> Unit
    ) {
        val builder = AlertDialog.Builder(context)
        val dialogClickListener =
            DialogInterface.OnClickListener { _, which ->
                when (which) {
                    DialogInterface.BUTTON_POSITIVE -> {
                        viewModelScope.launchSafe {
                            deleteFilesAndUpdateSettings(context, ids, this)
                            setIsMultiDeleteState(false)
                            onDeleteConfirm.invoke()
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

    private data class DeleteData(
        val ids: List<Int>,
        val seriesNames: List<String>,
        val names: List<String>,
        val parentName: String?
    )
}