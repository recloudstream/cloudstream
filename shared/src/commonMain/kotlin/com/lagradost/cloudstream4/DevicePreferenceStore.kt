package com.lagradost.cloudstream4

import androidx.compose.runtime.Composable
import com.lagradost.cloudstream3.TvType
import com.mihon.common.preference.PreferenceStore

@Composable
expect fun rememberAppSettings(): AppSettings

/** Only preferences consumed by the extension host. Existing preference keys are preserved. */
class AppSettings internal constructor(preferences: PreferenceStore) {
    val provider = ProviderPreferences(preferences)
    val ui = UIPreferences(preferences)
}

class UIPreferences(preferences: PreferenceStore) {
    val primaryColor = preferences.getString("primary_color_key", "Normal")
    val theme = preferences.getString("app_theme_key", "AmoledLight")
    val layout = preferences.getInt("app_layout_key", -1)
}

class ProviderPreferences(preferences: PreferenceStore) {
    val preferredMedia = preferences.getStringSet("prefer_media_type_key_2",
        TvType.entries.filter { it != TvType.NSFW }.map { it.ordinal.toString() }.toSet())
}
