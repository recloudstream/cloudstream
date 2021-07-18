package com.lagradost.cloudstream3.ui.download

import android.content.Context
import android.os.Environment
import android.os.StatFs
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.utils.DOWNLOAD_EPISODE_CACHE
import com.lagradost.cloudstream3.utils.DOWNLOAD_HEADER_CACHE
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.DataStore.getKeys
import com.lagradost.cloudstream3.utils.VideoDownloadHelper
import com.lagradost.cloudstream3.utils.VideoDownloadManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DownloadViewModel : ViewModel() {

    private val _noDownloadsText = MutableLiveData<String>().apply {
        value = ""
    }
    val noDownloadsText: LiveData<String> = _noDownloadsText

    private val _headerCards =
        MutableLiveData<List<VisualDownloadHeaderCached>>().apply { listOf<VisualDownloadHeaderCached>() }
    val headerCards: LiveData<List<VisualDownloadHeaderCached>> = _headerCards

    private val _usedBytes = MutableLiveData<Long>()
    private val _availableBytes = MutableLiveData<Long>()
    private val _downloadBytes = MutableLiveData<Long>()

    val usedBytes: LiveData<Long> = _usedBytes
    val availableBytes: LiveData<Long> = _availableBytes
    val downloadBytes: LiveData<Long> = _downloadBytes

    fun updateList(context: Context) = viewModelScope.launch {
        val children = withContext(Dispatchers.IO) {
            val headers = context.getKeys(DOWNLOAD_EPISODE_CACHE)
            headers.mapNotNull { context.getKey<VideoDownloadHelper.DownloadEpisodeCached>(it) }
        }

        // parentId : bytes
        val bytesUsedByChild = HashMap<Int, Long>()
        // parentId : downloadsCount
        val totalDownloads = HashMap<Int, Int>()

        // Gets all children downloads
        withContext(Dispatchers.IO) {
            for (c in children) {
                val childFile = VideoDownloadManager.getDownloadFileInfoAndUpdateSettings(context, c.id)
                val len = childFile?.totalBytes ?: continue
                if (bytesUsedByChild.containsKey(c.parentId)) {
                    bytesUsedByChild[c.parentId] = bytesUsedByChild[c.parentId]?.plus(len) ?: len
                } else {
                    bytesUsedByChild[c.parentId] = len
                }

                if (totalDownloads.containsKey(c.parentId)) {
                    totalDownloads[c.parentId] = totalDownloads[c.parentId]?.plus(1) ?: 1
                } else {
                    totalDownloads[c.parentId] = 1
                }
            }
        }

        val cached = withContext(Dispatchers.IO) {
            val headers = context.getKeys(DOWNLOAD_HEADER_CACHE)
            headers.mapNotNull { context.getKey<VideoDownloadHelper.DownloadHeaderCached>(it) }
        }

        val visual = withContext(Dispatchers.IO) {
            cached.mapNotNull { // TODO FIX
                val downloads = totalDownloads[it.id] ?: 0
                val bytes = bytesUsedByChild[it.id] ?: 0
                if(bytes <= 0 || downloads <= 0) return@mapNotNull null
                VisualDownloadHeaderCached(0, downloads, bytes, it)
            }
        }

        val stat = StatFs(Environment.getExternalStorageDirectory().path)

        val localBytesAvailable = stat.availableBytes//stat.blockSizeLong * stat.blockCountLong
        val localTotalBytes = stat.blockSizeLong * stat.blockCountLong
        val localDownloadedBytes = visual.sumOf { it.totalBytes }

        _usedBytes.postValue(localTotalBytes - localBytesAvailable - localDownloadedBytes)
        _availableBytes.postValue(localBytesAvailable)
        _downloadBytes.postValue(localDownloadedBytes)

        _headerCards.postValue(visual)
    }
}