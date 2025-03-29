package com.lagradost.cloudstream3.utils

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getString
import androidx.fragment.app.FragmentActivity
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.R

object BiometricAuthenticator {

    const val TAG = "cs3Auth"
    private const val MAX_FAILED_ATTEMPTS = 3
    private var failedAttempts = 0
    private var biometricManager: BiometricManager? = null
    var biometricPrompt: BiometricPrompt? = null
    var promptInfo: BiometricPrompt.PromptInfo? = null
    var authCallback: BiometricCallback? = null // listen to authentication success

    private fun initializeBiometrics(activity: FragmentActivity) {
        val executor = ContextCompat.getMainExecutor(activity)

        biometricManager = BiometricManager.from(activity)

        biometricPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    showToast("$errString")
                    Log.e(TAG, "$errorCode")
                    authCallback?.onAuthenticationError()
                        //activity.finish()
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    failedAttempts = 0
                    authCallback?.onAuthenticationSuccess()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    failedAttempts++
                    if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
                        failedAttempts = 0
                        activity.finish()
                    }
                }
            })
    }

    @Suppress("DEPRECATION")
    // authentication dialog prompt builder
    private fun authenticationDialog(
        activity: Activity,
        title: Int,
        setDeviceCred: Boolean,
    ) {
        val description = activity.getString(R.string.biometric_prompt_description)

        if (setDeviceCred) {
            // For API level > 30, Newer API setAllowedAuthenticators is used
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {

                val authFlag = DEVICE_CREDENTIAL or BIOMETRIC_WEAK or BIOMETRIC_STRONG
                promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle(activity.getString(title))
                    .setDescription(description)
                    .setAllowedAuthenticators(authFlag)
                    .build()
            } else {
                // for apis < 30
                promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle(activity.getString(title))
                    .setDescription(description)
                    .setDeviceCredentialAllowed(true)
                    .build()
            }
        } else {
            // fallback for A12+ when both fingerprint & Face unlock is absent but PIN is set
            promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(activity.getString(title))
                .setDescription(description)
                .setDeviceCredentialAllowed(true)
                .build()
        }
    }

    private fun isBiometricHardWareAvailable(): Boolean {
        // authentication occurs only when this is true and device is truly capable
        var result = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            when (biometricManager?.canAuthenticate(
                DEVICE_CREDENTIAL or BIOMETRIC_STRONG or BIOMETRIC_WEAK
            )) {
                BiometricManager.BIOMETRIC_SUCCESS -> result = true
                BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> result = false
                BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> result = false
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> result = false
                BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> result = true
                BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> result = true
                BiometricManager.BIOMETRIC_STATUS_UNKNOWN -> result = false
            }
        } else {
            @Suppress("DEPRECATION")
            when (biometricManager?.canAuthenticate()) {
                BiometricManager.BIOMETRIC_SUCCESS -> result = true
                BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> result = false
                BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> result = false
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> result = false
                BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> result = true
                BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> result = true
                BiometricManager.BIOMETRIC_STATUS_UNKNOWN -> result = false
            }
        }

        return result
    }

    // checks if device is secured i.e has at least some type of lock
    fun deviceHasPasswordPinLock(context: Context?): Boolean {
        val keyMgr =
            context?.getSystemService(AppCompatActivity.KEYGUARD_SERVICE) as? KeyguardManager
        return keyMgr?.isKeyguardSecure ?: false
    }

    // function to start authentication in any fragment or activity
    fun startBiometricAuthentication(activity: FragmentActivity, title: Int, setDeviceCred: Boolean) {
        initializeBiometrics(activity)
        authCallback = activity as? BiometricCallback
        if (isBiometricHardWareAvailable()) {
            authCallback = activity as? BiometricCallback
            authenticationDialog(activity, title, setDeviceCred)
            promptInfo?.let { biometricPrompt?.authenticate(it) }
        } else {
            if (deviceHasPasswordPinLock(activity)) {
                authCallback = activity as? BiometricCallback
                authenticationDialog(activity, R.string.password_pin_authentication_title, true)
                promptInfo?.let { biometricPrompt?.authenticate(it) }

            } else {
                showToast(R.string.biometric_unsupported)
            }
        }
    }

    fun isAuthEnabled(ctx: Context):Boolean {
        return ctx.let {
            PreferenceManager.getDefaultSharedPreferences(ctx)
                .getBoolean(getString(ctx, R.string.biometric_key), false)
        }
    }

    interface BiometricCallback {
        fun onAuthenticationSuccess()
        fun onAuthenticationError()
    }
}
