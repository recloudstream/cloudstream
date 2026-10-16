package com.lagradost.cloudstream4.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.AllLanguagesName
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream4.generated.resources.*
import com.lagradost.cloudstream4.download.Downloads
import com.lagradost.cloudstream4.network.DesktopHttp
import com.lagradost.cloudstream4.rememberAppSettings
import com.lagradost.cloudstream4.theme.CloudStreamPrimaryColor
import com.lagradost.cloudstream4.theme.modeToTheme
import com.mihon.presentation.settings.Preference
import com.mihon.presentation.settings.SearchableSettings
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/*
 * Desktop versions of the settings screens in app/ui/settings. They use the same AppSettings keys,
 * but leave out what only applies to Android: TV layout, touch gestures, battery, rotation, PiP,
 * APK updates and biometrics. Accounts, updates, backups and downloads come with the phases that
 * add those features.
 */

/** The settings home: one entry per category */
class SettingsHomeScreen(private val open: (SearchableSettings) -> Unit) : SearchableSettings {
    @Composable
    override fun getTitleRes(): String = stringResource(Res.string.title_settings)

    @Composable
    override fun getPreferences(): List<Preference> = listOf(
        category(SettingsGeneralScreen, Res.drawable.build_24px),
        category(SettingsPlayerScreen, Res.drawable.play_arrow_24px),
        category(SettingsLayoutScreen, Res.drawable.format_paint_24px),
        category(SettingsProvidersScreen, Res.drawable.plugin_lang),
        category(ExtensionsScreen(open), Res.drawable.extension_24px),
    ) + maintenanceCategories(open)

    @Composable
    private fun category(screen: SearchableSettings, icon: org.jetbrains.compose.resources.DrawableResource) =
        Preference.PreferenceItem.TextPreference(
            title = screen.getTitleRes(),
            icon = painterResource(icon),
            onClick = { open(screen) },
        )
}

object SettingsGeneralScreen : SearchableSettings {
    @Composable
    override fun getTitleRes(): String = stringResource(Res.string.category_general)

    @Composable
    override fun getPreferences(): List<Preference> {
        val settings = rememberAppSettings()
        val uriHandler = LocalUriHandler.current
        val bananas = settings.general.bananas.get()
        val downloadPath by settings.general.downloadPath.changes().collectAsState(settings.general.downloadPath.get())

        fun link(title: String, url: String, icon: Painter) = Preference.PreferenceItem.TextPreference(
            title = title,
            subtitle = url,
            icon = icon,
            onClick = { runCatching { uriHandler.openUri(url) } },
        )

        return persistentListOf(
            Preference.PreferenceGroup(
                title = stringResource(Res.string.pref_category_links),
                preferenceItems = persistentListOf(
                    link(
                        stringResource(Res.string.github),
                        "https://github.com/recloudstream/cloudstream",
                        painterResource(Res.drawable.ic_github_logo)
                    ),
                    link(
                        stringResource(Res.string.lightnovel),
                        "https://github.com/LagradOst/QuickNovel",
                        painterResource(Res.drawable.quick_novel_icon)
                    ),
                    link(
                        stringResource(Res.string.discord),
                        "https://discord.gg/5Hus6fM",
                        painterResource(Res.drawable.ic_baseline_discord_24)
                    ),
                    link(
                        stringResource(Res.string.cs3wiki),
                        "https://cloudstream.miraheze.org/",
                        painterResource(Res.drawable.description_24px)
                    ),
                )
            ),
            Preference.PreferenceItem.ListPreference(
                preference = settings.general.dns,
                entries = DesktopHttp.dnsProviders.mapValues { (value, name) -> if (value == 0) stringResource(Res.string.none) else name },
                title = stringResource(Res.string.dns_pref),
                subtitle = "%s · " + stringResource(Res.string.dns_pref_summary),
            ),
            Preference.PreferenceItem.TextPreference(
                title = stringResource(Res.string.download_path_pref),
                subtitle = downloadPath.ifBlank { Downloads.defaultRoot().absolutePath },
                icon = painterResource(Res.drawable.folder_open_24px),
                onClick = { chooseFolder(downloadPath.ifBlank { Downloads.defaultRoot().absolutePath })?.let { settings.general.downloadPath.set(it) } },
            ),
            Preference.PreferenceItem.TextPreference(
                title = stringResource(Res.string.benene),
                subtitle = if (bananas == 0) {
                    stringResource(Res.string.benene_count_text_none)
                } else {
                    stringResource(Res.string.benene_count_text, bananas)
                },
                onClick = { settings.general.bananas.set(bananas + 1) },
                icon = painterResource(Res.drawable.benene),
            ),
            Preference.PreferenceItem.InfoPreference(title = stringResource(Res.string.legal_notice_text)),
        )
    }
}

