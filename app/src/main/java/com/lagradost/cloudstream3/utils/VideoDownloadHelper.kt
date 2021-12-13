package com.lagradost.cloudstream3.utils

import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.ui.download.EasyDownloadButton

object VideoDownloadHelper {
    data class DownloadEpisodeCached(
        val name: String?,
        val poster: String?,
        val episode: Int,
        val season: Int?,
        override val id: Int,
        val parentId: Int,
        val rating: Int?,
        val description: String?,
        val cacheTime: Long,
    ) : EasyDownloadButton.IMinimumData

    data class DownloadHeaderCached(
        val apiName: String,
        val url: String,
        val type: TvType,
        val name: String,
        val poster: String?,
        val id: Int,
        val cacheTime: Long,
    )

    data class ResumeWatching(
        val parentId: Int,
        val episodeId: Int,
        val episode: Int?,
        val season: Int?,
        val updateTime : Long,
        val isFromDownload: Boolean,
    )
}