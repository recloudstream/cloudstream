package com.lagradost.cloudstream3.ui.download

import android.content.Context
import android.os.Environment
import android.os.StatFs
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.isMovieType
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.DOWNLOAD_EPISODE_CACHE
import com.lagradost.cloudstream3.utils.DOWNLOAD_HEADER_CACHE
import com.lagradost.cloudstream3.utils.DataStore.getFolderName
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
            for (c in children) {
                val childFile = VideoDownloadManager.getDownloadFileInfoAndUpdateSettings(context, c.id) ?: continue

                if (childFile.fileLength <= 1) continue
                val len = childFile.totalBytes
                val flen = childFile.fileLength

                totalBytesUsedByChild[c.parentId] = totalBytesUsedByChild[c.parentId]?.plus(len) ?: len
                currentBytesUsedByChild[c.parentId] = currentBytesUsedByChild[c.parentId]?.plus(flen) ?: flen
                totalDownloads[c.parentId] = totalDownloads[c.parentId]?.plus(1) ?: 1
            }
        }

        val cached = withContext(Dispatchers.IO) { // wont fetch useless keys
            totalDownloads.entries.filter { it.value > 0 }.mapNotNull {
                context.getKey<VideoDownloadHelper.DownloadHeaderCached>(
                    DOWNLOAD_HEADER_CACHE,
                    it.key.toString()
                )
            }
        }

        val visual = withContext(Dispatchers.IO) {
            cached.mapNotNull { // TODO FIX
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
                VisualDownloadHeaderCached(
                    0,
                    downloads,
                    bytes,
                    currentBytes,
                    it,
                    movieEpisode
                )
            }.sortedBy {
                (it.child?.episode ?: 0) + (it.child?.season?.times(10000) ?: 0)
            } // episode sorting by episode, lowest to highest
        }
        try {
            val stat = StatFs(Environment.getExternalStorageDirectory().path)

            val localBytesAvailable = stat.availableBytes//stat.blockSizeLong * stat.blockCountLong
            val localTotalBytes = stat.blockSizeLong * stat.blockCountLong
            val localDownloadedBytes = visual.sumOf { it.totalBytes }

            _usedBytes.postValue(localTotalBytes - localBytesAvailable - localDownloadedBytes)
            _availableBytes.postValue(localBytesAvailable)
            _downloadBytes.postValue(localDownloadedBytes)
        } catch (e : Exception) {
            _downloadBytes.postValue(0)
            logError(e)
        }

        _headerCards.postValue(visual)
    }
}
