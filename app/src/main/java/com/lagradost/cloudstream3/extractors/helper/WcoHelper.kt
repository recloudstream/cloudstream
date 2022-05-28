package com.lagradost.cloudstream3.extractors.helper

import com.lagradost.cloudstream3.app

class WcoHelper {
    companion object {
        suspend fun getWcoKey(): String {
            val i = app.get("https://raw.githubusercontent.com/LagradOst/CloudStream-3/master/docs/wcokey.txt").text
            return i
        }
    }
}