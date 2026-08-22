package com.lagradost.cloudstream3.utils

@OptIn(ExperimentalWasmJsInterop::class)
external class IntlDisplayNames(locale: String, options: JsAny) : JsAny {
    fun of(code: String): String?
}

@OptIn(ExperimentalWasmJsInterop::class)
private fun navigatorLanguage(): String =
    js("navigator.language")

actual fun getCurrentLocale(): String =
    try { navigatorLanguage() } catch (_: Throwable) { "en" }

@OptIn(ExperimentalWasmJsInterop::class)
private fun buildOptions(): JsAny =
    js("({ type: 'language' })")

@OptIn(ExperimentalWasmJsInterop::class)
private fun makeDisplayNames(locale: String, options: JsAny): IntlDisplayNames =
    js("new Intl.DisplayNames([locale], options)")

@OptIn(ExperimentalWasmJsInterop::class)
actual fun localizedLanguageName(ietfTag: String, localizedTo: String): String? =
    try {
        val dn = makeDisplayNames(localizedTo, buildOptions())
        val name = dn.of(ietfTag)
        if (name.isNullOrBlank() || name.equals(ietfTag, ignoreCase = true)) null else name
    } catch (_: Throwable) {
        null
    }
