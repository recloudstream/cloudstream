package com.lagradost.cloudstream3.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.lagradost.cloudstream3.R

enum class WatchType(val internalId: Int, @StringRes val stringRes: Int, @DrawableRes val iconRes: Int) {
    WATCHING(0, R.string.type_watching, R.drawable.ic_baseline_bookmark_24),
    COMPLETED(1, R.string.type_completed, R.drawable.ic_baseline_bookmark_24),
    ONHOLD(2, R.string.type_on_hold, R.drawable.ic_baseline_bookmark_24),
    DROPPED(3, R.string.type_dropped, R.drawable.ic_baseline_bookmark_24),
    PLANTOWATCH(4, R.string.type_plan_to_watch, R.drawable.ic_baseline_bookmark_24),
    NONE(5, R.string.type_none, R.drawable.ic_baseline_add_24);

    companion object {
        fun fromInternalId(id: Int?) = values().find { value -> value.internalId == id } ?: NONE
    }
}

enum class SyncWatchType(val internalId: Int, @StringRes val stringRes: Int, @DrawableRes val iconRes: Int) {
    /*
          -1 -> None
          0 -> Watching
          1 -> Completed
          2 -> OnHold
          3 -> Dropped
          4 -> PlanToWatch
          5 -> ReWatching
          */
    NONE(-1, R.string.type_none, R.drawable.ic_baseline_add_24),
    WATCHING(0, R.string.type_watching, R.drawable.ic_baseline_bookmark_24),
    COMPLETED(1, R.string.type_completed, R.drawable.ic_baseline_bookmark_24),
    ONHOLD(2, R.string.type_on_hold, R.drawable.ic_baseline_bookmark_24),
    DROPPED(3, R.string.type_dropped, R.drawable.ic_baseline_bookmark_24),
    PLANTOWATCH(4, R.string.type_plan_to_watch, R.drawable.ic_baseline_bookmark_24),
    REWATCHING(5, R.string.type_re_watching, R.drawable.ic_baseline_bookmark_24);

    companion object {
        fun fromInternalId(id: Int?) = values().find { value -> value.internalId == id } ?: NONE
    }
}
