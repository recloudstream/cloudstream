package com.lagradost.cloudstream3.ui.settings.helpers.settings.account

import android.net.Uri
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentActivity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.AddAccountInputOauthBinding
import com.lagradost.cloudstream3.syncproviders.InAppOAuth2API
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.UIHelper.dismissSafe


class InAppOAuth2DialogBuilder(
    private val api: InAppOAuth2API,
    private val activity: FragmentActivity,
) : DialogBuilder<AddAccountInputOauthBinding>(
    api, activity, R.style.AlertDialogCustom,
    AddAccountInputOauthBinding.inflate(activity.layoutInflater)
) {
    override fun getCommonItems(dialog: AlertDialog) = with(binding) {
        CommonDialogItems(dialog, text1, applyBtt, cancelBtt, null, infoButton)
    }

    override fun getVisibilityMap(dialog: AlertDialog): Map<View, Boolean> = with(binding) {
        mapOf(
            loginFileName to api.requiresFilename,
            loginClientId to api.requiresClientId,
            loginClientSecret to api.requiresSecret,
        )
    }

    override fun setupItems(dialog: AlertDialog): Unit = with(binding) {
        loginFileName.isVisible = api.requiresFilename
        loginClientId.isVisible = api.requiresClientId
        loginClientSecret.isVisible = api.requiresSecret

        infoButton.isGone = api.infoUrl.isNullOrBlank()
        infoButton.setOnClickListener {
            val customTabIntent = CustomTabsIntent.Builder().setShowTitle(true).build()
            customTabIntent.launchUrl(binding.root.context, Uri.parse(api.infoUrl))
        }
    }


    override fun onLogin(dialog: AlertDialog): Unit = with(binding) {
//        if (this == null) throw IllegalStateException("Login should be called after validation")

        val ctx = this.root.context

        val clientId = loginClientId.text.toString().ifBlank {
            ctx.getString(R.string.debug_gdrive_clientId)
        }
        val clientSecret = loginClientSecret.text.toString().ifBlank {
            ctx.getString(R.string.debug_gdrive_secret)
        }
        val syncFileName = loginFileName.text.toString().trim().ifBlank {
            api.defaultFilenameValue
        }
        val redirectUrl = loginFileName.text.toString().trim().ifBlank {
            api.defaultRedirectUrl
        }

        ioSafe {
            api.getAuthorizationToken(
                this@InAppOAuth2DialogBuilder.activity,
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
