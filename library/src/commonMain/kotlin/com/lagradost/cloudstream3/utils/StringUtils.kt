package com.lagradost.cloudstream3.utils

import io.ktor.http.decodeURLQueryComponent
import io.ktor.http.encodeURLParameter

object StringUtils {
    fun String.decodeUrl(): String {
        return this.decodeURLQueryComponent()
    }

    fun String.encodeUrl(): String {
        return this.encodeURLParameter()
    }

    // Deprecate after next stable

    /* @Deprecated(
        message = "Use Ktor 'Url' naming convention instead.",
        replaceWith = ReplaceWith("this.encodeUrl()")
    ) */
    fun String.encodeUri(): String = encodeUrl()

    /* @Deprecated(
        message = "Use Ktor 'Url' naming convention instead.",
        replaceWith = ReplaceWith("this.decodeUrl()")
    ) */
    fun String.decodeUri(): String = decodeUrl()
}

