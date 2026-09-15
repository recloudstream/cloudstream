package com.lagradost.cloudstream3.tv.model

/**
 * Immutable TV-side episode / season models mapped from real [com.lagradost.cloudstream3.Episode]
 * and [com.lagradost.cloudstream3.SeasonData] fields only — never invent domain shape.
 *
 * Domain facts (MainAPI.kt):
 * - Episode: data, name?, season?, episode?, posterUrl?, score?, description?, date?, runTime?
 * - season / episode numbers are Int? only (no string/fractional episode IDs in LoadResponse)
 * - SeasonData: season (Int), name?, displaySeason?
 * - TvSeriesLoadResponse.episodes: List&lt;Episode&gt;
 * - AnimeLoadResponse.episodes: MutableMap&lt;DubStatus, List&lt;Episode&gt;&gt;
 * - season == null → grouped as seasonIndex 0 ("No Season"); season == 0 also "No Season" / specials bucket
 */

/** One DubStatus bucket for anime (or a single None group for TV series). */
data class TvDubGroup(
    /** [com.lagradost.cloudstream3.DubStatus.id] (-1 None, 0 Subbed, 1 Dubbed). */
    val dubStatusId: Int,
    val label: String,
    val seasons: List<TvSeason>,
)

data class TvSeason(
    /**
     * Grouping key = Episode.season ?: 0 (same as ResultViewModel2 EpisodeIndexer.season).
     * 0 = missing / explicit zero → UI label "No Season" (specials bucket).
     */
    val seasonIndex: Int,
    /** SeasonData.displaySeason when present; else Episode.season (may be null). */
    val displaySeason: Int?,
    /** SeasonData.name when present. */
    val name: String?,
    /** Precomputed 10ft label ("Season 1", "No Season", or custom name). */
    val label: String,
    val episodes: List<TvEpisode>,
)

data class TvEpisode(
    /**
     * Stable id matching ResultViewModel2 formulas so existing player / link cache keys align.
     * TvSeries: mainId + (season?.times(100_000) ?: 0) + episodeNumber + 1
     * Anime: mainId + episodeNumber + dubStatusId * 1_000_000 + (season?.times(10_000) ?: 0)
     */
    val id: Int,
    /** Index within the source Episode list for that dub (ResultViewModel2 index). */
    val index: Int,
    /** Episode.episode ?: (index + 1) — domain is Int? only. */
    val episodeNumber: Int,
    val name: String?,
    val description: String?,
    val posterUrl: String?,
    /** Raw Episode.season (null when missing). */
    val seasonIndex: Int?,
    /** Display season for ResultEpisode.season (SeasonData.displaySeason ?: Episode.season). */
    val displaySeason: Int?,
    /** Episode.data — required payload for APIRepository.loadLinks / RepoLinkGenerator. */
    val data: String,
    val airDate: Long?,
    val runTime: Int?,
    val scoreLabel: String?,
    val dubStatusId: Int,
    val totalEpisodeIndex: Int?,
    /** True when [data] is non-blank — only playable episodes launch GeneratorPlayer. */
    val isPlayable: Boolean,
) {
    val titleLine: String
        get() {
            val ep = "E$episodeNumber"
            val n = name?.takeIf { it.isNotBlank() }
            return if (n != null) "$ep · $n" else ep
        }
}

/**
 * Default selection rule (Phase 6 — deterministic, NO resume / DataStore writes):
 * 1. Dub: Subbed if it has episodes, else Dubbed, else None, else first non-empty group.
 * 2. Season: lowest seasonIndex among seasons with seasonIndex != 0; if none, seasonIndex 0.
 * 3. Episode: first episode in that season with isPlayable; else first episode in that season.
 */
object TvEpisodeDefaults {
    fun pickDubStatusId(groups: List<TvDubGroup>): Int? {
        if (groups.isEmpty()) return null
        val sub = groups.firstOrNull { it.dubStatusId == 0 && it.seasons.any { s -> s.episodes.isNotEmpty() } }
        if (sub != null) return sub.dubStatusId
        val dub = groups.firstOrNull { it.dubStatusId == 1 && it.seasons.any { s -> s.episodes.isNotEmpty() } }
        if (dub != null) return dub.dubStatusId
        val none = groups.firstOrNull { it.dubStatusId == -1 && it.seasons.any { s -> s.episodes.isNotEmpty() } }
        if (none != null) return none.dubStatusId
        return groups.firstOrNull { it.seasons.any { s -> s.episodes.isNotEmpty() } }?.dubStatusId
            ?: groups.firstOrNull()?.dubStatusId
    }

    fun pickSeasonIndex(seasons: List<TvSeason>): Int? {
        if (seasons.isEmpty()) return null
        val regular = seasons.filter { it.seasonIndex != 0 && it.episodes.isNotEmpty() }
            .minByOrNull { it.seasonIndex }
        if (regular != null) return regular.seasonIndex
        return seasons.firstOrNull { it.episodes.isNotEmpty() }?.seasonIndex
            ?: seasons.firstOrNull()?.seasonIndex
    }

    fun pickEpisodeId(season: TvSeason?): Int? {
        if (season == null || season.episodes.isEmpty()) return null
        return season.episodes.firstOrNull { it.isPlayable }?.id
            ?: season.episodes.firstOrNull()?.id
    }

    fun defaultsFor(groups: List<TvDubGroup>): Triple<Int?, Int?, Int?> {
        val dubId = pickDubStatusId(groups)
        val seasons = groups.firstOrNull { it.dubStatusId == dubId }?.seasons.orEmpty()
        val seasonIndex = pickSeasonIndex(seasons)
        val season = seasons.firstOrNull { it.seasonIndex == seasonIndex }
        val episodeId = pickEpisodeId(season)
        return Triple(dubId, seasonIndex, episodeId)
    }
}
