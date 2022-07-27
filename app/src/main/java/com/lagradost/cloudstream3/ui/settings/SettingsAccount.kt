package com.lagradost.cloudstream3.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.View.*
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.annotation.UiThread
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.preference.PreferenceFragmentCompat
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.syncproviders.AccountManager
import com.lagradost.cloudstream3.syncproviders.AccountManager.Companion.aniListApi
import com.lagradost.cloudstream3.syncproviders.AccountManager.Companion.malApi
import com.lagradost.cloudstream3.syncproviders.AccountManager.Companion.nginxApi
import com.lagradost.cloudstream3.syncproviders.AccountManager.Companion.openSubtitlesApi
import com.lagradost.cloudstream3.syncproviders.AuthAPI
import com.lagradost.cloudstream3.syncproviders.InAppAuthAPI
import com.lagradost.cloudstream3.syncproviders.OAuth2API
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.getPref
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.isTvSettings
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.setUpToolbar
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.UIHelper.dismissSafe
import com.lagradost.cloudstream3.utils.UIHelper.hideKeyboard
import com.lagradost.cloudstream3.utils.UIHelper.setImage
import kotlinx.android.synthetic.main.account_managment.*
import kotlinx.android.synthetic.main.account_switch.*
import kotlinx.android.synthetic.main.add_account_input.*

class SettingsAccount : PreferenceFragmentCompat() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setUpToolbar(R.string.category_account)
    }

    private fun showLoginInfo(api: AccountManager, info: AuthAPI.LoginInfo) {
        val builder =
            AlertDialog.Builder(context ?: return, R.style.AlertDialogCustom)
                .setView(R.layout.account_managment)
        val dialog = builder.show()

        dialog.account_main_profile_picture_holder?.isVisible =
            dialog.account_main_profile_picture?.setImage(info.profilePicture) == true

        dialog.account_logout?.setOnClickListener {
            api.logOut()
            dialog.dismissSafe(activity)
        }

        (info.name ?: context?.getString(R.string.no_data))?.let {
            dialog.findViewById<TextView>(R.id.account_name)?.text = it
        }

        dialog.account_site?.text = api.name
        dialog.account_switch_account?.setOnClickListener {
            dialog.dismissSafe(activity)
            showAccountSwitch(it.context, api)
        }
    }

    @UiThread
    private fun addAccount(api: AccountManager) {
        try {
            when (api) {
                is OAuth2API -> {
                    api.authenticate()
                }
                is InAppAuthAPI -> {
                    val builder =
                        AlertDialog.Builder(context ?: return, R.style.AlertDialogCustom)
                            .setView(R.layout.add_account_input)
                    val dialog = builder.show()

                    val visibilityMap = mapOf(
                        dialog.login_email_input to api.requiresEmail,
                        dialog.login_password_input to api.requiresPassword,
                        dialog.login_server_input to api.requiresServer,
                        dialog.login_username_input to api.requiresUsername
                    )

                    if (context?.isTvSettings() == true) {
                        visibilityMap.forEach { (input, isVisible) ->
                            input.isVisible = isVisible

                            // Band-aid for weird FireTV behavior causing crashes because keyboard covers the screen
                            input.setOnEditorActionListener { textView, actionId, _ ->
                                if (actionId == EditorInfo.IME_ACTION_NEXT) {
                                    val view = textView.focusSearch(FOCUS_DOWN)
                                    return@setOnEditorActionListener view?.requestFocus(FOCUS_DOWN) == true
                                }
                                return@setOnEditorActionListener true
                            }
                        }
                    } else {
                        visibilityMap.forEach { (input, isVisible) ->
                            input.isVisible = isVisible
                        }
                    }

                    dialog.login_email_input?.isVisible = api.requiresEmail
                    dialog.login_password_input?.isVisible = api.requiresPassword
                    dialog.login_server_input?.isVisible = api.requiresServer
                    dialog.login_username_input?.isVisible = api.requiresUsername
                    dialog.create_account?.isGone = api.createAccountUrl.isNullOrBlank()
                    dialog.create_account?.setOnClickListener {
                        val i = Intent(Intent.ACTION_VIEW)
                        i.data = Uri.parse(api.createAccountUrl)
                        try {
                            startActivity(i)
                        } catch (e: Exception) {
                            logError(e)
                        }
                    }
                    dialog.text1?.text = api.name

                    if (api.storesPasswordInPlainText) {
                        api.getLatestLoginData()?.let { data ->
                            dialog.login_email_input?.setText(data.email ?: "")
                            dialog.login_server_input?.setText(data.server ?: "")
                            dialog.login_username_input?.setText(data.username ?: "")
                            dialog.login_password_input?.setText(data.password ?: "")
                        }
                    }

                    dialog.apply_btt?.setOnClickListener {
                        val loginData = InAppAuthAPI.LoginData(
                            username = if (api.requiresUsername) dialog.login_username_input?.text?.toString() else null,
                            password = if (api.requiresPassword) dialog.login_password_input?.text?.toString() else null,
                            email = if (api.requiresEmail) dialog.login_email_input?.text?.toString() else null,
                            server = if (api.requiresServer) dialog.login_server_input?.text?.toString() else null,
                        )
                        ioSafe {
                            val isSuccessful = try {
                                api.login(loginData)
                            } catch (e: Exception) {
                                logError(e)
                                false
                            }
                            activity?.runOnUiThread {
                                try {
                                    showToast(
                                        activity,
                                        getString(if (isSuccessful) R.string.authenticated_user else R.string.authenticated_user_fail).format(
                                            api.name
                                        )
                                    )
                                } catch (e: Exception) {
                                    logError(e) // format might fail
                                }
                            }
                        }
                        dialog.dismissSafe(activity)
                    }
                    dialog.cancel_btt?.setOnClickListener {
                        dialog.dismissSafe(activity)
                    }
                }
                else -> {
                    throw NotImplementedError("You are trying to add an account that has an unknown login method")
                }
            }
        } catch (e: Exception) {
            logError(e)
        }
    }

    private fun showAccountSwitch(context: Context, api: AccountManager) {
        val accounts = api.getAccounts() ?: return

        val builder =
            AlertDialog.Builder(context, R.style.AlertDialogCustom).setView(R.layout.account_switch)
        val dialog = builder.show()

        dialog.account_add?.setOnClickListener {
            addAccount(api)
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

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        hideKeyboard()
        setPreferencesFromResource(R.xml.settings_account, rootKey)

        val syncApis =
            listOf(
                R.string.mal_key to malApi,
                R.string.anilist_key to aniListApi,
                R.string.opensubtitles_key to openSubtitlesApi,
                R.string.nginx_key to nginxApi,
            )

        for ((key, api) in syncApis) {
            getPref(key)?.apply {
                title =
                    getString(R.string.login_format).format(api.name, getString(R.string.account))
                setOnPreferenceClickListener {
                    val info = api.loginInfo()
                    if (info != null) {
                        showLoginInfo(api, info)
                    } else {
                        addAccount(api)
                    }
                    return@setOnPreferenceClickListener true
                }
            }
        }
    }
}
