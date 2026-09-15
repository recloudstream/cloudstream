package com.lagradost.cloudstream3.tv.data

import com.lagradost.cloudstream3.CloudStreamApp.Companion.getKey
import com.lagradost.cloudstream3.tv.model.TvContentRail
import com.lagradost.cloudstream3.tv.model.TvContinueWatchingItem
import com.lagradost.cloudstream3.tv.model.TvRailIds
import com.lagradost.cloudstream3.utils.DOWNLOAD_HEADER_CACHE
import com.lagradost.cloudstream3.utils.DOWNLOAD_HEADER_CACHE_BACKUP
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.DataStoreHelper.getAllResumeStateIds
import com.lagradost.cloudstream3.utils.DataStoreHelper.getLastWatched
import com.lagradost.cloudstream3.utils.DataStoreHelper.getViewPos
import com.lagradost.cloudstream3.utils.DataStoreHelper.removeLastWatched
import com.lagradost.cloudstream3.utils.downloader.DownloadObjects

/**
 * Continue Watching adapter (Phase 8/10).
 *
 * Reads are pure; Phase 10 remove uses existing [removeLastWatched] only
 * (no new DataStore keys / hide flags).
 *
 * Why not [com.lagradost.cloudstream3.ui.home.HomeViewModel.getResumeWatching]:
 * that companion can **write** `DOWNLOAD_HEADER_CACHE` when restoring from
 * `DOWNLOAD_HEADER_CACHE_BACKUP` (Phase 3/8 forbid persistence writes for TV).
 *
 * Read-only source APIs (exact):
 * - [DataStoreHelper.getAllResumeStateIds]
 * - [DataStoreHelper.getLastWatched]
 * - [getKey] on [DOWNLOAD_HEADER_CACHE] (and optionally BACKUP — read only, never setKey)
 * - [DataStoreHelper.getViewPos]
 *
 * Items without a header cache entry (primary or backup) are skipped — never invent titles
 * or restore keys.
 */
class TvContinueWatchingRepository {

    fun loadContinueWatching(limit: Int = 24): List<TvContinueWatchingItem> {
        val ids = getAllResumeStateIds() ?: return emptyList()
        return ids.mapNotNull { id -> getLastWatched(id) }
            .sortedByDescending { it.updateTime }
            .mapNotNull { resume -> mapResume(resume) }
            .distinctBy { it.id }
            .take(limit)
    }

    fun loadContinueWatchingRail(limit: Int = 24): TvContentRail? {
        val items = loadContinueWatching(limit)
        if (items.isEmpty()) return null
        return TvContentRail(
            id = TvRailIds.CONTINUE,
            title = "Continue Watching",
            items = items.map { it.toMediaItem() },
            isMock = false,
        )
    }


    /**
     * Phase 10 — remove one CW entry via existing [removeLastWatched].
     * Does not touch PosDur, bookmarks, favorites, or header cache.
     * @return true when parentId was non-null and remove was invoked.
     */
    fun removeContinueWatching(parentId: Int?): Boolean {
        if (parentId == null) return false
        removeLastWatched(parentId)
        return true
    }

    private fun mapResume(
        resume: DownloadObjects.ResumeWatching,
    ): TvContinueWatchingItem? {
        val header = getKey<DownloadObjects.DownloadHeaderCached>(
            DOWNLOAD_HEADER_CACHE,
            resume.parentId.toString(),
        ) ?: getKey<DownloadObjects.DownloadHeaderCached>(
            DOWNLOAD_HEADER_CACHE_BACKUP,
            resume.parentId.toString(),
        ) ?: return null

        val url = header.url.takeIf { it.isNotBlank() } ?: return null
        val apiName = header.apiName.takeIf { it.isNotBlank() } ?: return null
        val title = header.name.takeIf { it.isNotBlank() } ?: return null

        val watchPos = getViewPos(resume.episodeId)
        val progress = watchPos?.takeIf { it.duration > 0 }?.let { pos ->
            (pos.position.toFloat() / pos.duration.toFloat()).coerceIn(0f, 1f)
        }

        return TvContinueWatchingItem(
            id = "cw-${resume.parentId}-${resume.episodeId ?: 0}",
            title = title,
            url = url,
            apiName = apiName,
            posterUrl = header.poster?.takeIf { it.isNotBlank() },
            typeLabel = header.type.name,
            progressFraction = progress,
            episode = resume.episode,
            season = resume.season,
            parentId = resume.parentId,
            episodeId = resume.episodeId,
            updateTimeMs = resume.updateTime,
        )
    }
}
