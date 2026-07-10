package com.lagradost.cloudstream3.utils

import com.lagradost.cloudstream3.Prerelease
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
        replaceWith = ReplaceWith("this.encodeUrl()"),
        level = DeprecationLevel.WARNING,
    ) */
    fun String.encodeUri(): String = encodeUrl()

    /* @Deprecated(
        message = "Use Ktor 'Url' naming convention instead.",
        replaceWith = ReplaceWith("this.decodeUrl()"),
        level = DeprecationLevel.WARNING,
    ) */
    fun String.decodeUri(): String = decodeUrl()
}

