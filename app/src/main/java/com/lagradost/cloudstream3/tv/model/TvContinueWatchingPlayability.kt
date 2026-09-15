package com.lagradost.cloudstream3.tv.model

/**
 * Phase 9 Continue Watching playability — classified from real Phase 8 fields only.
 *
 * Exact resume fields available on [TvContinueWatchingItem]:
 * id, title, url, apiName, posterUrl, typeLabel, progressFraction?,
 * episode?, season?, parentId?, episodeId?, updateTimeMs
 *
 * Missing for series direct play: Episode.data (playable payload) — never invent it.
 * Progress / PosDur is display-only; the existing player owns seek.
 */
enum class TvCwClass {
    /** A — Movie with url + apiName + title; build TvPlaybackRequest → bridge. */
    DirectPlayable,

    /** B — Identity enough for Details; series may resolve to fromEpisode after load. */
    PlayableAfterDetails,

    /** C — Insufficient or unsupported; clear unavailable, never play. */
    NotSafelyPlayable,
}

data class TvCwClassification(
    val clazz: TvCwClass,
    /** Human-readable reason for C, or brief note for A/B. */
    val reason: String,
) {
    val isDirect: Boolean get() = clazz == TvCwClass.DirectPlayable
    val isDetails: Boolean get() = clazz == TvCwClass.PlayableAfterDetails
    val isUnavailable: Boolean get() = clazz == TvCwClass.NotSafelyPlayable
}

/**
 * Compact resume hints carried on CW [TvMediaItem] / [TvContentRef] for Details restore
 * and series resolve — never invent episode URLs.
 */
data class TvResumeHint(
    val season: Int? = null,
    val episode: Int? = null,
    val episodeId: Int? = null,
    val parentId: Int? = null,
    val typeLabel: String? = null,
) {
    val hasExactEpisode: Boolean
        get() = episode != null || episodeId != null
}

object TvContinueWatchingClassifier {

    /** Types that Compose TV can play as Movie via existing bridge (variantLabel = Movie). */
    private val directMovieTypes = setOf("Movie")

    /** Series/anime families that use episode selector + fromEpisode after Details load. */
    private val seriesOrAnimeTypes = setOf(
        "TvSeries",
        "Anime",
        "Cartoon",
        "AsianDrama",
        "OVA",
    )

    private val blockedTypes = setOf("Live", "Torrent")

    fun classify(item: TvContinueWatchingItem): TvCwClassification {
        if (item.url.isBlank() || item.apiName.isBlank() || item.title.isBlank()) {
            return TvCwClassification(
                TvCwClass.NotSafelyPlayable,
                "Missing title, provider, or URL — cannot resume safely.",
            )
        }
        val type = item.typeLabel
        return when {
            type != null && type in blockedTypes -> TvCwClassification(
                TvCwClass.NotSafelyPlayable,
                "$type playback is not supported in Compose TV yet.",
            )
            type != null && type in directMovieTypes -> TvCwClassification(
                TvCwClass.DirectPlayable,
                "Movie — Resume → TvPlaybackRequest → TvPlaybackBridge (player owns seek).",
            )
            type != null && type in seriesOrAnimeTypes -> TvCwClassification(
                TvCwClass.PlayableAfterDetails,
                if (item.season != null && item.episode != null) {
                    "Series/Anime S${item.season} E${item.episode} — resolve Episode.data via Details load, then fromEpisode."
                } else if (item.episodeId != null) {
                    "Series/Anime episodeId=${item.episodeId} — resolve via Details load, then fromEpisode."
                } else {
                    "Series/Anime — open Details (no exact season+episode to resume)."
                },
            )
            // AnimeMovie / Documentary / Video / etc.: identity enough for Details — do not guess MovieLoadResponse.
            type != null -> TvCwClassification(
                TvCwClass.PlayableAfterDetails,
                "Type $type — open shared Details (do not guess playback variant from CW alone).",
            )
            // typeLabel missing but identity present → Details only
            else -> TvCwClassification(
                TvCwClass.PlayableAfterDetails,
                "Unknown type — open Details; never invent playback variant.",
            )
        }
    }

    fun classifyMediaItem(item: TvMediaItem): TvCwClassification {
        if (item.isMock) {
            return TvCwClassification(
                TvCwClass.NotSafelyPlayable,
                "Demo item — never becomes a real TvPlaybackRequest.",
            )
        }
        val url = item.url?.takeIf { it.isNotBlank() }
        val api = item.apiName?.takeIf { it.isNotBlank() }
        if (url == null || api == null || item.title.isBlank()) {
            return TvCwClassification(
                TvCwClass.NotSafelyPlayable,
                "Missing title, provider, or URL — cannot resume safely.",
            )
        }
        // Rebuild a minimal CW item for the same classifier rules.
        return classify(
            TvContinueWatchingItem(
                id = item.id,
                title = item.title,
                url = url,
                apiName = api,
                posterUrl = item.posterUrl,
                typeLabel = item.typeLabel,
                progressFraction = item.progressFraction,
                episode = item.resumeHint?.episode,
                season = item.resumeHint?.season,
                parentId = item.resumeHint?.parentId,
                episodeId = item.resumeHint?.episodeId,
            ),
        )
    }

    fun moviePlaybackRequest(item: TvMediaItem): TvPlaybackRequest? {
        if (item.isMock) return null
        val classification = classifyMediaItem(item)
        if (!classification.isDirect) return null
        val url = item.url?.takeIf { it.isNotBlank() } ?: return null
        val api = item.apiName?.takeIf { it.isNotBlank() } ?: return null
        return TvPlaybackRequest(
            url = url,
            apiName = api,
            title = item.title,
            variantLabel = "Movie",
            comingSoon = false,
            isMock = false,
        )
    }

    fun resumeHintOf(item: TvContinueWatchingItem): TvResumeHint = TvResumeHint(
        season = item.season,
        episode = item.episode,
        episodeId = item.episodeId,
        parentId = item.parentId,
        typeLabel = item.typeLabel,
    )
}
