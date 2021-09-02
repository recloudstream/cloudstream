package com.lagradost.cloudstream3.ui.settings

import android.os.Bundle
import android.widget.Toast
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.MainActivity.Companion.setLocale
import com.lagradost.cloudstream3.MainActivity.Companion.showToast
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.ui.subtitles.SubtitlesFragment
import com.lagradost.cloudstream3.utils.InAppUpdater.Companion.runAutoUpdate
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showDialog
import com.lagradost.cloudstream3.utils.UIHelper.hideKeyboard
import kotlin.concurrent.thread


class SettingsFragment : PreferenceFragmentCompat() {
    var count = 0

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        hideKeyboard()
        setPreferencesFromResource(R.xml.settings, rootKey)
        val updatePreference = findPreference<Preference>(getString(R.string.manual_check_update_key))!!
        val localePreference = findPreference<Preference>(getString(R.string.locale_key))!!
        val benenePreference = findPreference<Preference>(getString(R.string.benene_count))!!

        try {
            val settingsManager = PreferenceManager.getDefaultSharedPreferences(context)

            count = settingsManager.getInt(getString(R.string.benene_count), 0)

            benenePreference.summary =
                if (count <= 0) getString(R.string.benene_count_text_none) else getString(R.string.benene_count_text).format(
                    count
                )
            benenePreference.setOnPreferenceClickListener {
                try {
                    count++
                    settingsManager.edit().putInt(getString(R.string.benene_count), count).apply()
                    it.summary = getString(R.string.benene_count_text).format(count)
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                return@setOnPreferenceClickListener true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        updatePreference.setOnPreferenceClickListener {
            thread {
                if (!requireActivity().runAutoUpdate(false)) {
                    activity?.runOnUiThread {
                        showToast(activity, R.string.no_update_found, Toast.LENGTH_SHORT)
                    }
                }
            }
            return@setOnPreferenceClickListener true
        }

        localePreference.setOnPreferenceClickListener { pref ->
            val languages = listOf(
                Triple("\uD83C\uDDEC\uD83C\uDDE7", "English", "en"),
                Triple("\uD83C\uDDF3\uD83C\uDDF1", "Dutch", "nl"),
                Triple("\uD83C\uDDEC\uD83C\uDDF7", "Greek", "gr"),
                Triple("\uD83C\uDDF8\uD83C\uDDEA", "Swedish", "sv"),
            ) // idk, if you find a way of automating this it would be great
            val current = getCurrentLocale()
            val languageCodes = languages.map { it.third }
            val languageNames = languages.map { "${it.first}  ${it.second}" }
            val index = languageCodes.indexOf(current)
            pref?.context?.showDialog(
                languageNames, index, getString(R.string.app_language), true, { }
            ) { languageIndex ->
                try {
                    val code = languageCodes[languageIndex]
                    setLocale(activity, code)
                    val settingsManager = PreferenceManager.getDefaultSharedPreferences(pref.context)
                    settingsManager.edit().putString(getString(R.string.locale_key), code).apply()
                    activity?.recreate()
                } catch (e: Exception) {
                    logError(e)
                }
            }
            return@setOnPreferenceClickListener true
        }
    }

    private fun getCurrentLocale(): String {
        val res = context!!.resources
// Change locale settings in the app.
        // val dm = res.displayMetrics
        val conf = res.configuration
        return conf?.locale?.language ?: "en"
    }

    override fun onPreferenceTreeClick(preference: Preference?): Boolean {
        if (preference != null) {
            if (preference.key == "subtitle_settings_key") {
                SubtitlesFragment.push(activity, false)
            }
        }
        return super.onPreferenceTreeClick(preference)
    }
}