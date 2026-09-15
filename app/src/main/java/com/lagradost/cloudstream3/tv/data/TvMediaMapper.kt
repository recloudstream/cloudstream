package com.lagradost.cloudstream3.tv.data

import com.lagradost.cloudstream3.AnimeSearchResponse
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.MovieSearchResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvSeriesSearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.tv.model.TvContentRail
import com.lagradost.cloudstream3.tv.model.TvMediaItem

/**
 * Null-safe mapping from domain search DTOs → immutable TV models.
 * Does not mutate [SearchResponse] / [HomePageList].
 */
object TvMediaMapper {

    fun toMediaItem(response: SearchResponse): TvMediaItem {
        val poster = response.posterUrl?.takeIf { it.isNotBlank() }
        val typeLabel = response.type?.name
        val year = yearOf(response)
        val rating = runCatching {
            response.score?.toStringNull(minScore = 0.1, maxScore = 10)
        }.getOrNull()
        val subtitle = buildList {
            typeLabel?.let { add(it) }
            year?.let { add(it.toString()) }
            response.quality?.name?.let { add(it) }
        }.joinToString(" · ")

        return TvMediaItem(
            id = response.id?.toString()
                ?: "${response.apiName}:${response.url}".ifBlank { response.name },
            title = response.name.ifBlank { "Untitled" },
            subtitle = subtitle,
            posterUrl = poster,
            // SearchResponse has no backdrop field — poster is the legitimate fallback.
            backdropUrl = poster,
            year = year,
            rating = rating,
            typeLabel = typeLabel,
            apiName = response.apiName.takeIf { it.isNotBlank() },
            url = response.url.takeIf { it.isNotBlank() },
            posterHeaders = response.posterHeaders?.toMap(),
            isMock = false,
        )
    }

    fun toRail(
        id: String,
        title: String,
        list: HomePageList,
        limit: Int = 24,
    ): TvContentRail {
        val items = list.list
            .asSequence()
            .map { toMediaItem(it) }
            .distinctBy { it.id }
            .take(limit)
            .toList()
        return TvContentRail(
            id = id,
            title = title,
            items = items,
            isMock = false,
        )
    }

    fun toRailFromItems(
        id: String,
        title: String,
        items: List<SearchResponse>,
        limit: Int = 24,
    ): TvContentRail {
        return TvContentRail(
            id = id,
            title = title,
            items = items.asSequence()
                .map { toMediaItem(it) }
                .distinctBy { it.id }
                .take(limit)
                .toList(),
            isMock = false,
        )
    }

    private fun yearOf(response: SearchResponse): Int? = when (response) {
        is MovieSearchResponse -> response.year
        is TvSeriesSearchResponse -> response.year
        is AnimeSearchResponse -> response.year
        else -> null
    }

    fun isAnimeType(type: TvType?): Boolean = when (type) {
        TvType.Anime, TvType.OVA, TvType.AnimeMovie, TvType.Cartoon -> true
        else -> false
    }

    fun isMovieRailType(type: TvType?): Boolean = when (type) {
        TvType.Movie, TvType.Documentary, TvType.Torrent, TvType.Video -> true
        else -> false
    }
}
