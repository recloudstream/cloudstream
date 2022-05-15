package com.lagradost.cloudstream3.ui.settings

import android.content.Context
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.syncproviders.AccountManager
import com.lagradost.cloudstream3.syncproviders.OAuth2API
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.beneneCount
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.getPref
import com.lagradost.cloudstream3.utils.UIHelper.dismissSafe
import com.lagradost.cloudstream3.utils.UIHelper.hideKeyboard
import com.lagradost.cloudstream3.utils.UIHelper.setImage

class SettingsAccount : PreferenceFragmentCompat() {
    private fun showLoginInfo(api: AccountManager, info: OAuth2API.LoginInfo) {
        val builder =
            AlertDialog.Builder(context ?: return, R.style.AlertDialogCustom)
                .setView(R.layout.account_managment)
        val dialog = builder.show()

        dialog.findViewById<ImageView>(R.id.account_profile_picture)?.setImage(info.profilePicture)
        dialog.findViewById<TextView>(R.id.account_logout)?.setOnClickListener {
            api.logOut()
            dialog.dismissSafe(activity)
        }

        (info.name ?: context?.getString(R.string.no_data))?.let {
            dialog.findViewById<TextView>(R.id.account_name)?.text = it
        }
        dialog.findViewById<TextView>(R.id.account_site)?.text = api.name
        dialog.findViewById<TextView>(R.id.account_switch_account)?.setOnClickListener {
            dialog.dismissSafe(activity)
            showAccountSwitch(it.context, api)
        }
    }

    private fun showAccountSwitch(context: Context, api: AccountManager) {
        val accounts = api.getAccounts() ?: return

        val builder =
            AlertDialog.Builder(context, R.style.AlertDialogCustom).setView(R.layout.account_switch)
        val dialog = builder.show()

        dialog.findViewById<TextView>(R.id.account_add)?.setOnClickListener {
            try {
                api.authenticate()
            } catch (e: Exception) {
                logError(e)
            }
        }

        val ogIndex = api.accountIndex

        val items = ArrayList<OAuth2API.LoginInfo>()

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
        setPreferencesFromResource(R.xml.settings_credits_account, rootKey)
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(requireContext())

        getPref(R.string.legal_notice_key)?.setOnPreferenceClickListener {
            val builder: AlertDialog.Builder = AlertDialog.Builder(it.context)
            builder.setTitle(R.string.legal_notice)
            builder.setMessage(R.string.legal_notice_text)
            builder.show()
            return@setOnPreferenceClickListener true
        }

        val syncApis =
            listOf(
                Pair(R.string.mal_key, OAuth2API.malApi), Pair(
                    R.string.anilist_key,
                    OAuth2API.aniListApi
                )
            )
        for ((key, api) in syncApis) {
            getPref(key)?.apply {
                title =
                    getString(R.string.login_format).format(api.name, getString(R.string.account))
                setOnPreferenceClickListener { _ ->
                    val info = api.loginInfo()
                    if (info != null) {
                        showLoginInfo(api, info)
                    } else {
                        try {
                            api.authenticate()
                        } catch (e: Exception) {
                            logError(e)
                        }
                    }
                    return@setOnPreferenceClickListener true
                }
            }
        }



        try {
            beneneCount = settingsManager.getInt(getString(R.string.benene_count), 0)
            getPref(R.string.benene_count)?.let { pref ->
                pref.summary =
                    if (beneneCount <= 0) getString(R.string.benene_count_text_none) else getString(
                        R.string.benene_count_text
                    ).format(
                        beneneCount
                    )

                pref.setOnPreferenceClickListener {
                    try {
                        beneneCount++
                        settingsManager.edit().putInt(getString(R.string.benene_count), beneneCount)
                            .apply()
                        it.summary = getString(R.string.benene_count_text).format(beneneCount)
                    } catch (e: Exception) {
                        logError(e)
                    }

                    return@setOnPreferenceClickListener true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

    }
}