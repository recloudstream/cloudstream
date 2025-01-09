package com.lagradost.cloudstream3.ui.settings

import android.graphics.Bitmap
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.view.View.FOCUS_DOWN
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.annotation.UiThread
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentActivity
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.CloudStreamApp.Companion.openBrowser
import com.lagradost.cloudstream3.CommonActivity.onDialogDismissedEvent
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.AccountManagmentBinding
import com.lagradost.cloudstream3.databinding.AccountSwitchBinding
import com.lagradost.cloudstream3.databinding.AddAccountInputBinding
import com.lagradost.cloudstream3.databinding.DeviceAuthBinding
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.syncproviders.AccountManager
import com.lagradost.cloudstream3.syncproviders.AccountManager.Companion.aniListApi
import com.lagradost.cloudstream3.syncproviders.AccountManager.Companion.malApi
import com.lagradost.cloudstream3.syncproviders.AccountManager.Companion.openSubtitlesApi
import com.lagradost.cloudstream3.syncproviders.AccountManager.Companion.simklApi
import com.lagradost.cloudstream3.syncproviders.AccountManager.Companion.subDlApi
import com.lagradost.cloudstream3.syncproviders.AuthAPI
import com.lagradost.cloudstream3.syncproviders.InAppAuthAPI
import com.lagradost.cloudstream3.syncproviders.OAuth2API
import com.lagradost.cloudstream3.ui.settings.Globals.EMULATOR
import com.lagradost.cloudstream3.ui.settings.Globals.PHONE
import com.lagradost.cloudstream3.ui.settings.Globals.TV
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.getPref
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.hideOn
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.setPaddingBottom
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.setToolBarScrollFlags
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.setUpToolbar
import com.lagradost.cloudstream3.utils.AppContextUtils.html
import com.lagradost.cloudstream3.utils.BackupUtils
import com.lagradost.cloudstream3.utils.BiometricAuthenticator.BiometricCallback
import com.lagradost.cloudstream3.utils.BiometricAuthenticator.authCallback
import com.lagradost.cloudstream3.utils.BiometricAuthenticator.biometricPrompt
import com.lagradost.cloudstream3.utils.BiometricAuthenticator.deviceHasPasswordPinLock
import com.lagradost.cloudstream3.utils.BiometricAuthenticator.isAuthEnabled
import com.lagradost.cloudstream3.utils.BiometricAuthenticator.promptInfo
import com.lagradost.cloudstream3.utils.BiometricAuthenticator.startBiometricAuthentication
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.ImageLoader.loadImage
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showBottomDialogText
import com.lagradost.cloudstream3.utils.UIHelper.colorFromAttribute
import com.lagradost.cloudstream3.utils.UIHelper.dismissSafe
import com.lagradost.cloudstream3.utils.UIHelper.hideKeyboard
import com.lagradost.cloudstream3.utils.setText
import com.lagradost.cloudstream3.utils.txt
import qrcode.QRCode

class SettingsAccount : PreferenceFragmentCompat(), BiometricCallback {
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

            binding.accountMainProfilePictureHolder.isVisible = !info.profilePicture.isNullOrEmpty()
            binding.accountMainProfilePicture.loadImage(info.profilePicture)


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

