package com.lagradost.cloudstream3.utils

import com.lagradost.cloudstream3.Prerelease
import io.ktor.http.decodeURLQueryComponent
import io.ktor.http.encodeURLParameter

object StringUtils {
    @Prerelease
    fun String.decodeUrl(): String {
        return this.decodeURLQueryComponent()
    }

    @Prerelease
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

