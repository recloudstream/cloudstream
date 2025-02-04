package com.lagradost.cloudstream3.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build.VERSION.SDK_INT
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.BuildConfig
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.settings.Globals.PHONE
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout

private const val PACKAGE_NAME = BuildConfig.APPLICATION_ID
private const val TAG = "PowerManagerAPI"

object BatteryOptimizationChecker {

    fun isAppRestricted(context: Context?): Boolean {
        if (SDK_INT >= 23 && context != null) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            return !powerManager.isIgnoringBatteryOptimizations(context.packageName)
        }

        return false // below Marshmallow, it's always unrestricted when app is in background
    }

    fun openBatteryOptimizationSettings(context: Context) {
        if (shouldShowBatteryOptimizationDialog(context)) {
            context.showBatteryOptimizationDialog()
        }
    }

    fun Context.showBatteryOptimizationDialog() {
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)

        try {
            AlertDialog.Builder(this)
                .setTitle(R.string.battery_dialog_title)
                .setIcon(R.drawable.ic_battery)
                .setMessage(R.string.battery_dialog_message)
                .setPositiveButton(R.string.ok) { _, _ -> showRequestIgnoreBatteryOptDialog() }
                .setNegativeButton(R.string.cancel) { _, _ ->
                    settingsManager.edit()
                        .putBoolean(getString(R.string.battery_optimisation_key), false)
                        .apply()
                }
                .show()
        } catch (t: Throwable) {
            Log.e(TAG, "Error showing battery optimization dialog", t)
        }
    }

    private fun shouldShowBatteryOptimizationDialog(context: Context): Boolean {
        val isRestricted = isAppRestricted(context)
        val isOptimizedNotShown = PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(context.getString(R.string.battery_optimisation_key), true)
        return isRestricted && isOptimizedNotShown && isLayout(PHONE)
    }

    private fun Context.showRequestIgnoreBatteryOptDialog() {
        try {
            val intent = Intent().apply {
                action =  Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                data = Uri.parse("package:$PACKAGE_NAME")
            }
            startActivity(intent)
        } catch (t: Throwable) {
            Log.e(TAG, "Unable to invoke APP_DETAILS intent", t)
            if (t is ActivityNotFoundException) {
                showToast("Exception: Activity Not Found")
            } else {
                showToast(R.string.app_info_intent_error)
            }
        }
    }
}
