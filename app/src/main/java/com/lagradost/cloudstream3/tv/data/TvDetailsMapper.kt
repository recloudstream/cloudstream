package com.lagradost.cloudstream3.tv.data

import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.EpisodeResponse
import com.lagradost.cloudstream3.LiveStreamLoadResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.TorrentLoadResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.tv.model.TvDetailsContent

/**
 * Maps domain [LoadResponse] → immutable TV Details models.
 * Separate from [TvMediaMapper] (SearchResponse / Home). Does not mutate LoadResponse.
 *
 * Variants handled (all concrete LoadResponse types in MainAPI.kt):
 * - [MovieLoadResponse]
 * - [TvSeriesLoadResponse]
 * - [AnimeLoadResponse]
 * - [LiveStreamLoadResponse]
 * - [TorrentLoadResponse]
 * - other / unknown LoadResponse implementors → variantLabel "Other"
 */
object TvDetailsMapper {

    fun toDetails(response: LoadResponse): TvDetailsContent {
        val poster = response.posterUrl?.takeIf { it.isNotBlank() }
        val backdrop = response.backgroundPosterUrl?.takeIf { it.isNotBlank() } ?: poster
        val rating = runCatching {
            response.score?.toStringNull(minScore = 0.1, maxScore = 10)
        }.getOrNull()
        val runtime = response.duration?.takeIf { it > 0 }?.let { minutes ->
            val h = minutes / 60
            val m = minutes % 60
            when {
                h > 0 && m > 0 -> "${h}h ${m}m"
                h > 0 -> "${h}h"
                else -> "${m}m"
            }
        }
        val genres = response.tags.orEmpty().map { it.trim() }.filter { it.isNotEmpty() }
        val actors = response.actors.orEmpty().mapNotNull { actorData ->
            actorData.actor.name.takeIf { it.isNotBlank() }
        }
        val showStatus = (response as? EpisodeResponse)?.showStatus?.name
        val episodeCount = episodeCountOf(response)

        return TvDetailsContent(
            title = response.name.ifBlank { "Untitled" },
            posterUrl = poster,
            backdropUrl = backdrop,
            year = response.year,
            rating = rating,
            runtime = runtime,
            genres = genres,
            synopsis = response.plot.orEmpty().trim(),
            typeLabel = response.type.name,
            contentRating = response.contentRating?.takeIf { it.isNotBlank() },
            showStatus = showStatus,
            comingSoon = response.comingSoon,
            episodeCount = episodeCount,
            actors = actors,
            apiName = response.apiName,
            url = response.url,
            variantLabel = variantLabelOf(response),
            posterHeaders = response.posterHeaders?.toMap(),
        )
    }

    private fun variantLabelOf(response: LoadResponse): String = when (response) {
        is MovieLoadResponse -> "Movie"
        is TvSeriesLoadResponse -> "TvSeries"
        is AnimeLoadResponse -> "Anime"
        is LiveStreamLoadResponse -> "LiveStream"
        is TorrentLoadResponse -> "Torrent"
        else -> "Other"
    }

    private fun episodeCountOf(response: LoadResponse): Int? = when (response) {
        is TvSeriesLoadResponse -> response.episodes.size.takeIf { it > 0 }
        is AnimeLoadResponse -> {
            val total = response.episodes.values.sumOf { it.size }
            total.takeIf { it > 0 }
        }
        else -> null
    }
}
