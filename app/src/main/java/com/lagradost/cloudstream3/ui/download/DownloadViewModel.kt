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

    private val _usedBytes = MutableLiveData<Long>()
    private val _availableBytes = MutableLiveData<Long>()
    private val _downloadBytes = MutableLiveData<Long>()

    private val _selectedItems = MutableLiveData<MutableList<VisualDownloadCached>>(mutableListOf())

    val usedBytes: LiveData<Long> = _usedBytes
    val availableBytes: LiveData<Long> = _availableBytes
    val downloadBytes: LiveData<Long> = _downloadBytes

    val selectedItems: LiveData<MutableList<VisualDownloadCached>> = _selectedItems

    private var previousVisual: List<VisualDownloadCached.Header>? = null

    fun addSelected(item: VisualDownloadCached) {
        val currentSelected = selectedItems.value ?: mutableListOf()
        if (!currentSelected.contains(item)) {
            currentSelected.add(item)
            _selectedItems.postValue(currentSelected)
        }
    }

    fun removeSelected(item: VisualDownloadCached) {
        selectedItems.value?.let { selected ->
            selected.remove(item)
            _selectedItems.postValue(selected)
        }
    }

    fun selectAllItems() {
        val currentSelected = selectedItems.value ?: mutableListOf()
        val items = headerCards.value ?: return
        items.forEach { item ->
            if (!currentSelected.contains(item)) {
                currentSelected.add(item)
            }
        }
        _selectedItems.postValue(currentSelected)
    }

    fun clearSelectedItems() {
        _selectedItems.postValue(mutableListOf())
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
                val childFile = getDownloadFileInfoAndUpdateSettings(context, c.id) ?: return@forEach

                if (childFile.fileLength <= 1) return@forEach
                val len = childFile.totalBytes
                val flen = childFile.fileLength

                totalBytesUsedByChild[c.parentId] = totalBytesUsedByChild[c.parentId]?.plus(len) ?: len
                currentBytesUsedByChild[c.parentId] = currentBytesUsedByChild[c.parentId]?.plus(flen) ?: flen
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

    fun handleMultiDelete(context: Context) = viewModelScope.launchSafe {
        val selectedItemsList = selectedItems.value ?: mutableListOf()

        val ids = selectedItemsList.map { it.data.id }

        val (seriesNames, names) = selectedItemsList.map { item ->
            when (item) {
                is VisualDownloadCached.Header -> {
                    if (item.data.type.isEpisodeBased()) {
                        val episodeInfo = "${item.data.name} (${item.totalDownloads} ${
                            context.resources.getQuantityString(
                                R.plurals.episodes,
                                item.totalDownloads
                            ).lowercase()
                        })"
                        episodeInfo to null
                    } else null to item.data.name
                }

                is VisualDownloadCached.Child -> null to item.data.name
            }
        }.unzip()

        showDeleteConfirmationDialog(context, ids, names.filterNotNull(), seriesNames.filterNotNull())
    }

    private fun showDeleteConfirmationDialog(
        context: Context,
        ids: List<Int>,
        names: List<String>,
        seriesNames: List<String>
    ) {
        val formattedNames = names.joinToString(separator = "\n") { "• $it" }
        val formattedSeriesNames = seriesNames.joinToString(separator = "\n") { "• $it" }

        val message = when {
            seriesNames.isNotEmpty() && names.isEmpty() -> {
                context.getString(R.string.delete_message_series_only).format(formattedSeriesNames)
            }
            seriesNames.isNotEmpty() -> {
                val seriesSection = context.getString(R.string.delete_message_series_section).format(formattedSeriesNames)
                context.getString(R.string.delete_message_multiple).format(formattedNames) + "\n\n" + seriesSection
            }
            else -> context.getString(R.string.delete_message_multiple).format(formattedNames)
        }

        val builder: AlertDialog.Builder = AlertDialog.Builder(context)
        val dialogClickListener =
            DialogInterface.OnClickListener { _, which ->
                when (which) {
                    DialogInterface.BUTTON_POSITIVE -> {
                        viewModelScope.launchSafe {
                            deleteFilesAndUpdateSettings(context, ids, this)
                            clearSelectedItems()
                            updateList(context)
                        }
                    }
                    DialogInterface.BUTTON_NEGATIVE -> {
                        // Do nothing on cancel
                    }
                }
            }

        try {
            builder.setTitle(R.string.delete_files)
                .setMessage(message)
                .setPositiveButton(R.string.delete, dialogClickListener)
                .setNegativeButton(R.string.cancel, dialogClickListener)
                .show().setDefaultFocus()
        } catch (e: Exception) {
            logError(e)
        }
    }
}