package com.lagradost.cloudstream3.ui.settings

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.utils.UIHelper.hideKeyboard

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        hideKeyboard()
        setPreferencesFromResource(R.xml.settings, rootKey)
    }
}