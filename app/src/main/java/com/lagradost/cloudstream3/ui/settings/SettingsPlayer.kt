package com.lagradost.cloudstream3.ui.settings

import android.os.Bundle
import android.text.format.Formatter.formatShortFileSize
import android.view.View
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.actions.VideoClickActionHolder
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.ui.BasePreferenceFragmentCompat
import com.lagradost.cloudstream3.ui.player.source_priority.QualityProfileDialog
import com.lagradost.cloudstream3.ui.settings.Globals.EMULATOR
import com.lagradost.cloudstream3.ui.settings.Globals.PHONE
import com.lagradost.cloudstream3.ui.settings.Globals.TV
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.getFolderSize
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.getPref
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.hideOn
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.hidePrefs
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.setPaddingBottom
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.setToolBarScrollFlags
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.setUpToolbar
import com.lagradost.cloudstream3.ui.subtitles.ChromecastSubtitlesFragment
import com.lagradost.cloudstream3.ui.subtitles.SubtitlesFragment
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showBottomDialog
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showDialog
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showMultiDialog
import com.lagradost.cloudstream3.utils.UIHelper.hideKeyboard

class SettingsPlayer : BasePreferenceFragmentCompat() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setUpToolbar(R.string.category_player)
        setPaddingBottom()
        setToolBarScrollFlags()
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        hideKeyboard()
        setPreferencesFromResource(R.xml.settings_player, rootKey)
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(requireContext())

        //Hide specific prefs on TV/EMULATOR
        hidePrefs(
            listOf(
                R.string.pref_category_gestures_key,
                R.string.rotate_video_key,
                R.string.auto_rotate_video_key,
                R.string.speedup_key
            ),
            TV or EMULATOR
        )

        getPref(R.string.preview_seekbar_key)?.hideOn(TV)
        getPref(R.string.pref_category_android_tv_key)?.hideOn(PHONE)

        getPref(R.string.video_buffer_length_key)?.setOnPreferenceClickListener {
            val prefNames = resources.getStringArray(R.array.video_buffer_length_names)
            val prefValues = resources.getIntArray(R.array.video_buffer_length_values)

            val currentPrefSize =
                settingsManager.getInt(getString(R.string.video_buffer_length_key), 0)

            activity?.showDialog(
                prefNames.toList(),
                prefValues.indexOf(currentPrefSize),
                getString(R.string.video_buffer_length_settings),
                true,
                {}
            ) {
                settingsManager.edit {
                    putInt(getString(R.string.video_buffer_length_key), prefValues[it])
                }
            }
            return@setOnPreferenceClickListener true
        }

        getPref(R.string.prefer_limit_title_key)?.setOnPreferenceClickListener {
            val prefNames = resources.getStringArray(R.array.limit_title_pref_names)
            val prefValues = resources.getIntArray(R.array.limit_title_pref_values)
            val current = settingsManager.getInt(getString(R.string.prefer_limit_title_key), 0)

            activity?.showBottomDialog(
                prefNames.toList(),
                prefValues.indexOf(current),
                getString(R.string.limit_title),
                true,
                {}
            ) {
                settingsManager.edit {
                    putInt(getString(R.string.prefer_limit_title_key), prefValues[it])
                }
            }
            return@setOnPreferenceClickListener true
        }

        getPref(R.string.software_decoding_key)?.setOnPreferenceClickListener {
            val prefNames = resources.getStringArray(R.array.software_decoding_switch)
            val prefValues = resources.getIntArray(R.array.software_decoding_switch_values)
            val current = settingsManager.getInt(getString(R.string.software_decoding_key), -1)

            activity?.showBottomDialog(
                prefNames.toList(),
                prefValues.indexOf(current),
                getString(R.string.software_decoding),
                true,
                {}
            ) {
                settingsManager.edit {
                    putInt(getString(R.string.software_decoding_key), prefValues[it])
                }
            }
            return@setOnPreferenceClickListener true
        }

        getPref(R.string.prefer_limit_show_player_info)?.setOnPreferenceClickListener {
            val ctx = context ?: return@setOnPreferenceClickListener false

            val prefNames = resources.getStringArray(R.array.title_info_pref_names)
            val keys = resources.getStringArray(R.array.title_info_pref_values)

            // Player defaults
            val playerDefaults = mapOf(
                ctx.getString(R.string.show_name_key) to true,
                ctx.getString(R.string.show_resolution_key) to true,
                ctx.getString(R.string.show_media_info_key) to false
            )

            val selectedIndices = keys.map { key ->
                settingsManager.getBoolean(key, playerDefaults[key] ?: false)
            }.mapIndexedNotNull { index, enabled ->
                if (enabled) index else null
            }

            activity?.showMultiDialog(
                prefNames.toList(),
                selectedIndices,
                getString(R.string.limit_title_rez),
                {}
            ) { selected ->
                settingsManager.edit {
                    for ((index, key) in keys.withIndex()) {
                        putBoolean(key, selected.contains(index))
                    }
                }
            }

            true
        }

        getPref(R.string.hide_player_control_names_key)?.hideOn(TV)

        getPref(R.string.quality_pref_key)?.setOnPreferenceClickListener {
            val prefValues = Qualities.entries.map { it.value }.reversed().toMutableList()
            prefValues.remove(Qualities.Unknown.value)

            val prefNames = prefValues.map { Qualities.getStringByInt(it) }

            val currentQuality =
                settingsManager.getInt(
                    getString(R.string.quality_pref_key),
                    Qualities.entries.last().value
                )

            activity?.showBottomDialog(
                prefNames.toList(),
                prefValues.indexOf(currentQuality),
                getString(R.string.watch_quality_pref),
                true,
                {}
            ) {
                settingsManager.edit {
                    putInt(getString(R.string.quality_pref_key), prefValues[it])
                }
            }
            return@setOnPreferenceClickListener true
        }

        getPref(R.string.quality_pref_mobile_data_key)?.setOnPreferenceClickListener {
            val prefValues = Qualities.entries.map { it.value }.reversed().toMutableList()
            prefValues.remove(Qualities.Unknown.value)

            val prefNames = prefValues.map { Qualities.getStringByInt(it) }

            val currentQuality =
                settingsManager.getInt(
                    getString(R.string.quality_pref_mobile_data_key),
                    Qualities.entries.last().value
                )

            activity?.showBottomDialog(
                prefNames.toList(),
                prefValues.indexOf(currentQuality),
                getString(R.string.watch_quality_pref_data),
                true,
                {}
            ) {
                settingsManager.edit {
                    putInt(getString(R.string.quality_pref_mobile_data_key), prefValues[it])
                }
            }
            return@setOnPreferenceClickListener true
        }

        getPref(R.string.player_default_key)?.setOnPreferenceClickListener {
            val players = VideoClickActionHolder.getPlayers(activity)
            val prefNames = buildList {
                add(getString(R.string.player_settings_play_in_app))
                addAll(players.map { it.name.asStringNull(activity) ?: it.javaClass.simpleName })
            }
            val prefValues = buildList {
                add("")
                addAll(players.map { it.uniqueId() })
            }
            val current =
                settingsManager.getString(getString(R.string.player_default_key), "") ?: ""

            activity?.showBottomDialog(
                prefNames.toList(),
                prefValues.indexOf(current),
                getString(R.string.player_pref),
                true,
                {}
            ) {
                settingsManager.edit {
                    putString(getString(R.string.player_default_key), prefValues[it])
                }
            }
            return@setOnPreferenceClickListener true
        }

        getPref(R.string.subtitle_settings_key)?.setOnPreferenceClickListener {
            SubtitlesFragment.push(activity, false)
            return@setOnPreferenceClickListener true
        }

        getPref(R.string.subtitle_settings_chromecast_key)?.setOnPreferenceClickListener {
            ChromecastSubtitlesFragment.push(activity, false)
            return@setOnPreferenceClickListener true
        }

        getPref(R.string.player_source_priority_key)?.setOnPreferenceClickListener {
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
            return@setOnPreferenceClickListener true
        }

        getPref(R.string.video_buffer_disk_key)?.setOnPreferenceClickListener {
            val prefNames = resources.getStringArray(R.array.video_buffer_size_names)
            val prefValues = resources.getIntArray(R.array.video_buffer_size_values)

            val currentPrefSize =
                settingsManager.getInt(getString(R.string.video_buffer_disk_key), 0)

            activity?.showDialog(
                prefNames.toList(),
                prefValues.indexOf(currentPrefSize),
                getString(R.string.video_buffer_disk_settings),
                true,
                {}
            ) {
                settingsManager.edit {
                    putInt(getString(R.string.video_buffer_disk_key), prefValues[it])
                }
            }
            return@setOnPreferenceClickListener true
        }
        getPref(R.string.video_buffer_size_key)?.setOnPreferenceClickListener {
            val prefNames = resources.getStringArray(R.array.video_buffer_size_names)
            val prefValues = resources.getIntArray(R.array.video_buffer_size_values)

            val currentPrefSize =
                settingsManager.getInt(getString(R.string.video_buffer_size_key), 0)

            activity?.showDialog(
                prefNames.toList(),
                prefValues.indexOf(currentPrefSize),
                getString(R.string.video_buffer_size_settings),
                true,
                {}
            ) {
                settingsManager.edit {
                    putInt(getString(R.string.video_buffer_size_key), prefValues[it])
                }
            }
            return@setOnPreferenceClickListener true
        }

        getPref(R.string.video_buffer_clear_key)?.let { pref ->
            val cacheDir = context?.cacheDir ?: return@let

            fun updateSummary() {
                try {
                    pref.summary = formatShortFileSize(pref.context, getFolderSize(cacheDir))
                } catch (e: Exception) {
                    logError(e)
                }
            }

            updateSummary()

            pref.setOnPreferenceClickListener {
                try {
                    cacheDir.deleteRecursively()
                    updateSummary()
                } catch (e: Exception) {
                    logError(e)
                }
                return@setOnPreferenceClickListener true
            }
        }
    }
}
