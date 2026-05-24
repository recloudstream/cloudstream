package com.lagradost.cloudstream3.utils

actual fun getCurrentLocale(): String =
    js("navigator.language") as? String ?: "en"

actual fun localizedLanguageName(ietfTag: String, localizedTo: String): String? =
    try {
        val dn = IntlDisplayNames(arrayOf(localizedTo), js("({ type: 'language' })"))
        val name = dn.of(ietfTag)
        if (name.isNullOrBlank() || name.equals(ietfTag, ignoreCase = true)) null else name
    } catch (e: Throwable) {
        null
    }

@JsName("Intl.DisplayNames")
external class IntlDisplayNames(locales: Array<String>, options: dynamic) {
    fun of(code: String): String?
}