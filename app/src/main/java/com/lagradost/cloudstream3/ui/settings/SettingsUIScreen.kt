package com.lagradost.cloudstream3.ui.settings

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.integerArrayResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import com.lagradost.cloudstream3.CommonActivity.activity
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.cloudstream3.ui.clear
import com.lagradost.cloudstream3.ui.home.HomeChildItemAdapter
import com.lagradost.cloudstream3.ui.home.ParentItemAdapter
import com.lagradost.cloudstream3.ui.search.SearchAdapter
import com.lagradost.cloudstream3.ui.settings.Globals.updateTv
import com.lagradost.cloudstream3.utils.UIHelper.toPx
import com.lagradost.cloudstream4.compose.TV
import com.lagradost.cloudstream4.compose.isLayout
import com.lagradost.cloudstream4.rememberAppSettings
import com.mihon.presentation.settings.Preference
import com.mihon.presentation.settings.SearchableSettings
import com.mihon.presentation.settings.collectAsState
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentMap

class SettingsUIScreen : SearchableSettings {
    @Composable
    override fun getTitleRes(): String = stringResource(R.string.category_ui)

    fun SearchQuality.toStringRes() = when (this) {
        SearchQuality.BlueRay -> R.string.quality_blueray
        SearchQuality.Cam -> R.string.quality_cam
        SearchQuality.CamRip -> R.string.quality_cam_rip
        SearchQuality.DVD -> R.string.quality_dvd
        SearchQuality.HD -> R.string.quality_hd
        SearchQuality.HQ -> R.string.quality_hq
        SearchQuality.HdCam -> R.string.quality_cam_hd
        SearchQuality.Telecine -> R.string.quality_tc
        SearchQuality.Telesync -> R.string.quality_ts
        SearchQuality.WorkPrint -> R.string.quality_workprint
        SearchQuality.SD -> R.string.quality_sd
        SearchQuality.FourK -> R.string.quality_4k
        SearchQuality.UHD -> R.string.quality_uhd
        SearchQuality.SDR -> R.string.quality_sdr
        SearchQuality.HDR -> R.string.quality_hdr
        SearchQuality.WebRip -> R.string.quality_webrip
    }

