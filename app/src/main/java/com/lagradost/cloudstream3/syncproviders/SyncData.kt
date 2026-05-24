package com.lagradost.cloudstream3.syncproviders

import androidx.annotation.Keep
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.utils.DataStoreHelper.BookmarkedData
import com.lagradost.cloudstream3.utils.DataStoreHelper.PosDur
import com.lagradost.cloudstream3.utils.downloader.DownloadObjects.ResumeWatching
import com.lagradost.cloudstream3.utils.downloader.DownloadObjects.DownloadHeaderCached

@Keep
data class SyncSettings(
    @JsonProperty("theme") var theme: String? = null,
    @JsonProperty("autoPlayNext") var autoPlayNext: Boolean? = null,
    @JsonProperty("defaultPlayer") var defaultPlayer: String? = null,
    @JsonProperty("currentHomePage") var currentHomePage: String? = null,
    @JsonProperty("appLayout") var appLayout: Int? = null
)

@Keep
data class SyncBookmarks(
    @JsonProperty("planToWatch") var planToWatch: List<String> = emptyList(),
    @JsonProperty("completed") var completed: List<String> = emptyList(),
    @JsonProperty("watching") var watching: List<String> = emptyList(),
    @JsonProperty("onHold") var onHold: List<String> = emptyList(),
    @JsonProperty("dropped") var dropped: List<String> = emptyList()
)

@Keep
data class SyncWatchProgressItem(
    @JsonProperty("mediaTitle") var mediaTitle: String = "",
    @JsonProperty("season") var season: Int = 0,
    @JsonProperty("episode") var episode: Int = 0,
    @JsonProperty("timestampInSeconds") var timestampInSeconds: Long = 0L,
    @JsonProperty("lastUpdated") var lastUpdated: String = ""
)

@Keep
data class SyncReviewItem(
    @JsonProperty("mediaTitle") var mediaTitle: String = "",
    @JsonProperty("rating") var rating: Int = 0,
    @JsonProperty("note") var note: String? = null
)

@Keep
data class WatchProgressDetailsItem(
    @JsonProperty("headerCached") var headerCached: DownloadHeaderCached? = null,
    @JsonProperty("resumeWatching") var resumeWatching: ResumeWatching? = null,
    @JsonProperty("posDur") var posDur: PosDur? = null
)

@Keep
data class SyncData(
    @JsonProperty("userSettings") var userSettings: SyncSettings? = null,
    @JsonProperty("repositories") var repositories: List<String> = emptyList(),
    @JsonProperty("bookmarks") var bookmarks: SyncBookmarks = SyncBookmarks(),
    @JsonProperty("watchProgress") var watchProgress: List<SyncWatchProgressItem> = emptyList(),
    @JsonProperty("reviews") var reviews: List<SyncReviewItem> = emptyList(),
    @JsonProperty("bookmarkDetails") var bookmarkDetails: Map<String, BookmarkedData> = emptyMap(),
    @JsonProperty("watchProgressDetails") var watchProgressDetails: Map<String, WatchProgressDetailsItem> = emptyMap(),
    @JsonProperty("plugins") var plugins: List<String> = emptyList()
)
