package com.lagradost.cloudstream3.ui.settings

import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import androidx.preference.SeekBarPreference
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getActivity
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.ui.BasePreferenceFragmentCompat
import com.lagradost.cloudstream3.ui.clear
import com.lagradost.cloudstream3.ui.home.HomeChildItemAdapter
import com.lagradost.cloudstream3.ui.home.ParentItemAdapter
import com.lagradost.cloudstream3.ui.search.SearchAdapter
import com.lagradost.cloudstream3.ui.search.SearchResultBuilder
import com.lagradost.cloudstream3.ui.settings.Globals.EMULATOR
import com.lagradost.cloudstream3.ui.settings.Globals.PHONE
import com.lagradost.cloudstream3.ui.settings.Globals.updateTv
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.getPref
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.hideOn
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.setPaddingBottom
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.setToolBarScrollFlags
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.setUpToolbar
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showBottomDialog
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showDialog
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showMultiDialog
import com.lagradost.cloudstream3.utils.TvModeHelper
import com.lagradost.cloudstream3.utils.UIHelper.hideKeyboard
import com.lagradost.cloudstream3.utils.UIHelper.toPx

class SettingsUI : BasePreferenceFragmentCompat() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setUpToolbar(R.string.category_ui)
        setPaddingBottom()
        setToolBarScrollFlags()
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        hideKeyboard()
        setPreferencesFromResource(R.xml.settings_ui, rootKey)
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val homeQuickActionPref = getPref(R.string.home_quick_action_key)
        val tvModeTogglePref = getPref(R.string.tv_mode_button_key)
        val tvModeContentPref = getPref(R.string.tv_mode_content_key)
        val tvModeDubPref = getPref(R.string.tv_mode_dub_preference_key)
        val tvModeSeasonPref = getPref(R.string.tv_mode_season_scope_key)
        val tvModePlayerStartPref = getPref(R.string.tv_mode_player_start_key)
        val tvModeLoopPref = getPref(R.string.tv_mode_loop_key)
        val tvModeContinueWatchingPref = getPref(R.string.tv_mode_continue_watching_key)
        val tvModeStallProtectionPref = getPref(R.string.tv_mode_stall_protection_key)
        val tvModeRetryLimitPref = getPref(R.string.tv_mode_retry_limit_key)
        val tvModeLoadingTimeoutPref = getPref(R.string.tv_mode_loading_timeout_key)

        fun getTvModeContentMode(): TvModeHelper.TvModeContentMode {
            return TvModeHelper.TvModeContentMode.fromValue(
                settingsManager.getInt(
                    getString(R.string.tv_mode_content_key),
                    TvModeHelper.TvModeContentMode.BOTH.value
                )
            )
        }

        fun getTvModeDubPreference(): TvModeHelper.TvModeDubPreference {
            return TvModeHelper.TvModeDubPreference.fromValue(
                settingsManager.getInt(
                    getString(R.string.tv_mode_dub_preference_key),
                    TvModeHelper.TvModeDubPreference.PREFER_DUBBED.value
                )
            )
        }

        fun getTvModeSeasonMode(): TvModeHelper.TvModeSeasonMode {
            return TvModeHelper.TvModeSeasonMode.fromValue(
                settingsManager.getInt(
                    getString(R.string.tv_mode_season_scope_key),
                    TvModeHelper.TvModeSeasonMode.SELECTED_SEASON_ONLY.value
                )
            )
        }

        fun getHomeQuickActionMode(): TvModeHelper.HomeQuickActionMode {
            return TvModeHelper.getHomeQuickActionMode(requireContext())
        }

        fun getTvModePlayerStartMode(): TvModeHelper.TvModePlayerStartMode {
            return TvModeHelper.getPlayerStartMode(requireContext())
        }

        fun isTvModeStallProtectionEnabled(): Boolean {
            return TvModeHelper.isStallProtectionEnabled(requireContext())
        }

        fun updateTvModePreferenceState(
            isEnabledOverride: Boolean? = null,
            stallProtectionEnabledOverride: Boolean? = null,
        ) {
            val isEnabled = isEnabledOverride ?: settingsManager.getBoolean(
                getString(R.string.tv_mode_button_key),
                false
            )
            homeQuickActionPref?.summary = getString(getHomeQuickActionMode().labelRes)
            tvModeContentPref?.isVisible = isEnabled
            tvModeDubPref?.isVisible = isEnabled
            tvModeSeasonPref?.isVisible = isEnabled
            tvModePlayerStartPref?.isVisible = isEnabled
            tvModeLoopPref?.isVisible = isEnabled
            tvModeContinueWatchingPref?.isVisible = isEnabled
            tvModeStallProtectionPref?.isVisible = isEnabled
            val showStallProtectionDetails = if (isEnabled) {
                stallProtectionEnabledOverride ?: isTvModeStallProtectionEnabled()
            } else {
                false
            }
            tvModeRetryLimitPref?.isVisible = showStallProtectionDetails
            tvModeLoadingTimeoutPref?.isVisible = showStallProtectionDetails
            tvModeContentPref?.summary = getString(getTvModeContentMode().labelRes)
            tvModeDubPref?.summary = getString(getTvModeDubPreference().labelRes)
            tvModeSeasonPref?.summary = getString(getTvModeSeasonMode().labelRes)
            tvModePlayerStartPref?.summary = getString(getTvModePlayerStartMode().labelRes)
        }

        (getPref(R.string.overscan_key)?.hideOn(PHONE or EMULATOR) as? SeekBarPreference)?.setOnPreferenceChangeListener { pref, newValue ->
            val padding = (newValue as? Int)?.toPx ?: return@setOnPreferenceChangeListener true
            (pref.context.getActivity() as? MainActivity)?.binding?.homeRoot?.setPadding(padding, padding, padding, padding)
            return@setOnPreferenceChangeListener true
        }

        getPref(R.string.bottom_title_key)?.setOnPreferenceChangeListener { _, _ ->
            HomeChildItemAdapter.sharedPool.clear()
            ParentItemAdapter.sharedPool.clear()
            SearchAdapter.sharedPool.clear()
            true
        }

        getPref(R.string.poster_size_key)?.setOnPreferenceChangeListener { _, newValue ->
            HomeChildItemAdapter.sharedPool.clear()
            ParentItemAdapter.sharedPool.clear()
            SearchAdapter.sharedPool.clear()
            context?.let { HomeChildItemAdapter.updatePosterSize(it, newValue as? Int) }
            true
        }

        getPref(R.string.poster_ui_key)?.setOnPreferenceClickListener {
            val prefNames = resources.getStringArray(R.array.poster_ui_options)
            val keys = resources.getStringArray(R.array.poster_ui_options_values)
            val prefValues = keys.map {
                settingsManager.getBoolean(it, true)
            }.mapIndexedNotNull { index, b ->
                if (b) {
                    index
                } else null
            }

            activity?.showMultiDialog(
                prefNames.toList(),
                prefValues,
                getString(R.string.poster_ui_settings),
                {}
            ) { list ->
                settingsManager.edit {
                    for ((i, key) in keys.withIndex()) {
                        putBoolean(key, list.contains(i))
                    }
                }
                SearchResultBuilder.updateCache(it.context)
            }

            return@setOnPreferenceClickListener true
        }

        getPref(R.string.app_layout_key)?.setOnPreferenceClickListener {
            val prefNames = resources.getStringArray(R.array.app_layout)
            val prefValues = resources.getIntArray(R.array.app_layout_values)

            val currentLayout =
                settingsManager.getInt(getString(R.string.app_layout_key), -1)

            activity?.showBottomDialog(
                items = prefNames.toList(),
                selectedIndex = prefValues.indexOf(currentLayout),
                name = getString(R.string.app_layout),
                showApply = true,
                dismissCallback = {},
                callback = {
                    try {
                        settingsManager.edit {
                            putInt(getString(R.string.app_layout_key), prefValues[it])
                        }
                        context?.updateTv()
                        activity?.recreate()
                    } catch (e: Exception) {
                        logError(e)
                    }
                }
            )
            return@setOnPreferenceClickListener true
        }

        getPref(R.string.app_theme_key)?.setOnPreferenceClickListener {
            val prefNames = resources.getStringArray(R.array.themes_names).toMutableList()
            val prefValues = resources.getStringArray(R.array.themes_names_values).toMutableList()
            val removeIncompatible = { text: String ->
                val toRemove = prefValues
                    .mapIndexed { idx, s -> if (s.startsWith(text)) idx else null }
                    .filterNotNull()
                var offset = 0
                toRemove.forEach { idx ->
                    prefNames.removeAt(idx - offset)
                    prefValues.removeAt(idx - offset)
                    offset += 1
                }
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) { // remove monet on android 11 and less
                removeIncompatible("Monet")
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) { // Remove system on android 9 and less
                removeIncompatible("System")
            }

            val currentLayout =
                settingsManager.getString(getString(R.string.app_theme_key), prefValues.first())

            activity?.showBottomDialog(
                prefNames.toList(),
                prefValues.indexOf(currentLayout),
                getString(R.string.app_theme_settings),
                true,
                {}
            ) {
                try {
                    settingsManager.edit {
                        putString(getString(R.string.app_theme_key), prefValues[it])
                    }
                    activity?.recreate()
                } catch (e: Exception) {
                    logError(e)
                }
            }
            return@setOnPreferenceClickListener true
        }
        getPref(R.string.primary_color_key)?.setOnPreferenceClickListener {
            val prefNames = resources.getStringArray(R.array.themes_overlay_names).toMutableList()
            val prefValues =
                resources.getStringArray(R.array.themes_overlay_names_values).toMutableList()

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) { // remove monet on android 11 and less
                val toRemove = prefValues
                    .mapIndexed { idx, s -> if (s.startsWith("Monet")) idx else null }
                    .filterNotNull()
                var offset = 0
                toRemove.forEach { idx ->
                    prefNames.removeAt(idx - offset)
                    prefValues.removeAt(idx - offset)
                    offset += 1
                }
            }

            val currentLayout =
                settingsManager.getString(getString(R.string.primary_color_key), prefValues.first())

            activity?.showDialog(
                prefNames.toList(),
                prefValues.indexOf(currentLayout),
                getString(R.string.primary_color_settings),
                true,
                {}
            ) {
                try {
                    settingsManager.edit {
                        putString(getString(R.string.primary_color_key), prefValues[it])
                    }
                    activity?.recreate()
                } catch (e: Exception) {
                    logError(e)
                }
            }
            return@setOnPreferenceClickListener true
        }

        getPref(R.string.pref_filter_search_quality_key)?.setOnPreferenceClickListener {
            val names = enumValues<SearchQuality>().sorted().map { it.name }
            val currentList = settingsManager.getStringSet(
                getString(R.string.pref_filter_search_quality_key),
                setOf()
            )?.map {
                it.toInt()
            } ?: listOf()

            activity?.showMultiDialog(
                names,
                currentList,
                getString(R.string.pref_filter_search_quality),
                {}
            ) { selectedList ->
                settingsManager.edit {
                    putStringSet(
                        getString(R.string.pref_filter_search_quality_key),
                        selectedList.map { it.toString() }.toMutableSet()
                    )
                }
            }

            return@setOnPreferenceClickListener true
        }

        getPref(R.string.confirm_exit_key)?.setOnPreferenceClickListener {
            val prefNames = resources.getStringArray(R.array.confirm_exit)
            val prefValues = resources.getIntArray(R.array.confirm_exit_values)
            val confirmExit = settingsManager.getInt(getString(R.string.confirm_exit_key), -1)

            activity?.showBottomDialog(
                items = prefNames.toList(),
                selectedIndex = prefValues.indexOf(confirmExit),
                name = getString(R.string.confirm_before_exiting_title),
                showApply = true,
                dismissCallback = {},
                callback = { selectedOption ->
                    settingsManager.edit {
                        putInt(getString(R.string.confirm_exit_key), prefValues[selectedOption])
                    }
                }
            )
            return@setOnPreferenceClickListener true
        }

        homeQuickActionPref?.setOnPreferenceClickListener {
            val modes = TvModeHelper.HomeQuickActionMode.entries
            val selectedMode = getHomeQuickActionMode()

            activity?.showBottomDialog(
                items = modes.map { getString(it.labelRes) },
                selectedIndex = modes.indexOf(selectedMode),
                name = getString(R.string.home_quick_action_settings),
                showApply = true,
                dismissCallback = {},
                callback = { selectedIndex ->
                    val chosenMode = modes[selectedIndex]
                    var enabledOverride: Boolean? = null
                    settingsManager.edit {
                        putInt(getString(R.string.home_quick_action_key), chosenMode.value)
                        if (chosenMode == TvModeHelper.HomeQuickActionMode.TV_MODE &&
                            !settingsManager.getBoolean(getString(R.string.tv_mode_button_key), false)
                        ) {
                            putBoolean(getString(R.string.tv_mode_button_key), true)
                            enabledOverride = true
                        }
                    }
                    updateTvModePreferenceState(enabledOverride)
                }
            )
            true
        }

        tvModeTogglePref?.setOnPreferenceChangeListener { _, newValue ->
            if (newValue == false) {
                TvModeHelper.stopSession()
            }
            updateTvModePreferenceState(newValue as? Boolean)
            true
        }

        tvModeStallProtectionPref?.setOnPreferenceChangeListener { _, newValue ->
            updateTvModePreferenceState(
                stallProtectionEnabledOverride = newValue as? Boolean
            )
            true
        }

        tvModeContentPref?.setOnPreferenceClickListener {
            val modes = TvModeHelper.TvModeContentMode.entries
            val selectedMode = getTvModeContentMode()

            activity?.showBottomDialog(
                items = modes.map { getString(it.labelRes) },
                selectedIndex = modes.indexOf(selectedMode),
                name = getString(R.string.tv_mode_content_settings),
                showApply = true,
                dismissCallback = {},
                callback = { selectedIndex ->
                    settingsManager.edit {
                        putInt(
                            getString(R.string.tv_mode_content_key),
                            modes[selectedIndex].value
                        )
                    }
                    updateTvModePreferenceState()
                }
            )
            true
        }

        tvModeDubPref?.setOnPreferenceClickListener {
            val preferences = TvModeHelper.TvModeDubPreference.entries
            val selectedPreference = getTvModeDubPreference()

            activity?.showBottomDialog(
                items = preferences.map { getString(it.labelRes) },
                selectedIndex = preferences.indexOf(selectedPreference),
                name = getString(R.string.tv_mode_dub_settings),
                showApply = true,
                dismissCallback = {},
                callback = { selectedIndex ->
                    settingsManager.edit {
                        putInt(
                            getString(R.string.tv_mode_dub_preference_key),
                            preferences[selectedIndex].value
                        )
                    }
                    updateTvModePreferenceState()
                }
            )
            true
        }

        tvModeSeasonPref?.setOnPreferenceClickListener {
            val seasonModes = TvModeHelper.TvModeSeasonMode.entries
            val selectedMode = getTvModeSeasonMode()

            activity?.showBottomDialog(
                items = seasonModes.map { getString(it.labelRes) },
                selectedIndex = seasonModes.indexOf(selectedMode),
                name = getString(R.string.tv_mode_season_settings),
                showApply = true,
                dismissCallback = {},
                callback = { selectedIndex ->
                    settingsManager.edit {
                        putInt(
                            getString(R.string.tv_mode_season_scope_key),
                            seasonModes[selectedIndex].value
                        )
                    }
                    updateTvModePreferenceState()
                }
            )
            true
        }

        tvModePlayerStartPref?.setOnPreferenceClickListener {
            val startModes = TvModeHelper.TvModePlayerStartMode.entries
            val selectedMode = getTvModePlayerStartMode()

            activity?.showBottomDialog(
                items = startModes.map { getString(it.labelRes) },
                selectedIndex = startModes.indexOf(selectedMode),
                name = getString(R.string.tv_mode_player_start_settings),
                showApply = true,
                dismissCallback = {},
                callback = { selectedIndex ->
                    settingsManager.edit {
                        putInt(
                            getString(R.string.tv_mode_player_start_key),
                            startModes[selectedIndex].value
                        )
                    }
                    updateTvModePreferenceState()
                }
            )
            true
        }

        updateTvModePreferenceState()
    }
}
