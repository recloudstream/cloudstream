package com.lagradost.cloudstream3.tv.model

/**
 * Immutable TV presentation models for Compose Home (Phase 3).
 * Mapped from domain [com.lagradost.cloudstream3.SearchResponse] — UI must not hold mutable DTOs.
 */

data class TvMediaItem(
    val id: String,
    val title: String,
    val subtitle: String = "",
    /** Null-safe; Coil shows placeholder when null/blank. */
    val posterUrl: String? = null,
    /** SearchResponse has no backdrop — use poster or mock. */
    val backdropUrl: String? = posterUrl,
    val year: Int? = null,
    val rating: String? = null,
    val runtime: String? = null,
    val genres: List<String> = emptyList(),
    val synopsis: String = "",
    val progressFraction: Float? = null,
    val apiName: String? = null,
    val url: String? = null,
    val typeLabel: String? = null,
    val posterHeaders: Map<String, String>? = null,
    val isMock: Boolean = false,
    /**
     * Phase 9: present only on Continue Watching cards mapped from [TvContinueWatchingItem].
     * Null for Home/Search/Watchlist cards — never invent resume metadata.
     */
    val resumeHint: TvResumeHint? = null,
)

data class TvContentRail(
    val id: String,
    val title: String,
    val items: List<TvMediaItem>,
    /** True when this rail is demo/mock data (never silent). */
    val isMock: Boolean = false,
)

/** Successful catalog payload inside [TvHomeUiState.Content]. */
data class TvHomeCatalog(
    val hero: TvMediaItem,
    val rails: List<TvContentRail>,
    val providerName: String? = null,
    /** Entire catalog is demo fallback chosen by the user. */
    val usingMockFallback: Boolean = false,
)

sealed interface TvHomeUiState {
    data object Loading : TvHomeUiState

    data class Content(
        val catalog: TvHomeCatalog,
    ) : TvHomeUiState

    data class Empty(
        val providerName: String?,
        val message: String = "No catalog items from the current provider.",
    ) : TvHomeUiState

    data class Error(
        val message: String,
        val canUseMockFallback: Boolean = true,
    ) : TvHomeUiState
}

sealed interface TvHomeAction {
    data object Retry : TvHomeAction
    data object UseMockFallback : TvHomeAction
    /** Re-read Continue Watching only (enter/resume) — no homepage network. */
    data object RefreshContinueWatching : TvHomeAction
}

enum class TvDestination(val label: String) {
    Home("Home"),
    Search("Search"),
    Watchlist("Watchlist"),
    Settings("Settings"),
}

/** Fixed Phase 2/3 rail ids. */
object TvRailIds {
    const val CONTINUE = "continue"
    const val TRENDING = "trending"
    const val MOVIES = "movies"
    const val ANIME = "anime"
}
