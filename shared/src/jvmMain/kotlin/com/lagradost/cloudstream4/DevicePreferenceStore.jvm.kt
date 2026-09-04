package com.lagradost.cloudstream4

import androidx.compose.runtime.Composable
import com.mihon.common.preference.InMemoryPreferenceStore

/** As this is mostly for testing so far, we can assume we have a shared global for settings */
internal val settings = AppSettings()

@Composable
actual fun rememberAppSettings(): AppSettings {
    return settings
}

fun AppSettings() : AppSettings {
    return AppSettings(preferences = InMemoryPreferenceStore())
}