    @Composable
    override fun getPreferences(): List<Preference> {
        val settings = rememberAppSettings()

        val overscanDp by settings.ui.overscanDp.collectAsState()
        val posterSize by settings.ui.posterSize.collectAsState()

        return persistentListOf(
            Preference.PreferenceGroup(
                title = stringResource(R.string.pref_category_looks),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.ListPreference(
                        preference = settings.ui.primaryColor,
                        icon = painterResource(R.drawable.colors_24px),
                        title = stringResource(R.string.primary_color_settings),
                        entries = stringArrayResource(R.array.themes_overlay_names_values).zip(
                            stringArrayResource(R.array.themes_overlay_names)
                        ).toMap().toPersistentMap().mutate { map ->
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) { // remove monet on android 11 and less
                                map.remove("Monet")
                                map.remove("Monet2")
                            }
                        },
                        onValueChanged = { newValue ->
                            settings.ui.primaryColor.set(newValue) // We need to set before we recreate
                            activity?.recreate()
                            return@ListPreference true
                        }),
                    Preference.PreferenceItem.ListPreference(
                        preference = settings.ui.theme,
                        icon = painterResource(R.drawable.palette_24px),
                        title = stringResource(R.string.app_theme_settings),
                        entries = stringArrayResource(R.array.themes_names_values).zip(
                            stringArrayResource(R.array.themes_names)
                        ).toMap().toPersistentMap().mutate { map ->
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) { // remove monet on android 11 and less
                                map.remove("Monet")
                            }
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) { // Remove system on android 9 and less
                                map.remove("System")
                            }
                        },
                        onValueChanged = { newValue ->
                            settings.ui.theme.set(newValue) // We need to set before we recreate
                            activity?.recreate()
                            return@ListPreference true
                        }),
                    Preference.PreferenceItem.ListPreference(
                        preference = settings.ui.layout,
                        icon = painterResource(R.drawable.responsive_layout_24px),
                        title = stringResource(R.string.app_layout),
                        entries = integerArrayResource(R.array.app_layout_values).zip(
                            stringArrayResource(R.array.app_layout)
                        ).toMap().toPersistentMap(),
                        onValueChanged = { newValue ->
                            settings.ui.layout.set(newValue) // We need to set before we recreate
                            activity?.updateTv()
                            activity?.recreate()
                            return@ListPreference true
                        }),
                )
            ),

            Preference.PreferenceGroup(
                title = stringResource(R.string.pref_category_ui_features),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.SliderPreference(
                        value = overscanDp,
                        title = stringResource(R.string.overscan_settings),
                        subtitle = stringResource(R.string.overscan_settings_des),
                        valueRange = 0..100,
                        icon = painterResource(R.drawable.arrows_input_24px),
                        enabled = isLayout(TV),
                        onValueChanged = { newValue ->
                            settings.ui.overscanDp.set(newValue)
                            val padding = newValue.toPx
                            (activity as? MainActivity)?.binding?.homeRoot?.setPadding(
                                padding, padding, padding, padding
                            )
                        }),

                    Preference.PreferenceItem.SwitchPreference(
                        preference = settings.ui.advancedSearch,
                        title = stringResource(R.string.advanced_search),
                        subtitle = stringResource(R.string.advanced_search_des),
                        icon = painterResource(R.drawable.search_icon)
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = settings.ui.searchSuggestions,
                        title = stringResource(R.string.search_suggestions),
                        subtitle = stringResource(R.string.search_suggestions_des),
                        icon = painterResource(R.drawable.tooltip_24px)
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = settings.ui.trailersEnabled,
                        title = stringResource(R.string.show_trailers_settings),
                        icon = painterResource(R.drawable.baseline_theaters_24)
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = settings.ui.kitsuPostersEnabled,
                        title = stringResource(R.string.kitsu_settings),
                        icon = painterResource(R.drawable.kitsu_icon)
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = settings.ui.castEnabled,
                        title = stringResource(R.string.show_cast_in_details),
                        icon = painterResource(R.drawable.face_24px)
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = settings.ui.fillersEnabled,
                        title = stringResource(R.string.show_fillers_settings),
                        icon = painterResource(R.drawable.skip_next_24px)
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = settings.ui.showMetadataOverlay,
                        title = stringResource(R.string.show_player_metadata_overlay),
                        icon = painterResource(R.drawable.metadata_overlay_icon)
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = settings.ui.showClock,
                        title = stringResource(R.string.tv_layout_clock_settings),
                        subtitle = stringResource(R.string.tv_layout_clock_settings_des),
                        icon = painterResource(R.drawable.ic_baseline_clock_24),
                        enabled = isLayout(TV),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = settings.ui.randomButtonEnabled,
                        title = stringResource(R.string.random_button_settings),
                        subtitle = stringResource(R.string.random_button_settings_desc),
                        icon = painterResource(R.drawable.shuffle_24px)
                    ),
                    Preference.PreferenceItem.ListPreference(
                        preference = settings.ui.confirmExit,
                        title = stringResource(R.string.confirm_before_exiting_title),
                        subtitle = stringResource(R.string.confirm_before_exiting_desc),
                        icon = painterResource(R.drawable.ic_baseline_exit_24),
                        entries = integerArrayResource(R.array.confirm_exit_values).zip(
                            stringArrayResource(R.array.confirm_exit)
                        ).toMap()
                    ),
                )
            ),

            Preference.PreferenceGroup(
                title = stringResource(R.string.search_poster_img_des),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.MultiSelectListPreference(
                        preference = settings.ui.filterQuality,
                        title = stringResource(R.string.pref_filter_search_quality),
                        icon = painterResource(R.drawable.filter_alt_24px),
                        entries = SearchQuality.entries.associateWith { stringResource(it.toStringRes()) }),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = settings.ui.bottomTitle,
                        title = stringResource(R.string.bottom_title_settings),
                        subtitle = stringResource(R.string.bottom_title_settings_des),
                        icon = painterResource(R.drawable.title_24px)
                    ),
                    Preference.PreferenceItem.SliderPreference(
                        value = posterSize,
                        title = stringResource(R.string.poster_size_settings),
                        subtitle = stringResource(R.string.poster_size_settings_des),
                        valueRange = 0..15,
                        icon = painterResource(R.drawable.baseline_grid_view_24),
                        onValueChanged = { newValue ->
                            HomeChildItemAdapter.sharedPool.clear()
                            ParentItemAdapter.sharedPool.clear()
                            SearchAdapter.sharedPool.clear()
                            activity?.let { HomeChildItemAdapter.updatePosterSize(it, newValue) }
                            settings.ui.posterSize.set(newValue)
                        }),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = settings.ui.posterShowRating,
                        title = stringResource(R.string.show_rating),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = settings.ui.posterShowEpisode,
                        title = stringResource(R.string.show_episode_text),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = settings.ui.posterShowTitle,
                        title = stringResource(R.string.show_title),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = settings.ui.posterShowHd,
                        title = stringResource(R.string.show_hd),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = settings.ui.posterShowDub,
                        title = stringResource(R.string.show_dub),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = settings.ui.posterShowSub,
                        title = stringResource(R.string.show_sub),
                    ),
                )
            )
        )
    }
}