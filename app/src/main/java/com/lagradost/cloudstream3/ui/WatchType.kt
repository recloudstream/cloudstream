package com.lagradost.cloudstream3.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.syncproviders.SyncAPI

/**
 * Enum class representing watch states for library.
 *
 * @property internalId The unique ID used internally to identify the watch type and to store data. This value must never be changed.
 * @property stringRes The string resource ID representing the watch type name.
 * @property iconRes The drawable resource ID representing the watch type icon.
 */
enum class WatchType(val internalId: Int, @StringRes val stringRes: Int, @DrawableRes val iconRes: Int) {
    WATCHING(internalId = 0, R.string.type_watching, R.drawable.ic_baseline_bookmark_24),
    COMPLETED(internalId = 1, R.string.type_completed, R.drawable.ic_baseline_bookmark_24),
    ONHOLD(internalId = 2, R.string.type_on_hold, R.drawable.ic_baseline_bookmark_24),
    DROPPED(internalId = 3, R.string.type_dropped, R.drawable.ic_baseline_bookmark_24),
    PLANTOWATCH(internalId = 4, R.string.type_plan_to_watch, R.drawable.ic_baseline_bookmark_24),

    // Any types with negative internal IDs, or those for which the order is important,
    // should be placed at the bottom. This ensures that we don't need to worry about
    // their internal IDs when adding new types in the future.
    NOTINTERESTED(internalId = -2, R.string.type_not_interested, R.drawable.ic_baseline_bookmark_24),
    NONE(internalId = -1, R.string.type_none, R.drawable.ic_baseline_add_24);

    companion object {
        /**
         * Finds a [WatchType] corresponding to the given [internalId].
         *
         * @param id The internal ID to search for.
         * @return The corresponding [WatchType], or [NONE] if no match is found.
         */
        fun fromInternalId(id: Int?) = entries.find { value -> value.internalId == id } ?: NONE
    }
}

/**
 * Enum class representing various watch states for when using a remote library in [SyncAPI].
 *
 * @property internalId The unique ID used internally to identify the watch type and to store data locally. This value must never be changed.
 * @property stringRes The string resource ID representing the watch type name.
 * @property iconRes The drawable resource ID representing the watch type icon.
 */
enum class SyncWatchType(val internalId: Int, @StringRes val stringRes: Int, @DrawableRes val iconRes: Int) {
    NONE(internalId = -1, R.string.type_none, R.drawable.ic_baseline_add_24),
    WATCHING(internalId = 0, R.string.type_watching, R.drawable.ic_baseline_bookmark_24),
    COMPLETED(internalId = 1, R.string.type_completed, R.drawable.ic_baseline_bookmark_24),
    ONHOLD(internalId = 2, R.string.type_on_hold, R.drawable.ic_baseline_bookmark_24),
    DROPPED(internalId = 3, R.string.type_dropped, R.drawable.ic_baseline_bookmark_24),
    PLANTOWATCH(internalId = 4, R.string.type_plan_to_watch, R.drawable.ic_baseline_bookmark_24),
    REWATCHING(internalId = 5, R.string.type_re_watching, R.drawable.ic_baseline_bookmark_24),

    // Any types with negative internal IDs, or those for which the order is important, 
    // should be placed at the bottom, except for "NONE", which has a negative ID but is 
    // placed at the top for UI reasons.
    NOTINTERESTED(internalId = -2, R.string.type_not_interested, R.drawable.ic_baseline_bookmark_24);

    companion object {
        /**
         * Finds a [SyncWatchType] corresponding to the given [internalId].
         *
         * @param id The internal ID to search for.
         * @return The corresponding [SyncWatchType], or [NONE] if no match is found.
         */
        fun fromInternalId(id: Int?) = entries.find { value -> value.internalId == id } ?: NONE
    }
}