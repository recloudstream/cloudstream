package com.lagradost.cloudstream3.utils

import android.content.Context
import com.lagradost.cloudstream3.ui.WatchType
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.DataStore.setKey

const val VIDEO_POS_DUR = "video_pos_dur"
const val RESULT_WATCH_STATE = "result_watch_state"

data class PosDur(val position: Long, val duration: Long)

object DataStoreHelper {
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
}