object SettingsPlayerScreen : SearchableSettings {
    @Composable
    override fun getTitleRes(): String = stringResource(Res.string.category_player)

    @Composable
    override fun getPreferences(): List<Preference> {
        val settings = rememberAppSettings()
        return persistentListOf(
            Preference.PreferenceGroup(
                title = stringResource(Res.string.pref_category_player_features),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.SwitchPreference(
                        preference = settings.player.resizeEnabled,
                        title = stringResource(Res.string.player_size_settings),
                        subtitle = stringResource(Res.string.player_size_settings_des),
                        icon = painterResource(Res.drawable.aspect_ratio_24px),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = settings.player.speedEnabled,
                        title = stringResource(Res.string.eigengraumode_settings),
                        subtitle = stringResource(Res.string.speed_setting_summary),
                        icon = painterResource(Res.drawable.ic_baseline_speed_24),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = settings.player.autoPlayEnabled,
                        title = stringResource(Res.string.autoplay_next_settings),
                        subtitle = stringResource(Res.string.autoplay_next_settings_des),
                        icon = painterResource(Res.drawable.skip_next_24px),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = settings.player.startPaused,
                        title = stringResource(Res.string.start_paused_settings),
                        subtitle = stringResource(Res.string.start_paused_settings_des),
                        icon = painterResource(Res.drawable.pause_24px),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = settings.player.skipOpEnabled,
                        title = stringResource(Res.string.video_skip_op),
                        subtitle = stringResource(Res.string.enable_skip_op_from_database_des),
                        icon = painterResource(Res.drawable.keyboard_double_arrow_right_24px),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = settings.player.previewBarEnabled,
                        title = stringResource(Res.string.preview_seekbar),
                        subtitle = stringResource(Res.string.preview_seekbar_desc),
                        icon = painterResource(Res.drawable.picture_in_picture_center_24px),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = settings.player.episodeSync,
                        title = stringResource(Res.string.episode_sync_settings),
                        subtitle = stringResource(Res.string.episode_sync_settings_des),
                        icon = painterResource(Res.drawable.autorenew_24px)
                    ),
                )
            ),
            subtitlePreferences(),
        )
    }
}

object SettingsLayoutScreen : SearchableSettings {
    @Composable
    override fun getTitleRes(): String = stringResource(Res.string.category_ui)

    /**
     * The values of R.array.themes_names_values, without Material You which needs Android 12. The
     * names are the desktop's: "Black" is its Prime Video dark, the default, and "Light" the same in white
     */
    private val themes = listOf(
        "Black" to "Dark", "Light" to "Light", "System" to "System", "AmoledLight" to "Black", "Amoled" to "Amoled",
        "Dracula" to "Dracula", "Lavender" to "Lavender Dreams", "SilentBlue" to "Silent Blue",
    )

    /** Same values as R.array.themes_overlay_names_values, without the Material You colors */
    private val primaryColors = listOf(
        "Normal" to "Normal", "DandelionYellow" to "Dandelion Yellow", "CarnationPink" to "Carnation Pink",
        "Orange" to "Orange", "DarkGreen" to "Dark Green", "Maroon" to "Maroon", "NavyBlue" to "Navy Blue",
        "Grey" to "Grey", "White" to "White", "CoolBlue" to "Cool Blue", "Brown" to "Brown", "Blue" to "Cool",
        "Red" to "Fire", "Purple" to "Burple", "Green" to "Green", "GreenApple" to "Apple", "Banana" to "Banana",
        "Party" to "Party", "Pink" to "Pink Pain", "Lavender" to "Lavender",
    )

    @Composable
    private fun RoundColor(color: Color) {
        Box(
            modifier = Modifier
                .padding(start = 15.dp)
                .size(20.dp)
                .border(width = 1.5.dp, shape = CircleShape, color = MaterialTheme.colorScheme.onBackground)
                .background(color, CircleShape)
        )
    }

