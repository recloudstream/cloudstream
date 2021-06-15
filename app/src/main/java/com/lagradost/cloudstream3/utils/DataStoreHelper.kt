package com.lagradost.cloudstream3.utils

import android.content.Context
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.DataStore.setKey

const val VIDEO_POS_DUR = "video_pos_dur"

data class PosDur(val position: Long, val duration: Long)

object DataStoreHelper {
    var currentAccount: String = "0" //TODO ACCOUNT IMPLEMENTATION

    fun Context.saveViewPos(id: Int?, pos: Long, dur: Long) {
        if(id == null) return
        setKey("$currentAccount/$VIDEO_POS_DUR", id.toString(), PosDur(pos, dur))
    }

    fun Context.getViewPos(id: Int): PosDur? {
        return getKey<PosDur>("$currentAccount/$VIDEO_POS_DUR", id.toString(), null)
    }
}