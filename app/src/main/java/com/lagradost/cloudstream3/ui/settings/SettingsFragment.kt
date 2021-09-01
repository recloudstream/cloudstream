package com.lagradost.cloudstream3.ui.settings

import android.os.Bundle
import android.widget.Toast
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.MainActivity.Companion.showToast
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.mvvm.normalSafeApiCall
import com.lagradost.cloudstream3.ui.subtitles.SubtitlesFragment
import com.lagradost.cloudstream3.utils.Coroutines.main
import com.lagradost.cloudstream3.utils.InAppUpdater.Companion.runAutoUpdate
import com.lagradost.cloudstream3.utils.UIHelper.hideKeyboard
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import java.util.*
import kotlin.Exception
import kotlin.concurrent.thread

class SettingsFragment : PreferenceFragmentCompat() {
    var count = 0

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        hideKeyboard()
        setPreferencesFromResource(R.xml.settings, rootKey)
        val updatePrefrence = findPreference<Preference>(getString(R.string.manual_check_update_key))!!

        val benenePref = findPreference<Preference>(getString(R.string.benene_count))!!

        try {
            val settingsManager = PreferenceManager.getDefaultSharedPreferences(context)

            count = settingsManager.getInt(getString(R.string.benene_count), 0)

            benenePref.summary =
                if (count <= 0) getString(R.string.benene_count_text_none) else getString(R.string.benene_count_text).format(
                    count
                )
            benenePref.setOnPreferenceClickListener {
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

        updatePrefrence.setOnPreferenceClickListener {
            thread {
                if (!requireActivity().runAutoUpdate(false)) {
                    activity?.runOnUiThread {
                        showToast(activity, "No Update Found", Toast.LENGTH_SHORT)
                    }
                }
            }
            return@setOnPreferenceClickListener true
        }
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