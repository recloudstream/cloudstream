package com.lagradost.cloudstream3.ui.settings

import android.text.format.Formatter.formatShortFileSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.integerArrayResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import com.lagradost.cloudstream3.CommonActivity.activity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.actions.VideoClickActionHolder
import com.lagradost.cloudstream3.ui.player.source_priority.QualityProfileDialog
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.getFolderSize
import com.lagradost.cloudstream3.ui.subtitles.ChromecastSubtitlesFragment
import com.lagradost.cloudstream3.ui.subtitles.SubtitlesFragment
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream4.compose.EMULATOR
import com.lagradost.cloudstream4.compose.PHONE
import com.lagradost.cloudstream4.compose.TV
import com.lagradost.cloudstream4.compose.isLayout
import com.lagradost.cloudstream4.rememberAppSettings
import com.mihon.presentation.settings.Preference
import com.mihon.presentation.settings.SearchableSettings
import com.mihon.presentation.settings.collectAsState
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.reflect.jvm.jvmName

class SettingsPlayerScreen : SearchableSettings {
    @Composable
    override fun getTitleRes(): String = stringResource(R.string.category_player)

    @Composable
    override fun getPreferences(): List<Preference> {
        val settings = rememberAppSettings()

        val context = LocalContext.current
        val defaultPlayerName = stringResource(R.string.player_settings_play_in_app)
        val players = remember(context) {
            mapOf("" to defaultPlayerName) + VideoClickActionHolder.getPlayers()
                .associate { player ->
                    player.uniqueId() to (player.name.asStringNull(context)
                        ?: player::class.simpleName ?: player::class.jvmName)
                }
        }

        val playerSeekTime by settings.player.doubleTapTime.collectAsState()
        val tvSeekOnTime by settings.player.tvSeekOnTime.collectAsState()
        val tvSeekOffTime by settings.player.tvSeekOffTime.collectAsState()

        var cacheSize by remember { mutableLongStateOf(0L) }
        var cacheCleared by remember { mutableIntStateOf(0) }
        val cacheDir = LocalContext.current.cacheDir
        LaunchedEffect(cacheCleared) {
            withContext(Dispatchers.IO) {
                cacheSize = getFolderSize(cacheDir)
            }
        }

        return persistentListOf(
            Preference.PreferenceGroup(
                title = stringResource(R.string.pref_category_subtitles),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(R.string.player_subtitles_settings),
                        subtitle = stringResource(R.string.player_subtitles_settings_des),
                        icon = painterResource(R.drawable.subtitles_gear_24px),
                        onClick = {
                            SubtitlesFragment.push(activity, false)
                        }),
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(R.string.chromecast_subtitles_settings),
                        subtitle = stringResource(R.string.chromecast_subtitles_settings_des),
                        icon = painterResource(R.drawable.cast),
                        onClick = {
                            ChromecastSubtitlesFragment.push(activity, false)
                        }),
                ),
            ),
            Preference.PreferenceGroup(
                title = stringResource(R.string.pref_category_player_features),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.ListPreference(
                        title = stringResource(R.string.player_pref),
                        icon = painterResource(R.drawable.play_arrow_24px),
                        preference = settings.player.defaultPlayer,
                        entries = players,
                    ),

                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(R.string.source_priority),
                        subtitle = stringResource(R.string.source_priority_help),
                        icon = painterResource(R.drawable.ic_baseline_people_24),
                        onClick = {
                            ioSafe {
                                val defaultSources = QualityProfileDialog.getAllDefaultSources()
                                val activity = activity ?: return@ioSafe
                                activity.runOnUiThread {
                                    QualityProfileDialog(
                                        activity,
                                        R.style.DialogFullscreenPlayer,
                                        defaultSources,
                                    ).show()
                                }
                            }
                        }),

                    Preference.PreferenceItem.SwitchPreference(
                        preference = settings.player.pipEnabled,
                        title = stringResource(R.string.picture_in_picture),
                        subtitle = stringResource(R.string.picture_in_picture_des),
                        icon = painterResource(R.drawable.pip_24px),
                    ),

                    Preference.PreferenceItem.SwitchPreference(
                        preference = settings.player.resizeEnabled,
                        title = stringResource(R.string.player_size_settings),
                        subtitle = stringResource(R.string.player_size_settings_des),
                        icon = painterResource(R.drawable.aspect_ratio_24px),
                    ),

                    Preference.PreferenceItem.SwitchPreference(
                        preference = settings.player.speedEnabled,
                        title = stringResource(R.string.eigengraumode_settings),
                        subtitle = stringResource(R.string.speed_setting_summary),
                        icon = painterResource(R.drawable.ic_baseline_speed_24),
                    ),

                    Preference.PreferenceItem.SwitchPreference(
                        preference = settings.player.tiktokEnabled,
                        title = stringResource(R.string.speedup_title),
                        subtitle = stringResource(R.string.speedup_summary),
                        icon = painterResource(R.drawable.speedup),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = settings.player.autoPlayEnabled,
                        title = stringResource(R.string.autoplay_next_settings),
                        subtitle = stringResource(R.string.autoplay_next_settings_des),
                        icon = painterResource(R.drawable.skip_next_24px),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = settings.player.skipOpEnabled,
                        title = stringResource(R.string.video_skip_op),
                        subtitle = stringResource(R.string.enable_skip_op_from_database_des),
                        icon = painterResource(R.drawable.keyboard_double_arrow_right_24px),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = settings.player.autoRotateEnabled,
                        title = stringResource(R.string.auto_rotate_video),
                        subtitle = stringResource(R.string.auto_rotate_video_desc),
                        icon = painterResource(R.drawable.screen_rotation_alt_24px),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = settings.player.rotateButtonEnabled,
                        title = stringResource(R.string.rotate_video),
                        subtitle = stringResource(R.string.rotate_video_desc),
                        icon = painterResource(R.drawable.screen_rotation),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = settings.player.previewBarEnabled,
                        enabled = isLayout(PHONE or EMULATOR),
                        title = stringResource(R.string.preview_seekbar),
                        subtitle = stringResource(R.string.preview_seekbar_desc),
                        icon = painterResource(R.drawable.picture_in_picture_center_24px),
                    ),
                    Preference.PreferenceItem.ListPreference(
                        preference = settings.player.softwareDecoding,
                        title = stringResource(R.string.software_decoding),
                        subtitle = stringResource(R.string.software_decoding_desc),
                        icon = painterResource(R.drawable.memory_24px),
                        entries = integerArrayResource(R.array.software_decoding_switch_values).zip(
                            stringArrayResource(R.array.software_decoding_switch)
                        ).toMap()
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = settings.player.extraBrightnessEnabled,
                        title = stringResource(R.string.extra_brightness_settings),
                        subtitle = stringResource(R.string.extra_brightness_settings_des),
                        icon = painterResource(R.drawable.light_mode_24px),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = settings.player.episodeSync,
                        title = stringResource(R.string.episode_sync_settings),
                        subtitle = stringResource(R.string.episode_sync_settings_des),
                        icon = painterResource(R.drawable.autorenew_24px)
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = stringResource(R.string.pref_category_gestures),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.SwitchPreference(
                        preference = settings.player.swipeHorizontalEnabled,
                        title = stringResource(R.string.swipe_to_seek_settings),
                        subtitle = stringResource(R.string.swipe_to_seek_settings_des),
                        icon = painterResource(R.drawable.swipe_24px),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = settings.player.swipeVerticalEnabled,
                        title = stringResource(R.string.swipe_to_change_settings),
                        subtitle = stringResource(R.string.swipe_to_change_settings_des),
                        icon = painterResource(R.drawable.swipe_vertical_24px),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = settings.player.doubleTapToSeekEnabled,
                        title = stringResource(R.string.double_tap_to_seek_settings),
                        subtitle = stringResource(R.string.double_tap_to_seek_settings_des),
                        icon = painterResource(R.drawable.touch_double_24px),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = settings.player.doubleTapToPauseEnabled,
                        title = stringResource(R.string.double_tap_to_pause_settings),
                        subtitle = stringResource(R.string.double_tap_to_pause_settings_des),
                        icon = painterResource(R.drawable.touch_double_24px),
                    ),

                    Preference.PreferenceItem.SliderPreference(
                        value = playerSeekTime,
                        title = stringResource(R.string.double_tap_to_seek_amount_settings),
                        icon = painterResource(R.drawable.go_forward_30),
                        valueRange = 5..60,
                        steps = 10,
                        onValueChanged = settings.player.doubleTapTime::set
                    ),
                )
            ),
            Preference.PreferenceGroup(
                title = stringResource(R.string.pref_category_android_tv),
                enabled = isLayout(TV or EMULATOR),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.SliderPreference(
                        value = tvSeekOnTime,
                        title = stringResource(R.string.android_tv_interface_on_seek_settings),
                        subtitle = stringResource(R.string.android_tv_interface_on_seek_settings_summary),
                        icon = painterResource(R.drawable.go_forward_30),
                        valueRange = 5..60,
                        steps = 10,
                        onValueChanged = settings.player.tvSeekOnTime::set
                    ),
                    Preference.PreferenceItem.SliderPreference(
                        value = tvSeekOffTime,
                        title = stringResource(R.string.android_tv_interface_off_seek_settings),
                        subtitle = stringResource(R.string.android_tv_interface_off_seek_settings_summary),
                        icon = painterResource(R.drawable.go_forward_30),
                        valueRange = 5..60,
                        steps = 10,
                        onValueChanged = settings.player.tvSeekOffTime::set
                    ),
                )
            ),
            Preference.PreferenceGroup(
                title = stringResource(R.string.pref_category_player_layout),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.ListPreference(
                        preference = settings.player.limitPlayerTitle,
                        title = stringResource(R.string.limit_title),
                        icon = painterResource(R.drawable.match_word_24px),
                        entries = integerArrayResource(R.array.limit_title_pref_values).zip(
                            stringArrayResource(R.array.limit_title_pref_names)
                        ).toMap()
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        enabled = isLayout(PHONE or EMULATOR),
                        preference = settings.player.hidePlayerControlNames,
                        icon = painterResource(R.drawable.visibility_off_24px),
                        title = stringResource(R.string.hide_player_control_names),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = settings.player.showName,
                        icon = painterResource(R.drawable.label_24px),
                        title = stringResource(R.string.source_name),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = settings.player.showMediaInfo,
                        icon = painterResource(R.drawable.movie_info_24px),
                        title = stringResource(R.string.video_info),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = settings.player.showResolution,
                        title = stringResource(R.string.resolution),
                        icon = painterResource(R.drawable.high_res_24px),
                    ),

                    // Unsure if we want to have MultiSelectListPreference or boolean
                    /*Preference.PreferenceItem.MultiSelectListPreference(
                        preference = settings.player.showPlayerInfo,
                        title = stringResource(R.string.limit_title_rez),
                        icon = painterResource(R.drawable.ic_baseline_text_format_24),
                        entries = persistentMapOf(
                            ShowPlayerInfo.Name to stringResource(R.string.source_name),
                            ShowPlayerInfo.Resolution to stringResource(R.string.resolution),
                            ShowPlayerInfo.VideoInfo to stringResource(R.string.video_info),
                        )
                    ), */
                )
            ),
            Preference.PreferenceGroup(
                title = stringResource(R.string.pref_category_cache),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.ListPreference(
                        preference = settings.player.bufferDiskMB,
                        title = stringResource(R.string.video_buffer_disk_settings),
                        subtitle = "%s\n" + stringResource(R.string.video_disk_description),
                        icon = painterResource(R.drawable.hard_drive_24px),
                        entries = integerArrayResource(R.array.video_buffer_size_values).zip(
                            stringArrayResource(R.array.video_buffer_size_names)
                        ).toMap()
                    ),

                    Preference.PreferenceItem.ListPreference(
                        preference = settings.player.bufferRamMB,
                        title = stringResource(R.string.video_buffer_size_settings),
                        subtitle = "%s\n" + stringResource(R.string.video_ram_description),
                        icon = painterResource(R.drawable.memory_alt_24px),
                        entries = integerArrayResource(R.array.video_buffer_size_values).zip(
                            stringArrayResource(R.array.video_buffer_size_names)
                        ).toMap()
                    ),

                    Preference.PreferenceItem.ListPreference(
                        preference = settings.player.bufferTimeSec,
                        title = stringResource(R.string.video_buffer_length_settings),
                        subtitle = "%s\n" + stringResource(R.string.video_ram_description),
                        icon = painterResource(R.drawable.history_toggle_off_24px),
                        entries = integerArrayResource(R.array.video_buffer_length_values).zip(
                            stringArrayResource(R.array.video_buffer_length_names)
                        ).toMap()
                    ),

                    Preference.PreferenceItem.TextPreference(
                        icon = painterResource(R.drawable.ic_baseline_delete_outline_24),
                        title = stringResource(R.string.video_buffer_clear_settings),
                        subtitle = formatShortFileSize(LocalContext.current, cacheSize),
                        onClick = {
                            ioSafe {
                                cacheDir.deleteRecursively()
                                cacheCleared += 1
                            }
                        })
                )
            ),

            )
    }
}