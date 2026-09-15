package com.lagradost.cloudstream3.tv.model

/**
 * Compact immutable content identity for Details navigation.
 * Mirrors ResultFragment bundles: url + apiName (+ display title).
 * Never put mutable SearchResponse / LoadResponse in Compose nav state.
 */
data class TvContentRef(
    val url: String,
    val apiName: String,
    val title: String = "",
) {
    init {
        require(url.isNotBlank()) { "TvContentRef.url must be non-blank" }
        require(apiName.isNotBlank()) { "TvContentRef.apiName must be non-blank" }
    }

    companion object {
        /**
         * Build a loadable ref from a Home card. Returns null for mock/demo items
         * or when url/apiName are missing — callers must never invent fake IDs.
         */
        fun fromMediaItem(item: TvMediaItem): TvContentRef? {
            if (item.isMock) return null
            val url = item.url?.takeIf { it.isNotBlank() } ?: return null
            val apiName = item.apiName?.takeIf { it.isNotBlank() } ?: return null
            return TvContentRef(
                url = url,
                apiName = apiName,
                title = item.title,
            )
        }
    }
}

/** Successful details payload inside [TvDetailsUiState.Content]. Fields only from LoadResponse. */
data class TvDetailsContent(
    val title: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val year: Int?,
    val rating: String?,
    val runtime: String?,
    val genres: List<String>,
    val synopsis: String,
    val typeLabel: String?,
    val contentRating: String?,
    val showStatus: String?,
    val comingSoon: Boolean,
    val episodeCount: Int?,
    val actors: List<String>,
    val apiName: String,
    val url: String,
    /** Concrete LoadResponse kind: Movie, TvSeries, Anime, LiveStream, Torrent, Other. */
    val variantLabel: String,
    val posterHeaders: Map<String, String>?,
    /**
     * Season/episode tree for TvSeries / Anime (empty for Movie / Live / Torrent).
     * Anime: one [TvDubGroup] per DubStatus with episodes; Series: single None group.
     */
    val dubGroups: List<TvDubGroup> = emptyList(),
    /** Precomputed defaults — see [TvEpisodeDefaults]. */
    val defaultDubStatusId: Int? = null,
    val defaultSeasonIndex: Int? = null,
    val defaultEpisodeId: Int? = null,
) {
    val hasEpisodeSelector: Boolean
        get() = variantLabel == "TvSeries" || variantLabel == "Anime"

    fun seasonsForDub(dubStatusId: Int?): List<TvSeason> {
        if (dubGroups.isEmpty()) return emptyList()
        val match = dubGroups.firstOrNull { it.dubStatusId == dubStatusId }
        return match?.seasons ?: dubGroups.first().seasons
    }

    fun episodeById(episodeId: Int?): TvEpisode? {
        if (episodeId == null) return null
        return dubGroups.asSequence()
            .flatMap { it.seasons.asSequence() }
            .flatMap { it.episodes.asSequence() }
            .firstOrNull { it.id == episodeId }
    }
}

sealed interface TvDetailsUiState {
    data class Loading(
        val titleHint: String? = null,
    ) : TvDetailsUiState

    data class Content(
        val details: TvDetailsContent,
        val selectedDubStatusId: Int? = details.defaultDubStatusId,
        val selectedSeasonIndex: Int? = details.defaultSeasonIndex,
        val selectedEpisodeId: Int? = details.defaultEpisodeId,
    ) : TvDetailsUiState {
        val visibleSeasons: List<TvSeason>
            get() = details.seasonsForDub(selectedDubStatusId)

        val selectedSeason: TvSeason?
            get() = visibleSeasons.firstOrNull { it.seasonIndex == selectedSeasonIndex }
                ?: visibleSeasons.firstOrNull()

        val visibleEpisodes: List<TvEpisode>
            get() = selectedSeason?.episodes.orEmpty()

        val selectedEpisode: TvEpisode?
            get() = visibleEpisodes.firstOrNull { it.id == selectedEpisodeId }
                ?: details.episodeById(selectedEpisodeId)
                ?: visibleEpisodes.firstOrNull { it.isPlayable }
                ?: visibleEpisodes.firstOrNull()

        val showDubSelector: Boolean
            get() = details.variantLabel == "Anime" && details.dubGroups.size > 1
    }

    data class Error(
        val message: String,
        val titleHint: String? = null,
    ) : TvDetailsUiState
}

sealed interface TvDetailsAction {
    data object Retry : TvDetailsAction
    data object Back : TvDetailsAction
    /** Movies — Activity-level playback (Phase 5 path). */
    data object WatchNow : TvDetailsAction
    /** Series / Anime — play [TvDetailsUiState.Content.selectedEpisode]. */
    data object PlaySelectedEpisode : TvDetailsAction
    data class SelectDubStatus(val dubStatusId: Int) : TvDetailsAction
    data class SelectSeason(val seasonIndex: Int) : TvDetailsAction
    data class SelectEpisode(val episodeId: Int) : TvDetailsAction
}
