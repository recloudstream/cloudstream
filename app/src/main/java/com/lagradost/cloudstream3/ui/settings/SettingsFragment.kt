package com.lagradost.cloudstream3.ui.settings

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.UIHelper.navigate
import kotlinx.android.synthetic.main.main_settings.*
import java.io.File

class SettingsFragment : Fragment() {
    companion object {
        var beneneCount = 0

        fun PreferenceFragmentCompat?.getPref(id: Int): Preference? {
            if (this == null) return null

            return try {
                findPreference(getString(id))
            } catch (e: Exception) {
                logError(e)
                null
            }
        }

        fun getFolderSize(dir: File): Long {
            var size: Long = 0
            dir.listFiles()?.let {
                for (file in it) {
                    size += if (file.isFile) {
                        // System.out.println(file.getName() + " " + file.length());
                        file.length()
                    } else getFolderSize(file)
                }
            }

            return size
        }

        private fun Context.getLayoutInt(): Int {
            val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)
            return settingsManager.getInt(this.getString(R.string.app_layout_key), -1)
        }

        fun Context.isTvSettings(): Boolean {
            var value = getLayoutInt()
            if (value == -1) {
                value = if (isAutoTv()) 1 else 0
            }
            return value == 1 || value == 2
        }

        fun Context.isTrueTvSettings(): Boolean {
            var value = getLayoutInt()
            if (value == -1) {
                value = if (isAutoTv()) 1 else 0
            }
            return value == 1
        }

        fun Context.isEmulatorSettings(): Boolean {
            return getLayoutInt() == 2
        }

        private fun Context.isAutoTv(): Boolean {
            val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as UiModeManager?
            // AFT = Fire TV
            return uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION || Build.MODEL.contains(
                "AFT"
            )
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.main_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        fun navigate(id: Int) {
            activity?.navigate(id, Bundle())
        }

        settings_player?.setOnClickListener {
            navigate(R.id.action_navigation_settings_to_navigation_settings_player)
        }

        settings_credits?.setOnClickListener {
            navigate(R.id.action_navigation_settings_to_navigation_settings_account)
        }

        settings_ui?.setOnClickListener {
            navigate(R.id.action_navigation_settings_to_navigation_settings_ui)
        }

        settings_lang?.setOnClickListener {
            navigate(R.id.action_navigation_settings_to_navigation_settings_lang)
        }

        settings_nginx?.setOnClickListener {
            navigate(R.id.action_navigation_settings_to_navigation_settings_nginx)
        }

        settings_updates?.setOnClickListener {
            navigate(R.id.action_navigation_settings_to_navigation_settings_updates)
        }
    }
}