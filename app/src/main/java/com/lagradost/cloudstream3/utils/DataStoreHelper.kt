package com.lagradost.cloudstream3.utils

import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.AcraApplication.Companion.getKeys
import com.lagradost.cloudstream3.AcraApplication.Companion.removeKey
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.ui.WatchType

const val VIDEO_POS_DUR = "video_pos_dur"
const val RESULT_WATCH_STATE = "result_watch_state"
const val RESULT_WATCH_STATE_DATA = "result_watch_state_data"
const val RESULT_RESUME_WATCHING = "result_resume_watching"
const val RESULT_SEASON = "result_season"
const val RESULT_DUB = "result_dub"

object DataStoreHelper {
    data class PosDur(val position: Long, val duration: Long)

    fun PosDur.fixVisual(): PosDur {
        if (duration <= 0) return PosDur(0, duration)
        val percentage = position * 100 / duration
        if (percentage <= 1) return PosDur(0, duration)
        if (percentage <= 5) return PosDur(5 * duration / 100, duration)
        if (percentage >= 95) return PosDur(duration, duration)
        return this
    }

    data class BookmarkedData(
        override val id: Int?,
        val bookmarkedTime: Long,
        val latestUpdatedTime: Long,
        override val name: String,
        override val url: String,
        override val apiName: String,
        override val type: TvType,
        override val posterUrl: String?,
        val year: Int?,
    ) : SearchResponse

    data class ResumeWatchingResult(
        override val name: String,
        override val url: String,
        override val apiName: String,
        override val type: TvType,
        override val posterUrl: String?,

        val watchPos: PosDur?,

        override val id: Int?,
        val parentId: Int?,
        val episode: Int?,
        val season: Int?,
        val isFromDownload: Boolean,
    ) : SearchResponse

    var currentAccount: String = "0" //TODO ACCOUNT IMPLEMENTATION

    fun getAllWatchStateIds(): List<Int>? {
        val folder = "$currentAccount/$RESULT_WATCH_STATE"
        return getKeys(folder)?.mapNotNull {
            it.removePrefix("$folder/").toIntOrNull()
        }
    }

    fun getAllResumeStateIds(): List<Int>? {
        val folder = "$currentAccount/$RESULT_RESUME_WATCHING"
        return getKeys(folder)?.mapNotNull {
            it.removePrefix("$folder/").toIntOrNull()
        }
    }

    fun setLastWatched(
        parentId: Int?,
        episodeId: Int?,
        episode: Int?,
        season: Int?,
        isFromDownload: Boolean = false
    ) {
        if (parentId == null || episodeId == null) return
        setKey(
            "$currentAccount/$RESULT_RESUME_WATCHING",
            parentId.toString(),
            VideoDownloadHelper.ResumeWatching(
                parentId,
                episodeId,
                episode,
                season,
                System.currentTimeMillis(),
                isFromDownload
            )
        )
    }

    fun removeLastWatched(parentId: Int?) {
        if (parentId == null) return
        removeKey("$currentAccount/$RESULT_RESUME_WATCHING", parentId.toString())
    }

    fun getLastWatched(id: Int?): VideoDownloadHelper.ResumeWatching? {
        if (id == null) return null
        return getKey(
            "$currentAccount/$RESULT_RESUME_WATCHING",
            id.toString(),
        )
    }

    fun setBookmarkedData(id: Int?, data: BookmarkedData) {
        if (id == null) return
        setKey("$currentAccount/$RESULT_WATCH_STATE_DATA", id.toString(), data)
    }

    fun getBookmarkedData(id: Int?): BookmarkedData? {
        if (id == null) return null
        return getKey("$currentAccount/$RESULT_WATCH_STATE_DATA", id.toString())
    }

    fun setViewPos(id: Int?, pos: Long, dur: Long) {
        if (id == null) return
        if (dur < 30_000) return // too short
        setKey("$currentAccount/$VIDEO_POS_DUR", id.toString(), PosDur(pos, dur))
    }

    fun getViewPos(id: Int?): PosDur? {
        if(id == null) return null
        return getKey("$currentAccount/$VIDEO_POS_DUR", id.toString(), null)
    }

    fun getDub(id: Int): DubStatus {
        return DubStatus.values()[getKey("$currentAccount/$RESULT_DUB", id.toString()) ?: 0]
    }

    fun setDub(id: Int, status: DubStatus) {
        setKey("$currentAccount/$RESULT_DUB", id.toString(), status.ordinal)
    }

    fun setResultWatchState(id: Int?, status: Int) {
        if (id == null) return
        val folder = "$currentAccount/$RESULT_WATCH_STATE"
        if (status == WatchType.NONE.internalId) {
            removeKey(folder, id.toString())
            removeKey("$currentAccount/$RESULT_WATCH_STATE_DATA", id.toString())
        } else {
            setKey(folder, id.toString(), status)
        }
    }

    fun getResultWatchState(id: Int): WatchType {
        return WatchType.fromInternalId(getKey<Int>("$currentAccount/$RESULT_WATCH_STATE", id.toString(), null))
    }

    fun getResultSeason(id: Int): Int {
        return getKey("$currentAccount/$RESULT_SEASON", id.toString()) ?: -1
    }

    fun setResultSeason(id: Int, value: Int?) {
        setKey("$currentAccount/$RESULT_SEASON", id.toString(), value)
    }

    fun addSync(id: Int, idPrefix: String, url: String) {
        setKey("${idPrefix}_sync", id.toString(), url)
    }

    fun getSync(id: Int, idPrefixes: List<String>): List<String?> {
        return idPrefixes.map { idPrefix ->
            getKey("${idPrefix}_sync", id.toString())
        }
    }
}