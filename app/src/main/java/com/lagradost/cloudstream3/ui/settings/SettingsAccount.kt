package com.lagradost.cloudstream3.ui.settings

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.annotation.UiThread
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentActivity
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.AcraApplication.Companion.openBrowser
import com.lagradost.cloudstream3.CommonActivity.onDialogDismissedEvent
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.AccountManagmentBinding
import com.lagradost.cloudstream3.databinding.AccountSwitchBinding
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.normalSafeApiCall
import com.lagradost.cloudstream3.syncproviders.AccountManager
import com.lagradost.cloudstream3.syncproviders.AccountManager.Companion.aniListApi
import com.lagradost.cloudstream3.syncproviders.AccountManager.Companion.googleDriveApi
import com.lagradost.cloudstream3.syncproviders.AccountManager.Companion.malApi
import com.lagradost.cloudstream3.syncproviders.AccountManager.Companion.openSubtitlesApi
import com.lagradost.cloudstream3.syncproviders.AccountManager.Companion.pcloudApi
import com.lagradost.cloudstream3.syncproviders.AccountManager.Companion.simklApi
import com.lagradost.cloudstream3.syncproviders.AuthAPI
import com.lagradost.cloudstream3.syncproviders.InAppAuthAPI
import com.lagradost.cloudstream3.syncproviders.InAppOAuth2API
import com.lagradost.cloudstream3.syncproviders.OAuth2API
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.getPref
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.isTvSettings
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.setPaddingBottom
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.setToolBarScrollFlags
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.setUpToolbar
import com.lagradost.cloudstream3.ui.settings.helpers.settings.account.InAppAuthDialogBuilder
import com.lagradost.cloudstream3.ui.settings.helpers.settings.account.InAppOAuth2DialogBuilder
import com.lagradost.cloudstream3.utils.AppUtils.html
import com.lagradost.cloudstream3.utils.BackupUtils
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showBottomDialogText
import com.lagradost.cloudstream3.utils.UIHelper.dismissSafe
import com.lagradost.cloudstream3.utils.UIHelper.hideKeyboard
import com.lagradost.cloudstream3.utils.UIHelper.setImage

class SettingsAccount : PreferenceFragmentCompat() {
    companion object {
        /** Used by nginx plugin too */
        fun showLoginInfo(
            activity: FragmentActivity?,
            api: AccountManager,
            info: AuthAPI.LoginInfo
        ) {
            if (activity == null) return
            val binding: AccountManagmentBinding =
                AccountManagmentBinding.inflate(activity.layoutInflater, null, false)
            val builder =
                AlertDialog.Builder(activity, R.style.AlertDialogCustom)
                    .setView(binding.root)
            val dialog = builder.show()

            binding.accountMainProfilePictureHolder.isVisible =
                binding.accountMainProfilePicture.setImage(info.profilePicture)

            binding.accountLogout.setOnClickListener {
                api.logOut()
                dialog.dismissSafe(activity)
            }

            (info.name ?: activity.getString(R.string.no_data)).let {
                dialog.findViewById<TextView>(R.id.account_name)?.text = it
            }

            binding.accountSite.text = api.name
            binding.accountSwitchAccount.setOnClickListener {
                dialog.dismissSafe(activity)
                showAccountSwitch(activity, api)
            }

            if (isTvSettings()) {
                binding.accountSwitchAccount.requestFocus()
            }
        }

        private fun showAccountSwitch(activity: FragmentActivity, api: AccountManager) {
            val accounts = api.getAccounts() ?: return
            val binding: AccountSwitchBinding =
                AccountSwitchBinding.inflate(activity.layoutInflater, null, false)

            val builder =
                AlertDialog.Builder(activity, R.style.AlertDialogCustom)
                    .setView(binding.root)
            val dialog = builder.show()

            binding.accountAdd.setOnClickListener {
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
            val adapter = AccountAdapter(items) {
                dialog?.dismissSafe(activity)
                api.changeAccount(it.card.accountIndex)
            }
            val list = dialog.findViewById<RecyclerView>(R.id.account_list)
            list?.adapter = adapter
        }

        @UiThread
        fun addAccount(activity: FragmentActivity, api: AccountManager) {
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
        setToolBarScrollFlags()
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        hideKeyboard()
        setPreferencesFromResource(R.xml.settings_account, rootKey)

        getPref(R.string.biometric_key)?.setOnPreferenceClickListener {
            val authEnabled = PreferenceManager.getDefaultSharedPreferences(
                context ?: return@setOnPreferenceClickListener false
            )
                .getBoolean(getString(R.string.biometric_key), false)

            if (authEnabled) {
                BackupUtils.backup(activity)
                val title = activity?.getString(R.string.biometric_setting)
                val warning = activity?.getString(R.string.biometric_warning)
                activity?.showBottomDialogText(
                    title as String,
                    warning.html()
                ) { onDialogDismissedEvent }
            }
            true
        }

        val syncApis =
            listOf(
                R.string.mal_key to malApi,
                R.string.anilist_key to aniListApi,
                R.string.simkl_key to simklApi,
                R.string.opensubtitles_key to openSubtitlesApi,
                R.string.gdrive_key to googleDriveApi,
                R.string.pcloud_key to pcloudApi
            )

        for ((key, api) in syncApis) {
            getPref(key)?.apply {
                title =
                    getString(R.string.login_format).format(api.name, getString(R.string.account))
                setOnPreferenceClickListener {
                    val info = normalSafeApiCall { api.loginInfo() }
                    if (info != null) {
                        showLoginInfo(activity, api, info)
                    } else {
                        activity?.let { activity -> addAccount(activity, api) }
                    }
                    return@setOnPreferenceClickListener true
                }
            }
        }
    }
}
