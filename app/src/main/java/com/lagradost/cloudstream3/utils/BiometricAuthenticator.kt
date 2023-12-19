package com.lagradost.cloudstream3.utils

import android.content.Context
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.lagradost.cloudstream3.AcraApplication.Companion.context
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.isEmulatorSettings
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.isTrueTvSettings
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.isTvSettings

object BiometricAuthenticator {

    private lateinit var biometricManager: BiometricManager
    lateinit var biometricPrompt: BiometricPrompt
    lateinit var promptInfo: BiometricPrompt.PromptInfo

    fun initializeBiometrics(activity: MainActivity) {
        val executor = ContextCompat.getMainExecutor(activity)
        biometricManager = BiometricManager.from(activity)

        biometricPrompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    Toast.makeText(context?.applicationContext, "Authentication error: $errString", Toast.LENGTH_SHORT).show()
                    activity.finish()
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    Log.d("Cs3Auth", "Biometric succeeded.")
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Toast.makeText(context?.applicationContext, "Authentication failed", Toast.LENGTH_SHORT).show()
                    activity.finish()
                }
            })

        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("CloudStream")
            .setSubtitle("Log in using your biometric credential")
            //.setNegativeButtonText("Use account password")
            .setAllowedAuthenticators(BIOMETRIC_WEAK or BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
            .build()
    }

    fun checkBiometricAvailability(context: Context) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {

            // Strong and credential bundle cannot be checked at same time in API < 11
            when (biometricManager.canAuthenticate(BIOMETRIC_WEAK or BIOMETRIC_STRONG or DEVICE_CREDENTIAL)) {
                BiometricManager.BIOMETRIC_SUCCESS ->
                    Log.d("Cs3Auth", "App can authenticate.")

                BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
                    Log.d("Cs3Auth", "No biometric sensor found.")

                BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
                    Log.d("Cs3Auth", "Biometric authentication is currently unavailable.")

                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                    Toast.makeText(
                        context,
                        "No biometric credentials are enrolled",
                        Toast.LENGTH_SHORT
                    ).show()

                BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> {
                    Toast.makeText(
                        context,
                        "Please update your software and security patches.",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> {
                    Toast.makeText(
                        context,
                        "Please update your software and security patches.",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                BiometricManager.BIOMETRIC_STATUS_UNKNOWN -> {
                    Log.d("Cs3Auth", "Unknown error encountered(Biometric data failed).")
                }
            }
        }

        else {

            when (biometricManager.canAuthenticate(BIOMETRIC_WEAK or BIOMETRIC_STRONG)) {

            BiometricManager.BIOMETRIC_SUCCESS ->
            Log.d("Cs3Auth", "App can authenticate using biometrics.")
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
            Log.e("Cs3Auth", "No biometric features available on this device.")
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
            Log.e("Cs3Auth", "Biometric features are currently unavailable.")
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
            Log.e("Cs3Auth", "Biometric features are currently unavailable.")

                BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> {
                    Toast.makeText(
                        context,
                        "Please update your software and security patches.",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> {
                    Toast.makeText(
                        context,
                        "Please update your software and security patches.",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                BiometricManager.BIOMETRIC_STATUS_UNKNOWN -> {
                    Log.d("Cs3Auth", "Unknown error encountered(Biometric data failed).")
                }
            }
        }
    }

    // yes, this feature is phone exclusive
    fun isTruePhone(): Boolean {
        return !isTrueTvSettings() && !isTvSettings() && context?.isEmulatorSettings() != true
    }
}
