package com.lagradost.cloudstream3.tv.model

/**
 * Minimal TV presentation models for language multi-select (Phase 13).
 * Stored values remain existing IETF tags — [value] is what setKey persists;
 * [displayName] is UI-only (flag + localized name).
 */
data class TvLanguageOption(
    /** Persisted IETF BCP 47 tag (or legacy unknown string). */
    val value: String,
    /** Display label — never written to prefs. */
    val displayName: String,
    /** True when [value] is not in the shared SubtitleHelper language list. */
    val isUnknown: Boolean = false,
)

/**
 * Temp multi-select state while a TV dialog is open.
 * Commit once via Apply; Back/Cancel discards without writing.
 */
data class TvMultiSelectState(
    val options: List<TvLanguageOption>,
    val selectedValues: Set<String>,
)
