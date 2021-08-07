package com.lagradost.cloudstream3.ui.settings

import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.subtitles.SubtitlesFragment
import com.lagradost.cloudstream3.utils.UIHelper.hideKeyboard

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        hideKeyboard()
        setPreferencesFromResource(R.xml.settings, rootKey)
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