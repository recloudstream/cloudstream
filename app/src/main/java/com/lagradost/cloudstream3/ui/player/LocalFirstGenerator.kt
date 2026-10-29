package com.lagradost.cloudstream3.ui.player

import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.CloudStreamApp.Companion.context
import com.lagradost.cloudstream3.services.DownloadQueueService
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.downloader.LocalDownloadPolicy
import com.lagradost.cloudstream3.utils.downloader.DownloadObjects
import com.lagradost.cloudstream3.utils.downloader.VideoDownloadManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

class LocalFirstGenerator(private val remote: RepoLinkGenerator) : VideoGenerator<ResultEpisode>(remote.videos) {
    override val hasCache = remote.hasCache
    override val canSkipLoading = remote.canSkipLoading
    override fun getId(index: Int) = remote.getId(index)

    override suspend fun generateLinks(
        clearCache: Boolean,
        sourceTypes: Set<ExtractorLinkType>,
        callback: (Pair<ExtractorLink?, ExtractorUri?>) -> Unit,
        subtitleCallback: (SubtitleData) -> Unit,
        offset: Int,
        isCasting: Boolean,
    ): Boolean {
        val ctx = context
        val episode = videos.getOrNull(offset)
        if (!isCasting && !clearCache && ctx != null && episode != null &&
            PreferenceManager.getDefaultSharedPreferences(ctx).getBoolean("prefer_local_downloads", true)) {
            val local = withContext(Dispatchers.IO) {
                try {
                    val info = VideoDownloadManager.getDownloadFileInfo(ctx, episode.id) ?: return@withContext null
                    val completed = ctx.getKey<Long>(VideoDownloadManager.KEY_COMPLETED_DOWNLOAD_BYTES, episode.id.toString())
                    val busy = DownloadQueueService.downloadInstances.value.any { it.downloadQueueWrapper.id == episode.id }
                    if (!LocalDownloadPolicy.isComplete(info.fileLength, info.totalBytes, completed, busy)) return@withContext null
                    ctx.contentResolver.openAssetFileDescriptor(info.path, "r")?.use { } ?: return@withContext null
                    val meta = ctx.getKey<DownloadObjects.DownloadedFileInfo>(VideoDownloadManager.KEY_DOWNLOAD_INFO, episode.id.toString())
                        ?: return@withContext null
                    ExtractorUri(uri = info.path, name = episode.name ?: remote.page?.name.orEmpty(),
                        id = episode.id, parentId = episode.parentId, season = episode.season, episode = episode.episode,
                        headerName = remote.page?.name, tvType = remote.page?.type,
                        basePath = meta.basePath, relativePath = meta.relativePath, displayName = meta.displayName)
                } catch (_: IOException) { null } catch (_: SecurityException) { null }
            }
            if (local != null) return DownloadFileGenerator(listOf(local)).generateLinks(
                false, sourceTypes, callback, subtitleCallback, 0, false)
        }
        return remote.generateLinks(clearCache, sourceTypes, callback, subtitleCallback, offset, isCasting)
    }
}
