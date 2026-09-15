package com.lagradost.cloudstream3.tv.model

/**
 * Immutable playback request for Activity-level launch.
 * Min fields only — never put [com.lagradost.cloudstream3.LoadResponse], Activity,
 * or mutable UI state here.
 *
 * [variantLabel] mirrors [TvDetailsContent.variantLabel]
 * (Movie / TvSeries / Anime / LiveStream / Torrent / Other).
 *
 * Episode path (Phase 6): optional episode* fields carry the selected [TvEpisode]
 * so [com.lagradost.cloudstream3.tv.playback.TvPlaybackBridge] can build ResultEpisode
 * the same way ResultViewModel2 does for movies — but with episode data.
 */
data class TvPlaybackRequest(
    val url: String,
    val apiName: String,
    val title: String,
    val variantLabel: String,
    val comingSoon: Boolean = false,
    /** True for Compose demo / mock catalog — bridge must never launch real playback. */
    val isMock: Boolean = false,
    // --- Episode path (null for movies) ---
    val episodeData: String? = null,
    val episodeNumber: Int? = null,
    val seasonIndex: Int? = null,
    val displaySeason: Int? = null,
    val episodeName: String? = null,
    val episodePoster: String? = null,
    val episodeDescription: String? = null,
    val episodeId: Int? = null,
    val episodeIndex: Int? = null,
    val parentId: Int? = null,
    val totalEpisodeIndex: Int? = null,
    val airDate: Long? = null,
    val runTime: Int? = null,
    val dubStatusId: Int? = null,
) {
    init {
        require(url.isNotBlank()) { "TvPlaybackRequest.url must be non-blank" }
        require(apiName.isNotBlank()) { "TvPlaybackRequest.apiName must be non-blank" }
    }

    val isMovie: Boolean get() = variantLabel == "Movie"

    val isEpisodePlayback: Boolean
        get() = !episodeData.isNullOrBlank() && episodeId != null && episodeNumber != null

    companion object {
        fun fromDetails(details: TvDetailsContent, isMock: Boolean = false): TvPlaybackRequest =
            TvPlaybackRequest(
                url = details.url,
                apiName = details.apiName,
                title = details.title,
                variantLabel = details.variantLabel,
                comingSoon = details.comingSoon,
                isMock = isMock,
            )

        fun fromEpisode(
            details: TvDetailsContent,
            episode: TvEpisode,
            isMock: Boolean = false,
        ): TvPlaybackRequest =
            TvPlaybackRequest(
                url = details.url,
                apiName = details.apiName,
                title = details.title,
                variantLabel = details.variantLabel,
                comingSoon = details.comingSoon,
                isMock = isMock,
                episodeData = episode.data,
                episodeNumber = episode.episodeNumber,
                seasonIndex = episode.seasonIndex,
                displaySeason = episode.displaySeason,
                episodeName = episode.name,
                episodePoster = episode.posterUrl,
                episodeDescription = episode.description,
                episodeId = episode.id,
                episodeIndex = episode.index,
                parentId = null, // Bridge fills from LoadResponse.getId()
                totalEpisodeIndex = episode.totalEpisodeIndex,
                airDate = episode.airDate,
                runTime = episode.runTime,
                dubStatusId = episode.dubStatusId,
            )
    }
}

/** Why primary playback CTA is unavailable for this content. */
fun TvDetailsContent.watchNowDisabledReason(): String? = when {
    comingSoon -> "Coming soon — not released yet"
    variantLabel == "Movie" -> null
    variantLabel == "TvSeries" || variantLabel == "Anime" -> {
        val anyPlayable = dubGroups.any { g -> g.seasons.any { s -> s.episodes.any { it.isPlayable } } }
        if (anyPlayable) null else "No playable episodes found"
    }
    variantLabel == "LiveStream" -> "Live playback is not supported in Compose TV yet"
    variantLabel == "Torrent" -> "Torrent playback is not supported in Compose TV yet"
    else -> "Playback for $variantLabel is not supported"
}

fun TvDetailsContent.isSeriesOrAnime(): Boolean =
    variantLabel == "TvSeries" || variantLabel == "Anime"
