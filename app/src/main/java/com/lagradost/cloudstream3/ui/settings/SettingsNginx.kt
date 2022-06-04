package com.lagradost.cloudstream3.ui.settings

import android.os.Bundle
import android.view.View
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.getPref
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.setUpToolbar
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showNginxTextInputDialog
import com.lagradost.cloudstream3.utils.UIHelper.hideKeyboard

class SettingsNginx : PreferenceFragmentCompat() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setUpToolbar(R.string.category_nginx)
    }
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        hideKeyboard()
        setPreferencesFromResource(R.xml.settings_nginx, rootKey)
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(requireContext())

        getPref(R.string.nginx_credentials)?.setOnPreferenceClickListener {
            activity?.showNginxTextInputDialog(
                settingsManager.getString(
                    getString(R.string.nginx_credentials_title),
                    "Nginx Credentials"
                ).toString(),
                settingsManager.getString(getString(R.string.nginx_credentials), "")
                    .toString(),  // key: the actual you use rn
                android.text.InputType.TYPE_TEXT_VARIATION_URI,
                {}) {
                settingsManager.edit()
                    .putString(getString(R.string.nginx_credentials), it)
                    .apply()  // change the stored url in nginx_url_key to it
            }
            return@setOnPreferenceClickListener true
        }

        getPref(R.string.nginx_url_key)?.setOnPreferenceClickListener {
            activity?.showNginxTextInputDialog(
                settingsManager.getString(getString(R.string.nginx_url_pref), "Nginx server url")
                    .toString(),
                settingsManager.getString(getString(R.string.nginx_url_key), "")
                    .toString(),  // key: the actual you use rn
                android.text.InputType.TYPE_TEXT_VARIATION_URI,  // uri
                {}) {
                settingsManager.edit()
                    .putString(getString(R.string.nginx_url_key), it)
                    .apply()  // change the stored url in nginx_url_key to it
            }
            return@setOnPreferenceClickListener true
        }
    }
}