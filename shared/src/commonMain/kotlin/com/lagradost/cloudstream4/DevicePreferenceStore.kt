package com.lagradost.cloudstream4

import androidx.compose.runtime.Composable
import com.lagradost.cloudstream3.AllLanguagesName
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.cloudstream3.TvType
import com.mihon.common.preference.PreferenceStore
import com.mihon.common.preference.getEnumSet

@Composable
expect fun rememberAppSettings(): AppSettings

/**
 * App settings for every setting we have, that way we can just inject AppSettings into a viewmodel,
 * or similar.
 *
 * We use an internal constructor to force the user to construct it using the plantform specific
 * initalizer
 * */
class AppSettings internal constructor(
    preferences: PreferenceStore,
    // Todo add database key-value interface here, possibly using the same PreferenceStore interface
) {
    val general = GeneralPreferences(preferences)
    val player = PlayerPreferences(preferences)
    val provider = ProviderPreferences(preferences)
    val ui = UIPreferences(preferences)
}

class UIPreferences(preferences: PreferenceStore) {
    val primaryColor = preferences.getString(
        "primary_color_key", "Normal"
    )
    val theme = preferences.getString(
        "app_theme_key", "AmoledLight"
    )
    val layout = preferences.getInt(
        "app_layout_key", -1
    )
    val bottomTitle = preferences.getBoolean(
        "bottom_title_key", true
    )
    val advancedSearch = preferences.getBoolean(
        "advanced_search", true
    )
    val searchSuggestions = preferences.getBoolean(
        "search_suggestions_enabled", true
    )
    val kitsuPostersEnabled = preferences.getBoolean(
        "show_kitsu_posters_key", true
    )
    val trailersEnabled = preferences.getBoolean(
        "show_trailers_key", true
    )
    val castEnabled = preferences.getBoolean(
        "show_cast_in_details_key", true
    )
    val fillersEnabled = preferences.getBoolean(
        "show_fillers_key", false
    )
    val showMetadataOverlay = preferences.getBoolean(
        "show_player_metadata_key", true
    )
    val overscanDp = preferences.getInt(
        "overscan_key", 0
    )
    val posterSize = preferences.getInt(
        "poster_size_key", 0
    )
    val posterShowHd = preferences.getBoolean(
        "show_hd_key", true
    )
    val posterShowDub = preferences.getBoolean(
        "show_dub_key", true
    )
    val posterShowSub = preferences.getBoolean(
        "show_sub_key", true
    )
    val posterShowRating = preferences.getBoolean(
        "show_rating_key", true
    )
    val posterShowTitle = preferences.getBoolean(
        "show_title_key", true
    )
    val posterShowEpisode = preferences.getBoolean(
        "show_episode_text_key", true
    )
    val showClock = preferences.getBoolean(
        "tv_layout_clock_key", false
    )

    val randomButtonEnabled = preferences.getBoolean(
        "random_button_key", false
    )

    val confirmExit = preferences.getInt(
        "confirm_exit_key", -1
    )

    // TODO use this instead of the old key for the UI
    /** This had to be refactored to use enumSet because the old system is prone to bugs */
    val filterQuality = preferences.getEnumSet<SearchQuality>(
        "pref_filter_search_quality_key2", emptySet()
    )
}

class ProviderPreferences(preferences: PreferenceStore) {
    companion object {
        private val defaultPreferredMedia =
            TvType.entries.filter { it != TvType.NSFW }.map { it.ordinal.toString() }.toSet()
        private val defaultDub = DubStatus.entries.map { it.name }.toSet()
    }

    val preferredMedia = preferences.getStringSet(
        "prefer_media_type_key_2", defaultPreferredMedia
    )

    val extentionLanguages = preferences.getStringSet(
        "provider_lang_key", setOf(AllLanguagesName)
    )

    val displayDubSub = preferences.getStringSet(
        "display_sub_key", defaultDub
    )
}

class PlayerPreferences(preferences: PreferenceStore) {
    val episodeSync = preferences.getBoolean("episode_sync_enabled_key", true)
    val defaultPlayer = preferences.getString("player_default_key", "")
    val limitPlayerTitle = preferences.getInt("prefer_limit_title_key", 0)
    val hidePlayerControlNames = preferences.getBoolean("hide_player_control_names_key", false)
    val showName = preferences.getBoolean("show_name", true)
    val showResolution = preferences.getBoolean("show_resolution", true)
    val showMediaInfo = preferences.getBoolean("show_media_info", false)
    val pipEnabled = preferences.getBoolean("pip_enabled_key", true)
    val resizeEnabled = preferences.getBoolean("player_resize_enabled_key", true)
    val speedEnabled = preferences.getBoolean("playback_speed_enabled_key", false)
    val tiktokEnabled = preferences.getBoolean("speedup_key", false)
    val autoPlayEnabled = preferences.getBoolean("autoplay_next_key", true)
    val skipOpEnabled = preferences.getBoolean("enable_skip_op_from_database", true)
    val autoRotateEnabled = preferences.getBoolean("auto_rotate_video_key", true)
    val rotateButtonEnabled = preferences.getBoolean("rotate_video_key", false)
    val previewBarEnabled = preferences.getBoolean("preview_seekbar_key", true)
    val softwareDecoding = preferences.getInt("software_decoding_key2", -1)
    val extraBrightnessEnabled = preferences.getBoolean("extra_brightness_key", false)
    val swipeHorizontalEnabled = preferences.getBoolean("swipe_enabled_key", true)
    val swipeVerticalEnabled = preferences.getBoolean("swipe_vertical_enabled_key", true)
    val doubleTapToSeekEnabled = preferences.getBoolean("double_tap_enabled_key", false)
    val doubleTapToPauseEnabled = preferences.getBoolean("double_tap_pause_enabled_key", false)
    val doubleTapTime = preferences.getInt("double_tap_seek_time_key2", 10)
    val bufferDiskMB = preferences.getInt("video_buffer_disk_key", 0)
    val bufferRamMB = preferences.getInt("video_buffer_size_key", 0)
    val bufferTimeSec = preferences.getInt("video_buffer_length_key", 0)
    val tvSeekOnTime = preferences.getInt("android_tv_interface_on_seek_key", 10)
    val tvSeekOffTime = preferences.getInt("android_tv_interface_off_seek_key", 10)
}

class GeneralPreferences(preferences: PreferenceStore) {
    val locale = preferences.getString("app_locale", "")
    val bananas = preferences.getInt("benene_count", 0)
    val parallelDownloads = preferences.getInt("download_parallel_key", 3)
    val concurrentConnections = preferences.getInt("download_concurrent_key", 3)
    val batterOptimization = preferences.getBoolean("battery_optimisation_key", false)

    /** Please note that it used R.array.dns_pref before, this was a bug and caused this setting to be language sensitive **/
    val dns = preferences.getInt("dns_key", 0)

    val jsdelivrProxy = preferences.getBoolean("jsdelivr_proxy_key", false)

    /** This should honesty be refactored to a single setting */
    val downloadPath = preferences.getString("download_path_key", "")
    val downloadPathVisual = preferences.getString("download_path_key_visual", "")
}