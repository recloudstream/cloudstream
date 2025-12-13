package com.lagradost.cloudstream3.utils

import com.lagradost.cloudstream3.InternalAPI

@InternalAPI
object AppDebug {
    @Volatile
    var isDebug: Boolean = false
}