    @Composable
    private fun SearchQuality.label(): String = stringResource(
        when (this) {
            SearchQuality.BlueRay -> Res.string.quality_blueray
            SearchQuality.Cam -> Res.string.quality_cam
            SearchQuality.CamRip -> Res.string.quality_cam_rip
            SearchQuality.DVD -> Res.string.quality_dvd
            SearchQuality.HD -> Res.string.quality_hd
            SearchQuality.HQ -> Res.string.quality_hq
            SearchQuality.HdCam -> Res.string.quality_cam_hd
            SearchQuality.Telecine -> Res.string.quality_tc
            SearchQuality.Telesync -> Res.string.quality_ts
            SearchQuality.WorkPrint -> Res.string.quality_workprint
            SearchQuality.SD -> Res.string.quality_sd
            SearchQuality.FourK -> Res.string.quality_4k
            SearchQuality.UHD -> Res.string.quality_uhd
            SearchQuality.SDR -> Res.string.quality_sdr
            SearchQuality.HDR -> Res.string.quality_hdr
            SearchQuality.WebRip -> Res.string.quality_webrip
        }
    )

    @Composable
    override fun getPreferences(): List<Preference> {
        val settings = rememberAppSettings()
        val theme by settings.ui.theme.changes().collectAsState(settings.ui.theme.get())

        return persistentListOf(
            Preference.PreferenceGroup(
                title = stringResource(Res.string.pref_category_looks),
                preferenceItems = persistentListOf(
                    // The theme follows these two settings live, see MainContent
                    Preference.PreferenceItem.ListPreference(
                        preference = settings.ui.primaryColor,
                        icon = painterResource(Res.drawable.colors_24px),
                        title = stringResource(Res.string.primary_color_settings),
                        entries = primaryColors.toMap(),
                        // "Normal" is the theme's own accent
                        iconProvider = { key, _ -> RoundColor(modeToTheme(desktopThemeMode(theme), desktopPrimaryColor(key)).primary) },
                    ),
                    Preference.PreferenceItem.ListPreference(
                        preference = settings.ui.theme,
                        icon = painterResource(Res.drawable.palette_24px),
                        title = stringResource(Res.string.app_theme_settings),
                        entries = themes.toMap(),
                        iconProvider = { key, _ ->
                            RoundColor(modeToTheme(desktopThemeMode(key), CloudStreamPrimaryColor.NORMAL).background)
                        },
                    ),
                )
            ),
            Preference.PreferenceGroup(
                title = stringResource(Res.string.pref_category_ui_features),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.SwitchPreference(
                        preference = settings.ui.advancedSearch,
                        title = stringResource(Res.string.advanced_search),
                        subtitle = stringResource(Res.string.advanced_search_des),
                        icon = painterResource(Res.drawable.search_icon)
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = settings.ui.searchSuggestions,
                        title = stringResource(Res.string.search_suggestions),
                        subtitle = stringResource(Res.string.search_suggestions_des),
                        icon = painterResource(Res.drawable.tooltip_24px)
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = settings.ui.trailersEnabled,
                        title = stringResource(Res.string.show_trailers_settings),
                        icon = painterResource(Res.drawable.baseline_theaters_24)
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = settings.ui.kitsuPostersEnabled,
                        title = stringResource(Res.string.kitsu_settings),
                        icon = painterResource(Res.drawable.kitsu_icon)
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = settings.ui.castEnabled,
                        title = stringResource(Res.string.show_cast_in_details),
                        icon = painterResource(Res.drawable.face_24px)
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = settings.ui.fillersEnabled,
                        title = stringResource(Res.string.show_fillers_settings),
                        icon = painterResource(Res.drawable.skip_next_24px)
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = settings.ui.showMetadataOverlay,
                        title = stringResource(Res.string.show_player_metadata_overlay),
                        icon = painterResource(Res.drawable.metadata_overlay_icon)
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = settings.ui.randomButtonEnabled,
                        title = stringResource(Res.string.random_button_settings),
                        subtitle = stringResource(Res.string.random_button_settings_desc),
                        icon = painterResource(Res.drawable.shuffle_24px)
                    ),
                )
            ),
            Preference.PreferenceGroup(
                title = stringResource(Res.string.search_poster_img_des),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.MultiSelectListPreference(
                        preference = settings.ui.filterQuality,
                        title = stringResource(Res.string.pref_filter_search_quality),
                        icon = painterResource(Res.drawable.filter_alt_24px),
                        entries = SearchQuality.entries.associateWith { it.label() }
                    ),
                    dubSubPreference(),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = settings.ui.bottomTitle,
                        title = stringResource(Res.string.bottom_title_settings),
                        subtitle = stringResource(Res.string.bottom_title_settings_des),
                        icon = painterResource(Res.drawable.title_24px)
                    ),
                    Preference.PreferenceItem.SliderPreference(
                        preference = settings.ui.posterSize,
                        title = stringResource(Res.string.poster_size_settings),
                        subtitle = stringResource(Res.string.poster_size_settings_des),
                        valueRange = 0..15,
                        icon = painterResource(Res.drawable.baseline_grid_view_24),
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = stringResource(Res.string.poster_ui_settings),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.SwitchPreference(
                        preference = settings.ui.posterShowRating,
                        title = stringResource(Res.string.show_rating),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = settings.ui.posterShowEpisode,
                        title = stringResource(Res.string.show_episode_text),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = settings.ui.posterShowTitle,
                        title = stringResource(Res.string.show_title),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = settings.ui.posterShowHd,
                        title = stringResource(Res.string.show_hd),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = settings.ui.posterShowDub,
                        title = stringResource(Res.string.show_dub),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = settings.ui.posterShowSub,
                        title = stringResource(Res.string.show_sub),
                    ),
                )
            ),
        )
    }
}

