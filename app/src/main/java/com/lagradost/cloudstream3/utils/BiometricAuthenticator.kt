package com.lagradost.cloudstream3.utils

import android.app.Activity
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.lagradost.cloudstream3.AcraApplication.Companion.context
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.isEmulatorSettings
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.isTrueTvSettings
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.isTvSettings

object BiometricAuthenticator {

    private lateinit var biometricManager: BiometricManager
    lateinit var biometricPrompt: BiometricPrompt
    lateinit var promptInfo: BiometricPrompt.PromptInfo
    const val TAG = "cs3Auth"

    fun initializeBiometrics(activity: Activity) {
        val executor = ContextCompat.getMainExecutor(activity)
        biometricManager = BiometricManager.from(activity)

        biometricPrompt = BiometricPrompt(activity as AppCompatActivity, executor, object : BiometricPrompt.AuthenticationCallback() {

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    Log.d(TAG, "Authentication error: $errString")
                    activity.finish()
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    Log.d(TAG, "Biometric succeeded.")
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Log.d(TAG, "Authentication error")
                    showToast(R.string.biometric_failed, Toast.LENGTH_SHORT)
                    activity.finish()
                }
            })

        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock CloudStream")
            //.setSubtitle("Log in using your biometric credential")
            //.setNegativeButtonText("Use account password")
            .setAllowedAuthenticators(BIOMETRIC_WEAK or BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
            .build()
    }

    fun checkBiometricAvailability() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {

            // Strong and credential bundle cannot be checked at same time in API < A11 (R)
            when (biometricManager.canAuthenticate(BIOMETRIC_WEAK or BIOMETRIC_STRONG or DEVICE_CREDENTIAL)) {
                BiometricManager.BIOMETRIC_SUCCESS ->
                    Log.d(TAG, "App can authenticate.")

                BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
                    Log.d(TAG, "No biometric sensor found.")

                BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
                    Log.d(TAG, "Biometric authentication is currently unavailable.")

                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                    showToast(R.string.biometric_not_enrolled, Toast.LENGTH_SHORT)

                BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED ->
                    showToast(R.string.biometric_update_required, Toast.LENGTH_SHORT)

                BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED ->
                    showToast(R.string.biometric_unsupported, Toast.LENGTH_SHORT)

                BiometricManager.BIOMETRIC_STATUS_UNKNOWN ->
                    Log.e(TAG, "Unknown error encountered while authenticating fingerprint.")
            }
        }

        else {

            when (biometricManager.canAuthenticate(BIOMETRIC_WEAK or BIOMETRIC_STRONG)) {

            BiometricManager.BIOMETRIC_SUCCESS ->
            Log.d(TAG, "App can authenticate using biometrics.")

            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
            Log.d(TAG, "No biometric features available on this device.")

            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
            Log.e(TAG, "Biometric features are currently unavailable.")

            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
            Log.e(TAG, "Biometric features are currently unavailable.")

            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED ->
            showToast(R.string.biometric_update_required, Toast.LENGTH_SHORT)

            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED ->
            showToast(R.string.biometric_unsupported, Toast.LENGTH_SHORT)

            BiometricManager.BIOMETRIC_STATUS_UNKNOWN ->
            Log.d(TAG, "Unknown error encountered while authenticating fingerprint.")

            }
        }
    }

    // yes, this feature is phone exclusive
    fun isTruePhone(): Boolean {
        return !isTrueTvSettings() && !isTvSettings() && context?.isEmulatorSettings() != true
    }
}
