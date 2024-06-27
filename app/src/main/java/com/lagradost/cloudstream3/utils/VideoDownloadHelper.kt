package com.lagradost.cloudstream3.utils

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.TvType
object VideoDownloadHelper {
    abstract class DownloadCached(
        @JsonProperty("id") open val id: Int,
    )

    data class DownloadEpisodeCached(
        @JsonProperty("name") val name: String?,
        @JsonProperty("poster") val poster: String?,
        @JsonProperty("episode") val episode: Int,
        @JsonProperty("season") val season: Int?,
        @JsonProperty("parentId") val parentId: Int,
        @JsonProperty("rating") val rating: Int?,
        @JsonProperty("description") val description: String?,
        @JsonProperty("cacheTime") val cacheTime: Long,
        override val id: Int,
    ): DownloadCached(id)

    data class DownloadHeaderCached(
        @JsonProperty("apiName") val apiName: String,
        @JsonProperty("url") val url: String,
        @JsonProperty("type") val type: TvType,
        @JsonProperty("name") val name: String,
        @JsonProperty("poster") val poster: String?,
        @JsonProperty("cacheTime") val cacheTime: Long,
        override val id: Int,
    ): DownloadCached(id)

    data class ResumeWatching(
        @JsonProperty("parentId") val parentId: Int,
        @JsonProperty("episodeId") val episodeId: Int?,
        @JsonProperty("episode") val episode: Int?,
        @JsonProperty("season") val season: Int?,
        @JsonProperty("updateTime") val updateTime: Long,
        @JsonProperty("isFromDownload") val isFromDownload: Boolean,
    )
}