object SettingsProvidersScreen : SearchableSettings {
    @Composable
    override fun getTitleRes(): String = stringResource(Res.string.category_providers)

    @Composable
    private fun TvType.label(): String = stringResource(
        when (this) {
            TvType.TvSeries -> Res.string.tv_series_singular
            TvType.Anime -> Res.string.anime_singular
            TvType.OVA -> Res.string.ova_singular
            TvType.AnimeMovie -> Res.string.movies_singular
            TvType.Cartoon -> Res.string.cartoons_singular
            TvType.Documentary -> Res.string.documentaries_singular
            TvType.Movie -> Res.string.movies_singular
            TvType.Torrent -> Res.string.torrent_singular
            TvType.AsianDrama -> Res.string.asian_drama_singular
            TvType.Live -> Res.string.live_singular
            TvType.Others -> Res.string.other_singular
            TvType.NSFW -> Res.string.nsfw_singular
            TvType.Music -> Res.string.music_singular
            TvType.AudioBook -> Res.string.audio_book_singular
            TvType.CustomMedia -> Res.string.custom_media_singular
            TvType.Audio -> Res.string.audio_singular
            TvType.Podcast -> Res.string.podcast_singular
            TvType.Video -> Res.string.video_singular
        }
    )

    @Composable
    override fun getPreferences(): List<Preference> {
        val settings = rememberAppSettings()
        // Empty until plugins can be loaded on desktop, then each provider adds its language
        val languages = APIHolder.apis.withLock { APIHolder.apis.map { it.lang }.distinct() }.sorted()

        return persistentListOf(
            Preference.PreferenceItem.MultiSelectListPreference(
                title = stringResource(Res.string.provider_lang_settings),
                icon = painterResource(Res.drawable.plugin_lang),
                entries = mapOf(AllLanguagesName to stringResource(Res.string.all_languages_preference)) +
                        languages.associateWith { it },
                preference = settings.provider.extensionLanguages
            ),
            Preference.PreferenceItem.MultiSelectListPreference(
                title = stringResource(Res.string.preferred_media_settings),
                icon = painterResource(Res.drawable.movie_edit_24px),
                preference = settings.provider.preferredMedia,
                entries = TvType.entries.associate { it.ordinal.toString() to it.label() }
            ),
            dubSubPreference(),
        )
    }
}

@Composable
private fun dubSubPreference() = Preference.PreferenceItem.MultiSelectListPreference(
    title = stringResource(Res.string.display_subbed_dubbed_settings),
    icon = painterResource(Res.drawable.audio_capture_24px),
    preference = rememberAppSettings().provider.displayDubSub,
    entries = mapOf(
        DubStatus.None.name to stringResource(Res.string.none),
        DubStatus.Dubbed.name to stringResource(Res.string.app_dubbed_text),
        DubStatus.Subbed.name to stringResource(Res.string.app_subbed_text),
    )
)

/** The system's folder picker, starting in [current]. Null when cancelled */
private fun chooseFolder(current: String): String? {
    val chooser = javax.swing.JFileChooser(java.io.File(current).takeIf { it.isDirectory } ?: java.io.File(System.getProperty("user.home"))).apply {
        dialogTitle = "Download path"
        fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY
        isAcceptAllFileFilterUsed = false
    }
    return if (chooser.showDialog(null, "Use this folder") == javax.swing.JFileChooser.APPROVE_OPTION) chooser.selectedFile?.absolutePath else null
}
