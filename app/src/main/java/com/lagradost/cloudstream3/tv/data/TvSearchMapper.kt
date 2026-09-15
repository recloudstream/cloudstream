package com.lagradost.cloudstream3.tv.data

import com.lagradost.cloudstream3.AnimeSearchResponse
import com.lagradost.cloudstream3.MovieSearchResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvSeriesSearchResponse
import com.lagradost.cloudstream3.tv.model.TvContentRef
import com.lagradost.cloudstream3.tv.model.TvSearchResult

/**
 * Null-safe mapping from domain [SearchResponse] → immutable TV search models.
 * Retains apiName + url for [TvContentRef] so Search → Details converges with Home.
 * Does not mutate SearchResponse.
 */
object TvSearchMapper {

    /**
     * Returns null when url/apiName are missing — never invent fake IDs for Details.
     */
    fun toSearchResult(response: SearchResponse): TvSearchResult? {
        val url = response.url.takeIf { it.isNotBlank() } ?: return null
        val apiName = response.apiName.takeIf { it.isNotBlank() } ?: return null
        val poster = response.posterUrl?.takeIf { it.isNotBlank() }
        val typeLabel = response.type?.name
        val year = yearOf(response)
        val rating = runCatching {
            response.score?.toStringNull(minScore = 0.1, maxScore = 10)
        }.getOrNull()
        val subtitle = buildList {
            apiName.let { add(it) }
            typeLabel?.let { add(it) }
            year?.let { add(it.toString()) }
            response.quality?.name?.let { add(it) }
        }.joinToString(" · ")

        return TvSearchResult(
            id = response.id?.toString() ?: "$apiName:$url",
            title = response.name.ifBlank { "Untitled" },
            subtitle = subtitle,
            posterUrl = poster,
            year = year,
            rating = rating,
            typeLabel = typeLabel,
            providerName = apiName,
            posterHeaders = response.posterHeaders?.toMap(),
            contentRef = TvContentRef(
                url = url,
                apiName = apiName,
                title = response.name.ifBlank { "Untitled" },
            ),
        )
    }

    fun toSearchResults(items: List<SearchResponse>): List<TvSearchResult> =
        items.mapNotNull { toSearchResult(it) }.distinctBy { it.id }

    private fun yearOf(response: SearchResponse): Int? = when (response) {
        is MovieSearchResponse -> response.year
        is TvSeriesSearchResponse -> response.year
        is AnimeSearchResponse -> response.year
        else -> null
    }
}
