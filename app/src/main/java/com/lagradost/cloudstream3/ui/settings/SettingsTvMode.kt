package com.lagradost.cloudstream3.ui.settings

import android.os.Bundle
import android.view.View
import androidx.core.content.edit
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.BasePreferenceFragmentCompat
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.getPref
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.setPaddingBottom
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.setToolBarScrollFlags
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.setUpToolbar
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showBottomDialog
import com.lagradost.cloudstream3.utils.TvModeHelper
import com.lagradost.cloudstream3.utils.UIHelper.hideKeyboard

class SettingsTvMode : BasePreferenceFragmentCompat() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setUpToolbar(R.string.tv_mode_settings)
        setPaddingBottom()
        setToolBarScrollFlags()
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        hideKeyboard()
        setPreferencesFromResource(R.xml.settings_tv_mode, rootKey)

        val ctx = context ?: return
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(ctx)
        val tvModeNoticePref = findPreference<Preference>("tv_mode_activation_notice")
        val tvModeContentPref = getPref(R.string.tv_mode_content_key)
        val tvModeSeasonPref = getPref(R.string.tv_mode_season_scope_key)
        val tvModePlayerStartPref = getPref(R.string.tv_mode_player_start_key)
        val tvModeContinueWatchingPref = getPref(R.string.tv_mode_continue_watching_key)
        val tvModeStallProtectionPref = getPref(R.string.tv_mode_stall_protection_key)
        val tvModeRetryLimitPref = getPref(R.string.tv_mode_retry_limit_key)
        val tvModeLoadingTimeoutPref = getPref(R.string.tv_mode_loading_timeout_key)

        fun getTvModeContentMode(): TvModeHelper.TvModeContentMode {
            return TvModeHelper.TvModeContentMode.fromValue(
                settingsManager.getInt(
                    ctx.getString(R.string.tv_mode_content_key),
                    TvModeHelper.TvModeContentMode.BOTH.value
                )
            )
        }

        fun getTvModeSeasonMode(): TvModeHelper.TvModeSeasonMode {
            return TvModeHelper.TvModeSeasonMode.fromValue(
                settingsManager.getInt(
                    ctx.getString(R.string.tv_mode_season_scope_key),
                    TvModeHelper.TvModeSeasonMode.SELECTED_SEASON_ONLY.value
                )
            )
        }

        fun getTvModePlayerStartMode(): TvModeHelper.TvModePlayerStartMode {
            return TvModeHelper.getPlayerStartMode(ctx)
        }

        fun isTvModeStallProtectionEnabled(): Boolean {
            return TvModeHelper.isStallProtectionEnabled(ctx)
        }

        fun updateTvModePreferenceState(stallProtectionEnabledOverride: Boolean? = null) {
            val isEnabled = TvModeHelper.isEnabled(ctx)
            tvModeNoticePref?.isVisible = !isEnabled
            val showStallProtectionDetails =
                stallProtectionEnabledOverride ?: isTvModeStallProtectionEnabled()
            tvModeRetryLimitPref?.isVisible = showStallProtectionDetails
            tvModeLoadingTimeoutPref?.isVisible = showStallProtectionDetails
            tvModeContentPref?.summary = ctx.getString(getTvModeContentMode().labelRes)
            tvModeSeasonPref?.summary = ctx.getString(getTvModeSeasonMode().labelRes)
            tvModePlayerStartPref?.summary = ctx.getString(getTvModePlayerStartMode().labelRes)
        }

        tvModeStallProtectionPref?.setOnPreferenceChangeListener { _, newValue ->
            updateTvModePreferenceState(
                stallProtectionEnabledOverride = newValue as? Boolean
            )
            true
        }

        tvModeContentPref?.setOnPreferenceClickListener {
            val currentActivity = activity ?: return@setOnPreferenceClickListener false
            val modes = TvModeHelper.TvModeContentMode.entries
            val selectedMode = getTvModeContentMode()

            currentActivity.showBottomDialog(
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

        tvModeSeasonPref?.setOnPreferenceClickListener {
            val currentActivity = activity ?: return@setOnPreferenceClickListener false
            val seasonModes = TvModeHelper.TvModeSeasonMode.entries
            val selectedMode = getTvModeSeasonMode()

            currentActivity.showBottomDialog(
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
            val currentActivity = activity ?: return@setOnPreferenceClickListener false
            val startModes = TvModeHelper.TvModePlayerStartMode.entries
            val selectedMode = getTvModePlayerStartMode()

            currentActivity.showBottomDialog(
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
