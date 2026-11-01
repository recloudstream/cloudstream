package com.lagradost.cloudstream3.utils

import java.util.Locale

// TODO: add androidMain actual using LocaleListCompat.getAdjustedDefault() to respect
// per-app language preferences set via AppCompatDelegate.setApplicationLocales()

actual fun getCurrentLocale(): String =
    Locale.getDefault().toLanguageTag()

actual fun localizedLanguageName(ietfTag: String, localizedTo: String): String? {
    val localeOfLangCode = Locale.forLanguageTag(ietfTag)
    val localeOfLocalizeTo = Locale.forLanguageTag(localizedTo)
    val displayName = localeOfLangCode.getDisplayName(localeOfLocalizeTo)

    // Locale.getDisplayName() falls back to the raw tag or "language (country)" form
    // when it doesn't know how to render the name.
    val langCodeWithCountry = "${localeOfLangCode.language} ("
    val failed =
        displayName.equals(ietfTag, ignoreCase = true) ||
        displayName.contains(langCodeWithCountry, ignoreCase = true)

    return if (failed) null else displayName
}