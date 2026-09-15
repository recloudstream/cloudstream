package com.lagradost.cloudstream3.tv.model

/**
 * Immutable TV search presentation models (Phase 7).
 * Mapped from domain [com.lagradost.cloudstream3.SearchResponse] — UI must not hold mutable DTOs.
 * Navigation identity is always [TvContentRef] — same Details path as Home.
 */
data class TvSearchResult(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val posterUrl: String? = null,
    val year: Int? = null,
    val rating: String? = null,
    val typeLabel: String? = null,
    val providerName: String,
    val posterHeaders: Map<String, String>? = null,
    /** Same compact identity Home uses for [com.lagradost.cloudstream3.tv.details.TvDetailsScreen]. */
    val contentRef: TvContentRef,
) {
    fun toMediaItem(): TvMediaItem = TvMediaItem(
        id = id,
        title = title,
        subtitle = subtitle,
        posterUrl = posterUrl,
        backdropUrl = posterUrl,
        year = year,
        rating = rating,
        typeLabel = typeLabel,
        apiName = contentRef.apiName,
        url = contentRef.url,
        posterHeaders = posterHeaders,
        isMock = false,
    )
}

/** Successful search payload inside [TvSearchUiState.Content]. */
data class TvSearchCatalog(
    val query: String,
    val results: List<TvSearchResult>,
    val providerCount: Int,
    val failedProviderCount: Int = 0,
)

sealed interface TvSearchUiState {
    /** No submitted query yet (or query cleared). */
    data object Idle : TvSearchUiState

    data class Loading(
        val query: String,
    ) : TvSearchUiState

    data class Content(
        val catalog: TvSearchCatalog,
    ) : TvSearchUiState

    data class Empty(
        val query: String,
        val message: String = "No results for this query.",
        val failedProviderCount: Int = 0,
        val providerCount: Int = 0,
    ) : TvSearchUiState

    data class Error(
        val query: String,
        val message: String,
    ) : TvSearchUiState
}

sealed interface TvSearchAction {
    data class UpdateQuery(val query: String) : TvSearchAction
    /** Explicit submit — preferred over per-keystroke (mirrors SearchFragment.onQueryTextSubmit). */
    data object Submit : TvSearchAction
    data object Clear : TvSearchAction
    data object Retry : TvSearchAction
}
