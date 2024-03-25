package com.lagradost.cloudstream3.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
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

const val packageName = BuildConfig.APPLICATION_ID
const val TAG = "PowerManagerAPI"

object BatteryOptimizationChecker {

    fun isAppRestricted(context: Context?): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && context != null) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            return !powerManager.isIgnoringBatteryOptimizations(context.packageName)
        }

        return false // below Marshmallow, it's always unrestricted when app is in background
    }

    fun openBatteryOptimizationSettings(context: Context) {
        if (shouldShowBatteryOptimizationDialog(context)) {
            showBatteryOptimizationDialog(context)
        }
    }

    fun showBatteryOptimizationDialog(context: Context) {
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(context)

        try {
            context.let {
                AlertDialog.Builder(it)
                    .setTitle(R.string.battery_dialog_title)
                    .setIcon(R.drawable.ic_battery)
                    .setMessage(R.string.battery_dialog_message)
                    .setPositiveButton(R.string.ok) { _, _ ->
                        intentOpenAppInfo(it)
                    }
                    .setNegativeButton(R.string.cancel) { _, _ ->
                        settingsManager.edit()
                            .putBoolean(context.getString(R.string.battery_optimisation_key), false)
                            .apply()
                    }
                    .show()
            }
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

    private fun intentOpenAppInfo(context: Context) {
        val intent = Intent()
        try {
            intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", packageName, null))
            context.startActivity(intent, Bundle())
        } catch (t: Throwable) {
            Log.e(TAG, "Unable to invoke any intent", t)
            if (t is ActivityNotFoundException) {
                showToast("Exception: Activity Not Found")
            } else {
                showToast(R.string.app_info_intent_error)
            }
        }
    }
}
