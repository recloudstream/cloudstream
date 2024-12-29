package com.lagradost.cloudstream3.utils

import java.net.URLDecoder
import java.net.URLEncoder

object StringUtils {
    fun String.encodeUri(): String {
        URLEncoder.encode(this, "UTF-8")
    }

    fun String.decodeUri(): String {
        URLDecoder.decode(this, "UTF-8")
    }
}