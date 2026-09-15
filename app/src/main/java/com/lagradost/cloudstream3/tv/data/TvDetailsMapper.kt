package com.lagradost.cloudstream3.tv.data

import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.EpisodeResponse
import com.lagradost.cloudstream3.LiveStreamLoadResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.SeasonData
import com.lagradost.cloudstream3.TorrentLoadResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.tv.model.TvDetailsContent
import com.lagradost.cloudstream3.tv.model.TvDubGroup
import com.lagradost.cloudstream3.tv.model.TvEpisode
import com.lagradost.cloudstream3.tv.model.TvEpisodeDefaults
import com.lagradost.cloudstream3.tv.model.TvSeason
import com.lagradost.cloudstream3.ui.result.getId

/**
 * Maps domain [LoadResponse] → immutable TV Details models.
 * Separate from [TvMediaMapper] (SearchResponse / Home). Does not mutate LoadResponse.
 *
 * Variants handled (all concrete LoadResponse types in MainAPI.kt):
 * - [MovieLoadResponse]
 * - [TvSeriesLoadResponse] — episodes from List&lt;Episode&gt;, seasons via Episode.season + SeasonData
 * - [AnimeLoadResponse] — episodes from Map&lt;DubStatus, List&lt;Episode&gt;&gt;
 * - [LiveStreamLoadResponse]
 * - [TorrentLoadResponse]
 * - other / unknown LoadResponse implementors → variantLabel "Other"
 *
 * Episode id / indexing formulas mirror ResultViewModel2.postEpisodes (do not invent).
 *
 * Domain note: Episode.episode / Episode.season are Int? only — there are no non-int
 * episode numbers in LoadResponse; null episode → (listIndex + 1), null season → bucket 0.
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
        val dubGroups = mapDubGroups(response)
        val (defaultDub, defaultSeason, defaultEpisode) = TvEpisodeDefaults.defaultsFor(dubGroups)

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
            dubGroups = dubGroups,
            defaultDubStatusId = defaultDub,
            defaultSeasonIndex = defaultSeason,
            defaultEpisodeId = defaultEpisode,
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

    private fun mapDubGroups(response: LoadResponse): List<TvDubGroup> = when (response) {
        is TvSeriesLoadResponse -> {
            val mainId = response.getId()
            // ResultViewModel2 sorts TV episodes before assigning index / fallback episode nums.
            val ordered = response.episodes
                .withIndex()
                .sortedBy { (it.value.season?.times(10_000) ?: 0) + (it.value.episode ?: 0) }
                .map { it.value }
            val seasons = groupEpisodesToSeasons(
                episodes = ordered,
                seasonNames = response.seasonNames,
                dubStatus = DubStatus.None,
                totalEpisodeIndex = { epNum, season ->
                    season?.let { response.getTotalEpisodeIndex(epNum, it) }
                },
                idFor = { ep, episodeNumber, _ ->
                    mainId + (ep.season?.times(100_000) ?: 0) + episodeNumber + 1
                },
            )
            if (seasons.isEmpty()) emptyList()
            else listOf(
                TvDubGroup(
                    dubStatusId = DubStatus.None.id,
                    label = "",
                    seasons = seasons,
                ),
            )
        }
        is AnimeLoadResponse -> {
            val mainId = response.getId()
            response.episodes.entries
                .filter { it.value.isNotEmpty() }
                .sortedBy { it.key.id }
                .map { (status, eps) ->
                    // Anime: keep provider list order (ResultViewModel2 uses withIndex on raw list).
                    val seasons = groupEpisodesToSeasons(
                        episodes = eps,
                        seasonNames = response.seasonNames,
                        dubStatus = status,
                        totalEpisodeIndex = { epNum, season ->
                            season?.let { response.getTotalEpisodeIndex(epNum, it) }
                        },
                        idFor = { ep, episodeNumber, _ ->
                            mainId + episodeNumber + status.id * 1_000_000 +
                                (ep.season?.times(10_000) ?: 0)
                        },
                    )
                    TvDubGroup(
                        dubStatusId = status.id,
                        label = dubLabel(status),
                        seasons = seasons,
                    )
                }
                .filter { it.seasons.isNotEmpty() }
        }
        else -> emptyList()
    }

    private fun dubLabel(status: DubStatus): String = when (status) {
        DubStatus.Dubbed -> "Dub"
        DubStatus.Subbed -> "Sub"
        DubStatus.None -> ""
    }

    /**
     * Groups domain [Episode] list into [TvSeason] buckets.
     * Missing Episode.season → seasonIndex 0 (same as ResultViewModel2).
     * Episode.episode null → index + 1 (domain Int? only).
     */
    private fun groupEpisodesToSeasons(
        episodes: List<Episode>,
        seasonNames: List<SeasonData>?,
        dubStatus: DubStatus,
        totalEpisodeIndex: (episodeNumber: Int, season: Int?) -> Int?,
        idFor: (ep: Episode, episodeNumber: Int, index: Int) -> Int,
    ): List<TvSeason> {
        if (episodes.isEmpty()) return emptyList()

        val existingIds = HashSet<Int>()
        val bySeason = linkedMapOf<Int, MutableList<TvEpisode>>()

        for ((index, ep) in episodes.withIndex()) {
            val episodeNumber = ep.episode ?: (index + 1)
            val id = idFor(ep, episodeNumber, index)
            if (!existingIds.add(id)) continue

            val seasonKey = ep.season ?: 0
            val seasonData = seasonNames.getSeason(ep.season)
            val displaySeason = if (seasonData != null) seasonData.displaySeason else ep.season
            val scoreLabel = runCatching {
                ep.score?.toStringNull(minScore = 0.1, maxScore = 10)
            }.getOrNull()

            val tvEp = TvEpisode(
                id = id,
                index = index,
                episodeNumber = episodeNumber,
                name = filterEpisodeName(ep.name),
                description = ep.description?.takeIf { it.isNotBlank() },
                posterUrl = ep.posterUrl?.takeIf { it.isNotBlank() },
                seasonIndex = ep.season,
                displaySeason = displaySeason,
                data = ep.data,
                airDate = ep.date,
                runTime = ep.runTime,
                scoreLabel = scoreLabel,
                dubStatusId = dubStatus.id,
                totalEpisodeIndex = totalEpisodeIndex(episodeNumber, ep.season),
                isPlayable = ep.data.isNotBlank(),
            )
            bySeason.getOrPut(seasonKey) { mutableListOf() }.add(tvEp)
        }

        return bySeason.entries
            .sortedBy { it.key }
            .map { (seasonKey, eps) ->
                val matched = seasonNames.getSeason(seasonKey)
                TvSeason(
                    seasonIndex = seasonKey,
                    displaySeason = matched?.displaySeason ?: seasonKey.takeIf { it != 0 },
                    name = matched?.name,
                    label = seasonLabel(matched, seasonKey),
                    episodes = eps.sortedWith(
                        compareBy({ it.episodeNumber }, { it.index }),
                    ),
                )
            }
    }

    private fun List<SeasonData>?.getSeason(season: Int?): SeasonData? {
        if (season == null) return null
        return this?.firstOrNull { it.season == season }
    }

    /** Mirrors ResultViewModel2.seasonToTxt without UiText / resources. */
    private fun seasonLabel(seasonData: SeasonData?, season: Int): String {
        if (season == 0) return "No Season"
        if (seasonData?.name != null && seasonData.displaySeason == null) {
            return seasonData.name!!
        }
        val number = seasonData?.displaySeason ?: season
        val suffix = seasonData?.name?.let { " $it" }.orEmpty()
        return "Season $number$suffix"
    }

    /** Mirrors ResultViewModel2.filterName — strip redundant "Episode N" titles. */
    private fun filterEpisodeName(name: String?): String? {
        if (name == null) return null
        Regex("^[eE]pisode [0-9]*(.*)").find(name)?.groupValues?.get(1)?.let {
            if (it.isEmpty()) return null
        }
        return name
    }
}
