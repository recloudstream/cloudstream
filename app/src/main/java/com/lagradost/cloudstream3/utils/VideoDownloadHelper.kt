package com.lagradost.cloudstream3.utils

import com.lagradost.cloudstream3.TvType

object VideoDownloadHelper {
    data class DownloadEpisodeCached(
        val name: String?,
        val poster: String?,
        val episode: Int,
        val season: Int?,
        val id: Int,
        val parentId: Int,
        val rating: Int?,
        val descript: String?,
    )

    data class DownloadHeaderCached(
        val apiName: String,
        val source: String,
        val type : TvType,
        val name: String,
        val poster: String?,
        val id: Int,
    )
}