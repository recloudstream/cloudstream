package com.lagradost.cloudstream4

import androidx.compose.runtime.Composable
import com.mihon.common.preference.PreferenceStore

@Composable
expect fun rememberAppSettings() : AppSettings

/**
 * App settings for every setting we have, that way we can just inject AppSettings into a viewmodel,
 * or similar.
 *
 * We use an internal constructor to force the user to construct it using the plantform specific
 * initalizer
 * */
class AppSettings internal constructor(
    preferences : PreferenceStore,
    // Todo add database key-value interface here, possibly using the same PreferenceStore interface
) {
    val general = GeneralPreferences(preferences)
}

class GeneralPreferences(preferences: PreferenceStore) {
    val locale = preferences.getString("app_locale")
    val bananas = preferences.getInt("benene_count")
    val parallelDownloads = preferences.getInt("download_parallel_key", 3)
    val concurrentConnections = preferences.getInt("download_concurrent_key", 3)
    val batterOptimization = preferences.getBoolean("battery_optimisation_key", false)

    /** Please note that it used R.array.dns_pref before, this was a bug and caused this setting to be language sensitive **/
    val dns = preferences.getInt("dns_key", 0)

    val jsdelivrProxy = preferences.getBoolean("jsdelivr_proxy_key",false)

    /** This should honesty be refactored to a single setting */
    val downloadPath = preferences.getString("download_path_key","")
    val downloadPathVisual = preferences.getString("download_path_key_visual","")
}