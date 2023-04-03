package com.lagradost.cloudstream3.ui.settings

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.annotation.UiThread
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentActivity
import androidx.preference.PreferenceFragmentCompat
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.syncproviders.*
import com.lagradost.cloudstream3.syncproviders.AccountManager.Companion.aniListApi
import com.lagradost.cloudstream3.syncproviders.AccountManager.Companion.googleDriveApi
import com.lagradost.cloudstream3.syncproviders.AccountManager.Companion.malApi
import com.lagradost.cloudstream3.syncproviders.AccountManager.Companion.openSubtitlesApi
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.getPref
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.isTvSettings
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.setPaddingBottom
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.setUpToolbar
import com.lagradost.cloudstream3.ui.settings.helpers.settings.account.InAppAuthDialogBuilder
import com.lagradost.cloudstream3.ui.settings.helpers.settings.account.InAppOAuth2DialogBuilder
import com.lagradost.cloudstream3.utils.UIHelper.dismissSafe
import com.lagradost.cloudstream3.utils.UIHelper.hideKeyboard
import com.lagradost.cloudstream3.utils.UIHelper.setImage
import kotlinx.android.synthetic.main.account_managment.*
import kotlinx.android.synthetic.main.account_switch.*

class SettingsAccount : PreferenceFragmentCompat() {
    companion object {
        /** Used by nginx plugin too */
        fun showLoginInfo(
            activity: FragmentActivity?,
            api: AccountManager,
            info: AuthAPI.LoginInfo
        ) {
            val builder =
                AlertDialog.Builder(activity ?: return, R.style.AlertDialogCustom)
                    .setView(R.layout.account_managment)
            val dialog = builder.show()

            dialog.account_main_profile_picture_holder?.isVisible =
                dialog.account_main_profile_picture?.setImage(info.profilePicture) == true

            dialog.account_logout?.setOnClickListener {
                api.logOut()
                dialog.dismissSafe(activity)
            }

            (info.name ?: activity.getString(R.string.no_data)).let {
                dialog.findViewById<TextView>(R.id.account_name)?.text = it
            }

            dialog.account_site?.text = api.name
            dialog.account_switch_account?.setOnClickListener {
                dialog.dismissSafe(activity)
                showAccountSwitch(activity, api)
            }

            if (isTvSettings()) {
                dialog.account_switch_account?.requestFocus()
            }
        }

        fun showAccountSwitch(activity: FragmentActivity, api: AccountManager) {
            val accounts = api.getAccounts() ?: return

            val builder =
                AlertDialog.Builder(activity, R.style.AlertDialogCustom)
                    .setView(R.layout.account_switch)
            val dialog = builder.show()

            dialog.account_add?.setOnClickListener {
                addAccount(activity, api)
                dialog?.dismissSafe(activity)
            }

            val ogIndex = api.accountIndex

            val items = ArrayList<AuthAPI.LoginInfo>()

            for (index in accounts) {
                api.accountIndex = index
                val accountInfo = api.loginInfo()
                if (accountInfo != null) {
                    items.add(accountInfo)
                }
            }
            api.accountIndex = ogIndex
            val adapter = AccountAdapter(items, R.layout.account_single) {
                dialog?.dismissSafe(activity)
                api.changeAccount(it.card.accountIndex)
            }
            val list = dialog.findViewById<RecyclerView>(R.id.account_list)
            list?.adapter = adapter
        }

        @UiThread
        fun addAccount(activity: FragmentActivity?, api: AccountManager) {
            try {
                when (api) {
                    is InAppOAuth2API -> InAppOAuth2DialogBuilder(api, activity).open()
                    is OAuth2API -> api.authenticate(activity)
                    is InAppAuthAPI -> InAppAuthDialogBuilder(api, activity).open()
                    else -> {
                        throw NotImplementedError("You are trying to add an account that has an unknown login method")
                    }
                }
            } catch (e: Exception) {
                logError(e)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setUpToolbar(R.string.category_account)
        setPaddingBottom()
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        hideKeyboard()
        setPreferencesFromResource(R.xml.settings_account, rootKey)

        val syncApis =
            listOf(
                R.string.mal_key to malApi,
                R.string.anilist_key to aniListApi,
                R.string.opensubtitles_key to openSubtitlesApi,
                R.string.gdrive_key to googleDriveApi
            )

        for ((key, api) in syncApis) {
            getPref(key)?.apply {
                title =
                    getString(R.string.login_format).format(api.name, getString(R.string.account))
                setOnPreferenceClickListener {
                    val info = api.loginInfo()
                    if (info != null) {
                        showLoginInfo(activity, api, info)
                    } else {
                        addAccount(activity, api)
                    }
                    return@setOnPreferenceClickListener true
                }
            }
        }
    }
}
