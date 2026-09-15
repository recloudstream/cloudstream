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
)

sealed interface TvDetailsUiState {
    data class Loading(
        val titleHint: String? = null,
    ) : TvDetailsUiState

    data class Content(
        val details: TvDetailsContent,
    ) : TvDetailsUiState

    data class Error(
        val message: String,
        val titleHint: String? = null,
    ) : TvDetailsUiState
}

sealed interface TvDetailsAction {
    data object Retry : TvDetailsAction
    data object Back : TvDetailsAction
    /** Stub only — Phase 4 must not start a player. */
    data object WatchNow : TvDetailsAction
}
