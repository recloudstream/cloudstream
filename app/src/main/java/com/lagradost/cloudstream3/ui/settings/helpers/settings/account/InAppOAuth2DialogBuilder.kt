package com.lagradost.cloudstream3.ui.settings.helpers.settings.account

import android.net.Uri
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentActivity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.syncproviders.InAppOAuth2API
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.UIHelper.dismissSafe
import kotlinx.android.synthetic.main.add_account_input_oauth.apply_btt
import kotlinx.android.synthetic.main.add_account_input_oauth.cancel_btt
import kotlinx.android.synthetic.main.add_account_input_oauth.info_button
import kotlinx.android.synthetic.main.add_account_input_oauth.login_client_id
import kotlinx.android.synthetic.main.add_account_input_oauth.login_client_secret
import kotlinx.android.synthetic.main.add_account_input_oauth.login_file_name
import kotlinx.android.synthetic.main.add_account_input_oauth.text1


class InAppOAuth2DialogBuilder(
    private val api: InAppOAuth2API,
    private val activity: FragmentActivity?,
) : DialogBuilder(api, activity, R.style.AlertDialogCustom, R.layout.add_account_input_oauth) {
    override fun getCommonItems(dialog: AlertDialog) = with(dialog) {
        CommonDialogItems(dialog, text1, apply_btt, cancel_btt, null, info_button)
    }

    override fun getVisibilityMap(dialog: AlertDialog): Map<View, Boolean> = with(dialog) {
        mapOf(
            login_file_name to api.requiresFilename,
            login_client_id to api.requiresClientId,
            login_client_secret to api.requiresSecret,
        )
    }

    override fun setupItems(dialog: AlertDialog): Unit = with(dialog) {
        login_file_name?.isVisible = api.requiresFilename
        login_client_id?.isVisible = api.requiresClientId
        login_client_secret?.isVisible = api.requiresSecret

        info_button?.isGone = api.infoUrl.isNullOrBlank()
        info_button?.setOnClickListener {
            val customTabIntent = CustomTabsIntent.Builder().setShowTitle(true).build()
            customTabIntent.launchUrl(context, Uri.parse(api.infoUrl))
        }
    }


    override fun onLogin(dialog: AlertDialog): Unit = with(activity) {
        if (this == null) throw IllegalStateException("Login should be called after validation")

        val clientId = dialog.login_client_id.text.toString().ifBlank {
            getString(R.string.debug_gdrive_clientId)
        }
        val clientSecret = dialog.login_client_secret.text.toString().ifBlank {
            getString(R.string.debug_gdrive_secret)
        }
        val syncFileName = dialog.login_file_name.text.toString().trim().ifBlank {
            api.defaultFilenameValue
        }
        val redirectUrl = dialog.login_file_name.text.toString().trim().ifBlank {
            api.defaultRedirectUrl
        }

        ioSafe {
            api.getAuthorizationToken(
                this@with,
                InAppOAuth2API.LoginData(
                    clientId = clientId,
                    secret = clientSecret,
                    fileNameInput = syncFileName,
                    redirectUrl = redirectUrl,
                    syncFileId = null
                )
            )
        }

        dialog.dismissSafe()
    }
}
