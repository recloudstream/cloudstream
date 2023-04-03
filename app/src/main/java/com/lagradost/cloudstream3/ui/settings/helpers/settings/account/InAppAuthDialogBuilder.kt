package com.lagradost.cloudstream3.ui.settings.helpers.settings.account

import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentActivity
import com.lagradost.cloudstream3.AcraApplication
import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.syncproviders.InAppAuthAPI
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.UIHelper.dismissSafe
import kotlinx.android.synthetic.main.add_account_input.*

class InAppAuthDialogBuilder(
    private val api: InAppAuthAPI,
    private val activity: FragmentActivity?,
) : DialogBuilder(
    api,
    activity,
    R.style.AlertDialogCustom,
    R.layout.add_account_input,
) {

    override fun onLogin(dialog: AlertDialog): Unit = with(dialog) {
        if (activity == null) throw IllegalStateException("Login should be called after validation")

        val loginData = InAppAuthAPI.LoginData(
            username = if (api.requiresUsername) login_username_input?.text?.toString() else null,
            password = if (api.requiresPassword) login_password_input?.text?.toString() else null,
            email = if (api.requiresEmail) login_email_input?.text?.toString() else null,
            server = if (api.requiresServer) login_server_input?.text?.toString() else null,
        )

        ioSafe {
            val isSuccessful = try {
                api.login(loginData)
            } catch (e: Exception) {
                logError(e)
                false
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

    override fun getCommonItems(dialog: AlertDialog) = with(dialog) {
        CommonDialogItems(dialog, text1, apply_btt, cancel_btt, create_account,null)
    }

    override fun getVisibilityMap(dialog: AlertDialog): Map<View, Boolean> = with(dialog) {
        mapOf(
            login_email_input to api.requiresEmail,
            login_password_input to api.requiresPassword,
            login_server_input to api.requiresServer,
            login_username_input to api.requiresUsername
        )
    }

    override fun setupItems(dialog: AlertDialog): Unit = with(dialog) {
        login_email_input?.isVisible = api.requiresEmail
        login_password_input?.isVisible = api.requiresPassword
        login_server_input?.isVisible = api.requiresServer
        login_username_input?.isVisible = api.requiresUsername

        create_account?.isGone = api.createAccountUrl.isNullOrBlank()
        create_account?.setOnClickListener {
            AcraApplication.openBrowser(
                api.createAccountUrl ?: return@setOnClickListener, activity
            )

            dismissSafe()
        }
    }

    override fun handleStoresPasswordInPlainText(dialog: AlertDialog): Unit = with(dialog) {
        if (!api.storesPasswordInPlainText) return

        api.getLatestLoginData()?.let { data ->
            login_email_input?.setText(data.email ?: "")
            login_server_input?.setText(data.server ?: "")
            login_username_input?.setText(data.username ?: "")
            login_password_input?.setText(data.password ?: "")
        }
    }
}