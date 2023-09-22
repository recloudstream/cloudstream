package com.lagradost.cloudstream3.ui.settings.helpers.settings.account

import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentActivity
import com.lagradost.cloudstream3.AcraApplication
import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.AddAccountInputBinding
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.syncproviders.InAppAuthAPI
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.UIHelper.dismissSafe

class InAppAuthDialogBuilder(
    private val api: InAppAuthAPI,
    private val activity: FragmentActivity,
) : DialogBuilder<AddAccountInputBinding>(
    api,
    activity,
    R.style.AlertDialogCustom,
    AddAccountInputBinding.inflate(activity.layoutInflater),
) {

    override fun onLogin(dialog: AlertDialog): Unit = with(binding) {
//        if (activity == null) throw IllegalStateException("Login should be called after validation")

        val loginData = InAppAuthAPI.LoginData(
            username = if (api.requiresUsername) loginUsernameInput.text?.toString() else null,
            password = if (api.requiresPassword) loginPasswordInput.text?.toString() else null,
            email = if (api.requiresEmail) loginEmailInput.text?.toString() else null,
            server = if (api.requiresServer) loginServerInput.text?.toString() else null,
        )

        ioSafe {
            val isSuccessful = try {
                api.login(loginData)
            } catch (e: Exception) {
                logError(e)
                false
            }
            if (isSuccessful) {
                dialog.dismissSafe()
            }
            activity.runOnUiThread {
                try {
                    CommonActivity.showToast(
                        activity,
                        activity.getString(if (isSuccessful) R.string.authenticated_user else R.string.authenticated_user_fail)
                            .format(
                                api.name
                            )
                    )
                } catch (e: Exception) {
                    logError(e) // format might fail
                }
            }
        }

    }

    override fun getCommonItems(dialog: AlertDialog) = with(binding) {
        CommonDialogItems(dialog, text1, applyBtt, cancelBtt, createAccount,null)
    }

    override fun getVisibilityMap(dialog: AlertDialog): Map<View, Boolean> = with(binding) {
        mapOf(
            loginEmailInput to api.requiresEmail,
            loginPasswordInput to api.requiresPassword,
            loginServerInput to api.requiresServer,
            loginUsernameInput to api.requiresUsername
        )
    }

    override fun setupItems(dialog: AlertDialog): Unit = with(binding) {
        loginEmailInput.isVisible = api.requiresEmail
        loginPasswordInput.isVisible = api.requiresPassword
        loginServerInput.isVisible = api.requiresServer
        loginUsernameInput.isVisible = api.requiresUsername

        createAccount.isGone = api.createAccountUrl.isNullOrBlank()
        createAccount.setOnClickListener {
            AcraApplication.openBrowser(
                api.createAccountUrl ?: return@setOnClickListener, activity
            )

            dialog.dismissSafe()
        }
    }

    override fun handleStoresPasswordInPlainText(dialog: AlertDialog): Unit = with(binding) {
        if (!api.storesPasswordInPlainText) return

        api.getLatestLoginData()?.let { data ->
            loginEmailInput.setText(data.email ?: "")
            loginServerInput.setText(data.server ?: "")
            loginUsernameInput.setText(data.username ?: "")
            loginPasswordInput.setText(data.password ?: "")
        }
    }
}