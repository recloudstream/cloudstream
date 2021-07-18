package com.lagradost.cloudstream3.utils

import android.content.Context
import com.lagradost.cloudstream3.ui.WatchType
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.DataStore.setKey

const val VIDEO_POS_DUR = "video_pos_dur"
const val RESULT_WATCH_STATE = "result_watch_state"
const val RESULT_SEASON = "result_season"


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

    var currentAccount: String = "0" //TODO ACCOUNT IMPLEMENTATION

    fun Context.setViewPos(id: Int?, pos: Long, dur: Long) {
        if (id == null) return
        setKey("$currentAccount/$VIDEO_POS_DUR", id.toString(), PosDur(pos, dur))
    }

    fun Context.getViewPos(id: Int): PosDur? {
        return getKey("$currentAccount/$VIDEO_POS_DUR", id.toString(), null)
    }

    fun Context.setResultWatchState(id: Int?, status: Int) {
        if (id == null) return
        setKey("$currentAccount/$RESULT_WATCH_STATE", id.toString(), status)
    }

    fun Context.getResultWatchState(id: Int): WatchType {
        return WatchType.fromInternalId(getKey<Int>("$currentAccount/$RESULT_WATCH_STATE", id.toString(), null))
    }

    fun Context.getResultSeason(id: Int): Int {
        return getKey("$currentAccount/$RESULT_SEASON", id.toString(), -1)!!
    }
    fun Context.setResultSeason(id: Int, value : Int?) {
        return setKey("$currentAccount/$RESULT_SEASON", id.toString(), value)
    }
}