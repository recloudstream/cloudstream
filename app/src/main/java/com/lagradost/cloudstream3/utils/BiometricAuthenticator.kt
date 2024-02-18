package com.lagradost.cloudstream3.utils

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.util.Log
import android.widget.Toast.LENGTH_SHORT
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.R

object BiometricAuthenticator {

    private const val MAX_FAILED_ATTEMPTS = 3
    private var failedAttempts = 0
    const val TAG = "cs3Auth"

    private lateinit var biometricManager: BiometricManager
    lateinit var biometricPrompt: BiometricPrompt
    lateinit var promptInfo: BiometricPrompt.PromptInfo

    var authCallback: BiometricAuthCallback? = null // listen to authentication success

    private fun initializeBiometrics(activity: Activity) {
        val executor = ContextCompat.getMainExecutor(activity)
        biometricManager = BiometricManager.from(activity)

        if (!::promptInfo.isInitialized) {
            initBiometricPrompt(
                activity as AppCompatActivity,
                R.string.biometric_authentication_title,
                false
            )
        }

        biometricPrompt = BiometricPrompt(
            activity as AppCompatActivity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    showToast("$errString", LENGTH_SHORT)
                    Log.i(TAG, "$errorCode")
                    activity.finish()
                    failedAttempts++
                    if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
                        failedAttempts = 0
                        activity.finish()
                    } else {
                        failedAttempts = 0
                        MainActivity().finish()
                    }
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

    private fun initBiometricPrompt(
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
                @Suppress("DEPRECATION")
                promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle(activity.getString(title))
                    .setDescription(description)
                    .setDeviceCredentialAllowed(true)
                    .build()
            }
        } else {
            // fallback for A12+ when both fingerprint & Face unlock is absent but PIN is set
            @Suppress("DEPRECATION")
            promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(activity.getString(title))
                .setDescription(description)
                .setDeviceCredentialAllowed(true)
                .build()
        }
    }

    private fun isBiometricHardWareAvailable(): Boolean {
        var result = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            when (biometricManager.canAuthenticate(
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
            when (biometricManager.canAuthenticate()) {
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

    // only needed for Android 9 and below
    fun deviceHasPasswordPinLock(context: Context?): Boolean {
        val keyMgr =
            context?.getSystemService(AppCompatActivity.KEYGUARD_SERVICE) as KeyguardManager
        return keyMgr.isKeyguardSecure
    }

    fun startBiometricAuthentication(activity: Activity, title: Int, setDeviceCred: Boolean) {
        initializeBiometrics(activity)

        if (isBiometricHardWareAvailable()) {
            authCallback = activity as? BiometricAuthCallback
            initBiometricPrompt(activity, title, setDeviceCred)
            biometricPrompt.authenticate(promptInfo)

        } else {
            if (deviceHasPasswordPinLock(activity)) {
                authCallback = activity as? BiometricAuthCallback
                initBiometricPrompt(activity, R.string.password_pin_authentication_title, true)
                biometricPrompt.authenticate(promptInfo)

            } else {
                showToast(R.string.biometric_unsupported, LENGTH_SHORT)
            }
        }
    }

    interface BiometricAuthCallback {
        fun onAuthenticationSuccess()
    }
}