            if (isLayout(TV or EMULATOR)) {
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
        fun addAccount(activity: FragmentActivity?, api: AccountManager) {
            try {
                when (api) {
                    is OAuth2API -> {
                        if (isLayout(PHONE) || !api.supportDeviceAuth) {
                            api.authenticate(activity)
                        } else if (api.supportDeviceAuth && activity != null) {

                            val binding: DeviceAuthBinding =
                                DeviceAuthBinding.inflate(activity.layoutInflater, null, false)

                            val builder =
                                AlertDialog.Builder(activity)
                                    .setView(binding.root)

                            builder.apply {
                                setNegativeButton(R.string.cancel) { _, _ -> }
                                setPositiveButton(R.string.auth_locally) { _, _ ->
                                    api.authenticate(activity)
                                }
                            }

                            val dialog = builder.create()

                            ioSafe {
                                try {
                                    val pinCodeData = api.getDevicePin()
                                    if (pinCodeData == null) {
                                        showToast(R.string.device_pin_error_message)
                                        api.authenticate(activity)
                                        return@ioSafe
                                    }

                                    /*val logoBytes = ContextCompat.getDrawable(
                                        activity,
                                        R.drawable.cloud_2_solid
                                    )?.toBitmapOrNull()?.let { bitmap ->
                                        val csLogo = ByteArrayOutputStream()
                                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, csLogo)
                                        csLogo.toByteArray()
                                    }*/

                                    val qrCodeImage = QRCode.ofRoundedSquares()
                                        .withColor(activity.colorFromAttribute(R.attr.textColor))
                                        .withBackgroundColor(activity.colorFromAttribute(R.attr.primaryBlackBackground))
                                        //.withLogo(logoBytes, 200.toPx, 200.toPx) //For later if logo needed anytime
                                        .build(pinCodeData.verificationUrl)
                                        .render().nativeImage() as Bitmap

                                    activity.runOnUiThread {
                                        dialog.show()
                                        binding.apply {
                                            devicePinCode.setText(txt(pinCodeData.userCode))
                                            deviceAuthMessage.setText(
                                                txt(
                                                    R.string.device_pin_url_message,
                                                    pinCodeData.verificationUrl
                                                )
                                            )
                                            deviceAuthQrcode.loadImage(qrCodeImage)
                                        }

                                        val expirationMillis =
                                            pinCodeData.expiresIn.times(1000).toLong()

                                        object : CountDownTimer(expirationMillis, 1000) {

                                            override fun onTick(millisUntilFinished: Long) {
                                                val secondsUntilFinished =
                                                    millisUntilFinished.div(1000).toInt()

                                                binding.deviceAuthValidationCounter.setText(
                                                    txt(
                                                        R.string.device_pin_counter_text,
                                                        secondsUntilFinished.div(60),
                                                        secondsUntilFinished.rem(60)
                                                    )
                                                )

                                                ioSafe {
                                                    if (secondsUntilFinished.rem(pinCodeData.interval) == 0 && api.handleDeviceAuth(pinCodeData)) {
                                                        showToast(
                                                            txt(
                                                                R.string.authenticated_user,
                                                                api.name
                                                            )
                                                        )
                                                        dialog.dismissSafe(activity)
                                                        cancel()
                                                    }
                                                }
                                            }

                                            override fun onFinish() {
                                                showToast(R.string.device_pin_expired_message)
                                                dialog.dismissSafe(activity)
                                            }

                                        }.start()
                                    }
                                } catch (e: Exception) {
                                    logError(e)
                                }
                            }
                        }
                    }

                    is InAppAuthAPI -> {
                        if (activity == null) return
                        val binding: AddAccountInputBinding =
                            AddAccountInputBinding.inflate(activity.layoutInflater, null, false)
                        val builder =
                            AlertDialog.Builder(activity, R.style.AlertDialogCustom)
                                .setView(binding.root)
                        val dialog = builder.show()

                        val visibilityMap = listOf(
                            binding.loginEmailInput to api.requiresEmail,
                            binding.loginPasswordInput to api.requiresPassword,
                            binding.loginServerInput to api.requiresServer,
                            binding.loginUsernameInput to api.requiresUsername
                        )

                        if (isLayout(TV or EMULATOR)) {
                            visibilityMap.forEach { (input, isVisible) ->
                                input.isVisible = isVisible

                                // Band-aid for weird FireTV behavior causing crashes because keyboard covers the screen
                                input.setOnEditorActionListener { textView, actionId, _ ->
                                    if (actionId == EditorInfo.IME_ACTION_NEXT) {
                                        val view = textView.focusSearch(FOCUS_DOWN)
                                        return@setOnEditorActionListener view?.requestFocus(
                                            FOCUS_DOWN
                                        ) == true
                                    }
                                    return@setOnEditorActionListener true
                                }
                            }
                        } else {
                            visibilityMap.forEach { (input, isVisible) ->
                                input.isVisible = isVisible
                            }
                        }

                        binding.loginEmailInput.isVisible = api.requiresEmail
                        binding.loginPasswordInput.isVisible = api.requiresPassword
                        binding.loginServerInput.isVisible = api.requiresServer
                        binding.loginUsernameInput.isVisible = api.requiresUsername
                        binding.createAccount.isGone = api.createAccountUrl.isNullOrBlank()
                        binding.createAccount.setOnClickListener {
                            openBrowser(
                                api.createAccountUrl ?: return@setOnClickListener,
                                activity
                            )
                            dialog.dismissSafe()
                        }

                        val displayedItems = listOf(
                            binding.loginUsernameInput,
                            binding.loginEmailInput,
                            binding.loginServerInput,
                            binding.loginPasswordInput
                        ).filter { it.isVisible }

                        displayedItems.foldRight(displayedItems.firstOrNull()) { item, previous ->
                            item.id.let { previous?.nextFocusDownId = it }
                            previous?.id?.let { item.nextFocusUpId = it }
                            item
                        }

                        displayedItems.firstOrNull()?.let {
                            binding.createAccount.nextFocusDownId = it.id
                            it.nextFocusUpId = binding.createAccount.id
                        }
                        binding.applyBtt.id.let {
                            displayedItems.lastOrNull()?.nextFocusDownId = it
                        }

                        binding.text1.text = api.name

                        if (api.storesPasswordInPlainText) {
                            api.getLatestLoginData()?.let { data ->
                                binding.loginEmailInput.setText(data.email ?: "")
                                binding.loginServerInput.setText(data.server ?: "")
                                binding.loginUsernameInput.setText(data.username ?: "")
                                binding.loginPasswordInput.setText(data.password ?: "")
                            }
                        }

                        binding.applyBtt.setOnClickListener {
                            val loginData = InAppAuthAPI.LoginData(
                                username = if (api.requiresUsername) binding.loginUsernameInput.text?.toString() else null,
                                password = if (api.requiresPassword) binding.loginPasswordInput.text?.toString() else null,
                                email = if (api.requiresEmail) binding.loginEmailInput.text?.toString() else null,
                                server = if (api.requiresServer) binding.loginServerInput.text?.toString() else null,
                            )
                            ioSafe {
                                try {
                                    showToast(
                                        txt(
                                            if (api.login(loginData)) R.string.authenticated_user else R.string.authenticated_user_fail,
                                            api.name
                                        )
                                    )
                                } catch (e: Exception) {
                                    logError(e)
                                }
                            }
                            dialog.dismissSafe(activity)
                        }
                        binding.cancelBtt.setOnClickListener {
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
    }

    private fun updateAuthPreference(enabled: Boolean) {
        val biometricKey = getString(R.string.biometric_key)

        PreferenceManager.getDefaultSharedPreferences(context ?: return).edit()
            .putBoolean(biometricKey, enabled).apply()
        findPreference<SwitchPreferenceCompat>(biometricKey)?.isChecked = enabled
    }

    override fun onAuthenticationError() {
        updateAuthPreference(!isAuthEnabled(context ?: return))
    }

    override fun onAuthenticationSuccess() {
        if (isAuthEnabled(context?: return)) {
            updateAuthPreference(true)
            BackupUtils.backup(activity)
            activity?.showBottomDialogText(
                getString(R.string.biometric_setting),
                getString(R.string.biometric_warning).html()
            ) { onDialogDismissedEvent }
        } else {
            updateAuthPreference(false)
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

        //Hides the security  category on TV as it's only Biometric for now
        getPref(R.string.pref_category_security_key)?.hideOn(TV or EMULATOR)

        getPref(R.string.biometric_key)?.hideOn(TV or EMULATOR)?.setOnPreferenceClickListener {
            val ctx = context ?: return@setOnPreferenceClickListener false

            if (deviceHasPasswordPinLock(ctx)) {
                startBiometricAuthentication(
                    activity?: return@setOnPreferenceClickListener false,
                    R.string.biometric_authentication_title,
                    false
                    )
                promptInfo?.let {
                    authCallback = this
                    biometricPrompt?.authenticate(it)
                }
            }

            false
        }

        val syncApis =
            listOf(
                R.string.mal_key to malApi,
                R.string.anilist_key to aniListApi,
                R.string.simkl_key to simklApi,
                R.string.opensubtitles_key to openSubtitlesApi,
                R.string.subdl_key to subDlApi,
            )

        for ((key, api) in syncApis) {
            getPref(key)?.apply {
                title = api.name
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
