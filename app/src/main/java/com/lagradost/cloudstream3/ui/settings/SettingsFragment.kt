package com.lagradost.cloudstream3.ui.settings

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.syncproviders.AccountManager.Companion.accountManagers
import com.lagradost.cloudstream3.ui.home.HomeFragment
import com.lagradost.cloudstream3.utils.UIHelper.fixPaddingStatusbar
import com.lagradost.cloudstream3.utils.UIHelper.navigate
import com.lagradost.cloudstream3.utils.UIHelper.setImage
import com.lagradost.cloudstream3.utils.UIHelper.toPx
import kotlinx.android.synthetic.main.main_settings.*
import kotlinx.android.synthetic.main.standard_toolbar.*
import java.io.File

class SettingsFragment : Fragment() {
    companion object {
        var beneneCount = 0

        private var isTv : Boolean = false
        private var isTrueTv : Boolean = false

        fun PreferenceFragmentCompat?.getPref(id: Int): Preference? {
            if (this == null) return null

            return try {
                findPreference(getString(id))
            } catch (e: Exception) {
                logError(e)
                null
            }
        }

        /**
         * On TV you cannot properly scroll to the bottom of settings, this fixes that.
         * */
        fun PreferenceFragmentCompat.setPaddingBottom() {
            if (isTvSettings()) {
                listView?.setPadding(0, 0, 0, 100.toPx)
            }
        }

        fun Fragment?.setUpToolbar(title: String) {
            if (this == null) return
            settings_toolbar?.apply {
                setTitle(title)
                setNavigationIcon(R.drawable.ic_baseline_arrow_back_24)
                setNavigationOnClickListener {
                    activity?.onBackPressed()
                }
            }
            context.fixPaddingStatusbar(settings_toolbar)
        }

        fun Fragment?.setUpToolbar(@StringRes title: Int) {
            if (this == null) return
            settings_toolbar?.apply {
                setTitle(title)
                setNavigationIcon(R.drawable.ic_baseline_arrow_back_24)
                setNavigationOnClickListener {
                    activity?.onBackPressed()
                }
            }
            context.fixPaddingStatusbar(settings_toolbar)
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

        private fun Context.isTvSettings(): Boolean {
            var value = getLayoutInt()
            if (value == -1) {
                value = if (isAutoTv()) 1 else 0
            }
            return value == 1 || value == 2
        }

        private fun Context.isTrueTvSettings(): Boolean {
            var value = getLayoutInt()
            if (value == -1) {
                value = if (isAutoTv()) 1 else 0
            }
            return value == 1
        }

        fun Context.updateTv() {
            isTrueTv = isTrueTvSettings()
            isTv = isTvSettings()
        }

        fun isTrueTvSettings(): Boolean {
            return isTrueTv
        }

        fun isTvSettings(): Boolean {
            return isTv
        }

        fun Context.isEmulatorSettings(): Boolean {
            return getLayoutInt() == 2
        }

        private fun Context.isAutoTv(): Boolean {
            val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as UiModeManager?
            // AFT = Fire TV
            val model = Build.MODEL.lowercase()
            return uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION || Build.MODEL.contains(
                "AFT"
            ) || model.contains("firestick") || model.contains("fire tv") || model.contains("chromecast")
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

        val isTrueTv = isTrueTvSettings()

        for (syncApi in accountManagers) {
            val login = syncApi.loginInfo()
            val pic = login?.profilePicture ?: continue
            if (settings_profile_pic?.setImage(
                    pic,
                    errorImageDrawable = HomeFragment.errorProfilePic
                ) == true
            ) {
                settings_profile_text?.text = login.name
                settings_profile?.isVisible = true
                break
            }
        }

        listOf(
            Pair(settings_general, R.id.action_navigation_settings_to_navigation_settings_general),
            Pair(settings_player, R.id.action_navigation_settings_to_navigation_settings_player),
            Pair(settings_credits, R.id.action_navigation_settings_to_navigation_settings_account),
            Pair(settings_ui, R.id.action_navigation_settings_to_navigation_settings_ui),
            Pair(settings_providers, R.id.action_navigation_settings_to_navigation_settings_providers),
            Pair(settings_updates, R.id.action_navigation_settings_to_navigation_settings_updates),
            Pair(
                settings_extensions,
                R.id.action_navigation_settings_to_navigation_settings_extensions
            ),
        ).forEach { (view, navigationId) ->
            view?.apply {
                setOnClickListener {
                    navigate(navigationId)
                }
                if (isTrueTv) {
                    isFocusable = true
                    isFocusableInTouchMode = true
                }
            }
        }
    }
}