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
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.SingleSelectionHelper
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showBottomDialog
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showDialog
import com.lagradost.cloudstream3.utils.UIHelper.hideKeyboard
import kotlin.concurrent.thread


class SettingsFragment : PreferenceFragmentCompat() {
    private var beneneCount = 0

    private val languages = arrayListOf(
        Triple("\uD83C\uDDEC\uD83C\uDDE7", "English", "en"),
        Triple("\uD83C\uDDF3\uD83C\uDDF1", "Dutch", "nl"),
        Triple("\uD83C\uDDEB\uD83C\uDDF7", "French", "fr"),
        Triple("\uD83C\uDDEC\uD83C\uDDF7", "Greek", "gr"),
        Triple("\uD83C\uDDF8\uD83C\uDDEA", "Swedish", "sv"),
        Triple("\uD83C\uDDF5\uD83C\uDDED", "Tagalog", "tl"),
        Triple("\uD83C\uDDF5\uD83C\uDDF1", "Polish", "pl"),	
        Triple("\uD83C\uDDEE\uD83C\uDDF3", "Hindi", "hi"),
        Triple("\uD83C\uDDEE\uD83C\uDDF3", "Malayalam", "ml"),
    ) // idk, if you find a way of automating this it would be great

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        hideKeyboard()
        setPreferencesFromResource(R.xml.settings, rootKey)
        val updatePreference = findPreference<Preference>(getString(R.string.manual_check_update_key))!!
        val localePreference = findPreference<Preference>(getString(R.string.locale_key))!!
        val benenePreference = findPreference<Preference>(getString(R.string.benene_count))!!
        val watchQualityPreference = findPreference<Preference>(getString(R.string.quality_pref_key))!!

        watchQualityPreference.setOnPreferenceClickListener {
            val prefNames = resources.getStringArray(R.array.quality_pref)
            val prefValues = resources.getIntArray(R.array.quality_pref_values)
            val settingsManager = PreferenceManager.getDefaultSharedPreferences(context)

            val currentQuality = settingsManager.getInt(getString(R.string.watch_quality_pref), Qualities.values().last().value)
            context?.showBottomDialog(
                prefNames.toList(),
                prefValues.indexOf(currentQuality),
                getString(R.string.watch_quality_pref),
                true,
                {}) {
                settingsManager.edit().putInt(getString(R.string.watch_quality_pref), prefValues[it]).apply()
            }
            return@setOnPreferenceClickListener true
        }

        try {
            val settingsManager = PreferenceManager.getDefaultSharedPreferences(context)

            beneneCount = settingsManager.getInt(getString(R.string.benene_count), 0)

            benenePreference.summary =
                if (beneneCount <= 0) getString(R.string.benene_count_text_none) else getString(R.string.benene_count_text).format(
                    beneneCount
                )
            benenePreference.setOnPreferenceClickListener {
                try {
                    beneneCount++
                    settingsManager.edit().putInt(getString(R.string.benene_count), beneneCount).apply()
                    it.summary = getString(R.string.benene_count_text).format(beneneCount)
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
            val tempLangs = languages
            if (beneneCount > 100) {
                tempLangs.add(Triple("\uD83E\uDD8D", "mmmm... monke", "mo"))
            }
            val current = getCurrentLocale()
            val languageCodes = tempLangs.map { it.third }
            val languageNames = tempLangs.map { "${it.first}  ${it.second}" }
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
            if (preference.key == getString(R.string.subtitle_settings_key)) {
                SubtitlesFragment.push(activity, false)
            }
        }
        return super.onPreferenceTreeClick(preference)
    }
}
