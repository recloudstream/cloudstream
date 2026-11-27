package com.lagradost.cloudstream3.utils

/**
 * Returns the current locale as an IETF BCP 47 language tag.
 */
expect fun getCurrentLocale(): String

/**
 * Returns the display name of [ietfTag] localized into [localizedTo].
 * Returns null if the platform couldn't produce a meaningful name
 * (i.e. it just echoed back the tag or contained a bare language code with parentheses).
 */
expect fun localizedLanguageName(ietfTag: String, localizedTo: String): String?