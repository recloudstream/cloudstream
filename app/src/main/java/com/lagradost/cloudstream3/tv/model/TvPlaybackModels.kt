package com.lagradost.cloudstream3.tv.model

/**
 * Immutable playback request for Activity-level launch.
 * Min fields only — never put [com.lagradost.cloudstream3.LoadResponse], Activity,
 * or mutable UI state here.
 *
 * [variantLabel] mirrors [TvDetailsContent.variantLabel]
 * (Movie / TvSeries / Anime / LiveStream / Torrent / Other).
 */
data class TvPlaybackRequest(
    val url: String,
    val apiName: String,
    val title: String,
    val variantLabel: String,
    val comingSoon: Boolean = false,
    /** True for Compose demo / mock catalog — bridge must never launch real playback. */
    val isMock: Boolean = false,
) {
    init {
        require(url.isNotBlank()) { "TvPlaybackRequest.url must be non-blank" }
        require(apiName.isNotBlank()) { "TvPlaybackRequest.apiName must be non-blank" }
    }

    val isMovie: Boolean get() = variantLabel == "Movie"

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
    }
}

/** Why Watch Now is not available for this content in Phase 5. */
fun TvDetailsContent.watchNowDisabledReason(): String? = when {
    comingSoon -> "Coming soon — not released yet"
    variantLabel == "Movie" -> null
    variantLabel == "TvSeries" -> "Series episode picker is Phase 6"
    variantLabel == "Anime" -> "Anime episode / dub selection is Phase 6"
    variantLabel == "LiveStream" -> "Live playback wiring deferred (Phase 6)"
    variantLabel == "Torrent" -> "Torrent playback wiring deferred (Phase 6)"
    else -> "Playback for $variantLabel is not supported in Phase 5"
}
