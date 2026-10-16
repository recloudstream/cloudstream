package com.lagradost.cloudstream4.library

import android.content.Context
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.DataStore.getKeys
import com.lagradost.cloudstream3.utils.DataStore.removeKey
import com.lagradost.cloudstream3.utils.DataStore.setKey
import com.lagradost.cloudstream4.android.DesktopAndroid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The watch data in the same keys and JSON as the Android app's DataStoreHelper, under its first
 * account ("0/..."), so a later backup import can read either app's data. It lives in the same
 * settings store extensions use through DataStore.
 */
class DataStoreWatchStore(
    private val context: Context = DesktopAndroid.application,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : WatchStore {
    private val _version = MutableStateFlow(0)
    override val version: StateFlow<Int> = _version.asStateFlow()

    private fun changed() = _version.update { it + 1 }

    // The Android app's JSON shapes. Fields desktop does not use are kept out, Jackson ignores them on reading

    @JsonIgnoreProperties(ignoreUnknown = true)
    internal data class BookmarkedData(
        @JsonProperty("bookmarkedTime") val bookmarkedTime: Long,
        @JsonProperty("id") val id: Int?,
        @JsonProperty("latestUpdatedTime") val latestUpdatedTime: Long,
        @JsonProperty("name") val name: String,
        @JsonProperty("url") val url: String,
        @JsonProperty("apiName") val apiName: String,
        @JsonProperty("type") val type: TvType?,
        @JsonProperty("posterUrl") val posterUrl: String?,
        @JsonProperty("year") val year: Int?,
        @JsonProperty("posterHeaders") val posterHeaders: Map<String, String>? = null,
        @JsonProperty("plot") val plot: String? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    internal data class PosDur(
        @JsonProperty("position") val position: Long,
        @JsonProperty("duration") val duration: Long,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    internal data class ResumeWatching(
        @JsonProperty("parentId") val parentId: Int,
        @JsonProperty("episodeId") val episodeId: Int?,
        @JsonProperty("episode") val episode: Int?,
        @JsonProperty("season") val season: Int?,
        @JsonProperty("updateTime") val updateTime: Long,
        @JsonProperty("isFromDownload") val isFromDownload: Boolean = false,
    )

    /** What Continue watching shows for a title, the Android app's download header cache */
    @JsonIgnoreProperties(ignoreUnknown = true)
    internal data class DownloadHeaderCached(
        @JsonProperty("apiName") val apiName: String,
        @JsonProperty("url") val url: String,
        @JsonProperty("type") val type: TvType,
        @JsonProperty("name") val name: String,
        @JsonProperty("poster") val poster: String?,
        @JsonProperty("cacheTime") val cacheTime: Long,
        @JsonProperty("id") val id: Int,
    )

    /** Desktop's own, the Android app has no such key */
    @JsonIgnoreProperties(ignoreUnknown = true)
    internal data class LastSource(
        @JsonProperty("name") val name: String,
        @JsonProperty("quality") val quality: Int,
    )

    private fun BookmarkedData.toHeader(id: Int) = TitleHeader(id, apiName, url, name, type, posterUrl, posterHeaders, year, plot)

    override fun bookmark(id: Int): Bookmark? = synchronized(this) {
        val status = WatchType.fromInternalId(context.getKey("$ACCOUNT/$WATCH_STATE/$id", Int::class.javaObjectType))
        if (status == WatchType.NONE) return null
        val data = context.getKey("$ACCOUNT/$WATCH_STATE_DATA/$id", BookmarkedData::class.java) ?: return null
        Bookmark(data.toHeader(id), status, data.bookmarkedTime, data.latestUpdatedTime)
    }

    override fun bookmarks(): List<Bookmark> = synchronized(this) {
        context.getKeys("$ACCOUNT/$WATCH_STATE_DATA").mapNotNull { it.substringAfterLast('/').toIntOrNull()?.let(::bookmark) }
    }

    override fun setBookmark(header: TitleHeader, status: WatchType) {
        synchronized(this) {
            val id = header.id
            if (status == WatchType.NONE) {
                context.removeKey("$ACCOUNT/$WATCH_STATE/$id")
                context.removeKey("$ACCOUNT/$WATCH_STATE_DATA/$id")
            } else {
                val now = clock()
                val since = context.getKey("$ACCOUNT/$WATCH_STATE_DATA/$id", BookmarkedData::class.java)?.bookmarkedTime ?: now
                context.setKey("$ACCOUNT/$WATCH_STATE/$id", status.internalId)
                context.setKey(
                    "$ACCOUNT/$WATCH_STATE_DATA/$id",
                    BookmarkedData(since, id, now, header.name, header.url, header.apiName, header.type, header.posterUrl, header.year, header.posterHeaders, header.plot),
                )
            }
        }
        changed()
    }

    override fun position(id: Int): PlaybackPosition? =
        context.getKey("$ACCOUNT/$POS_DUR/$id", PosDur::class.java)?.let { PlaybackPosition(it.position, it.duration) }

    override fun setPosition(id: Int, positionMs: Long, durationMs: Long) {
        if (durationMs < MIN_DURATION_MS) return
        context.setKey("$ACCOUNT/$POS_DUR/$id", PosDur(positionMs, durationMs))
        changed()
    }

    override fun resume(parentId: Int): ResumeEntry? = synchronized(this) {
        val resume = context.getKey("$ACCOUNT/$RESUME_WATCHING/$parentId", ResumeWatching::class.java) ?: return null
        val header = context.getKey("$HEADER_CACHE/$parentId", DownloadHeaderCached::class.java) ?: return null
        ResumeEntry(
            TitleHeader(parentId, header.apiName, header.url, header.name, header.type, header.poster),
            resume.episodeId, resume.episode, resume.season, resume.updateTime,
        )
    }

    override fun resumeEntries(): List<ResumeEntry> = synchronized(this) {
        context.getKeys("$ACCOUNT/$RESUME_WATCHING").mapNotNull { it.substringAfterLast('/').toIntOrNull()?.let(::resume) }
    }

    override fun setResume(entry: ResumeEntry) {
        synchronized(this) {
            val header = entry.header
            context.setKey("$ACCOUNT/$RESUME_WATCHING/${header.id}", ResumeWatching(header.id, entry.episodeId, entry.episode, entry.season, entry.updateTime))
            context.setKey(
                "$HEADER_CACHE/${header.id}",
                DownloadHeaderCached(header.apiName, header.url, header.type ?: TvType.Movie, header.name, header.posterUrl, clock(), header.id),
            )
        }
        changed()
    }

    override fun removeResume(parentId: Int) {
        context.removeKey("$ACCOUNT/$RESUME_WATCHING/$parentId")
        changed()
    }

    override fun lastSource(parentId: Int): SavedSource? =
        context.getKey("$ACCOUNT/$LAST_SOURCE/$parentId", LastSource::class.java)?.let { SavedSource(it.name, it.quality) }

    // Not a change screens show, so no new version
    override fun setLastSource(parentId: Int, source: SavedSource) {
        context.setKey("$ACCOUNT/$LAST_SOURCE/$parentId", LastSource(source.name, source.quality))
    }

    companion object {
        /** The Android app's first account, DataStoreHelper.currentAccount */
        private const val ACCOUNT = "0"
        private const val WATCH_STATE = "result_watch_state"
        private const val WATCH_STATE_DATA = "result_watch_state_data"
        private const val POS_DUR = "video_pos_dur"
        private const val RESUME_WATCHING = "result_resume_watching_2"
        private const val HEADER_CACHE = "download_header_cache"
        private const val LAST_SOURCE = "desktop_last_source"

        val instance: DataStoreWatchStore by lazy { DataStoreWatchStore() }
    }
}
