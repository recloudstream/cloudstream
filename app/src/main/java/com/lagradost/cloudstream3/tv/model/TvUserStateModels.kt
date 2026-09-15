package com.lagradost.cloudstream3.tv.model

/**
 * Immutable TV models for Continue Watching + Watchlist/Library (Phase 8).
 * Only fields present on real DataStore / header-cache sources — no invented metadata.
 */

/** One Continue Watching row from read-only resume + header cache (+ optional PosDur). */
data class TvContinueWatchingItem(
    val id: String,
    val title: String,
    val url: String,
    val apiName: String,
    val posterUrl: String? = null,
    val typeLabel: String? = null,
    /** Null when PosDur missing or duration <= 0 — never invent progress. */
    val progressFraction: Float? = null,
    val episode: Int? = null,
    val season: Int? = null,
    val parentId: Int? = null,
    val episodeId: Int? = null,
    val updateTimeMs: Long = 0L,
) {
    fun toContentRef(): TvContentRef = TvContentRef(
        url = url,
        apiName = apiName,
        title = title,
        resumeHint = TvContinueWatchingClassifier.resumeHintOf(this),
    )

    fun toMediaItem(): TvMediaItem {
        val classification = TvContinueWatchingClassifier.classify(this)
        val availability = TvAvailabilityClassifier.fromCwClassification(classification)
        val epSubtitle = buildList {
            when {
                season != null && episode != null -> add("S$season E$episode")
                episode != null -> add("E$episode")
                else -> typeLabel?.let { add(it) }
            }
            when (classification.clazz) {
                // Valid resume: poster/title/ep/progress + Resume label (progress on card bar).
                TvCwClass.DirectPlayable -> add("Resume")
                TvCwClass.PlayableAfterDetails -> {
                    if (season != null || episode != null || episodeId != null) add("Continue")
                    else add("Details")
                }
                // Stale / unsafe: explicit availability label — never fake play.
                TvCwClass.NotSafelyPlayable -> add(availability.kind.label)
            }
            progressFraction?.takeIf { classification.clazz != TvCwClass.NotSafelyPlayable }?.let {
                add("${(it * 100).toInt()}%")
            }
        }.joinToString(" · ")
        return TvMediaItem(
            id = id,
            title = title,
            subtitle = epSubtitle,
            posterUrl = posterUrl,
            backdropUrl = posterUrl,
            // Hide fake progress on unavailable cards.
            progressFraction = progressFraction.takeIf {
                classification.clazz != TvCwClass.NotSafelyPlayable
            },
            apiName = apiName,
            url = url,
            typeLabel = typeLabel,
            isMock = false,
            resumeHint = TvContinueWatchingClassifier.resumeHintOf(this),
            availabilityKind = availability.kind,
        )
    }
}

/** One Library / Watchlist card from bookmarks or favorites (Local list sources). */
data class TvWatchlistItem(
    val id: String,
    val title: String,
    val url: String,
    val apiName: String,
    val posterUrl: String? = null,
    val year: Int? = null,
    val typeLabel: String? = null,
    val watchStatusLabel: String? = null,
    val latestUpdatedTime: Long = 0L,
    val posterHeaders: Map<String, String>? = null,
) {
    fun toContentRef(): TvContentRef = TvContentRef(
        url = url,
        apiName = apiName,
        title = title,
    )

    fun toMediaItem(): TvMediaItem {
        val subtitle = buildList {
            watchStatusLabel?.let { add(it) }
            typeLabel?.let { add(it) }
            year?.let { add(it.toString()) }
        }.joinToString(" · ")
        return TvMediaItem(
            id = id,
            title = title,
            subtitle = subtitle,
            posterUrl = posterUrl,
            backdropUrl = posterUrl,
            year = year,
            typeLabel = typeLabel,
            apiName = apiName,
            url = url,
            posterHeaders = posterHeaders,
            isMock = false,
        )
    }
}

data class TvWatchlistSection(
    val id: String,
    val title: String,
    val items: List<TvWatchlistItem>,
)

data class TvWatchlistCatalog(
    val sections: List<TvWatchlistSection>,
    /** Local CloudStream profile key index as string (DataStoreHelper.currentAccount). */
    val accountKey: String,
    val accountName: String? = null,
)

sealed interface TvWatchlistUiState {
    data object Loading : TvWatchlistUiState

    data class Content(
        val catalog: TvWatchlistCatalog,
    ) : TvWatchlistUiState

    data class Empty(
        val message: String = "No titles in Library yet. Mark watch status or favorites on phone/tablet.",
        val accountName: String? = null,
    ) : TvWatchlistUiState

    data class Error(
        val message: String,
    ) : TvWatchlistUiState
}

sealed interface TvWatchlistAction {
    data object Retry : TvWatchlistAction
    data object Refresh : TvWatchlistAction
}
