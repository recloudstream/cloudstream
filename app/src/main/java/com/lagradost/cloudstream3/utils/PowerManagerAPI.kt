package com.lagradost.cloudstream3.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.appcompat.app.AlertDialog
import com.lagradost.cloudstream3.AcraApplication.Companion.context
import com.lagradost.cloudstream3.BuildConfig
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.settings.SettingsFragment

object BatteryOptimizationChecker {

    private const val packageName = BuildConfig.APPLICATION_ID

    private fun isAppRestricted(context: Context?): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && context != null) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            return !powerManager.isIgnoringBatteryOptimizations(context.packageName)
        }

        return false // below Marshmallow, it's always unrestricted
    }

    fun openBatteryOptimizationSettings(context: Context) {
        if (isPhoneAndRestricted()) {
            try {
                showBatteryOptimizationDialog(context)
            } catch (e: Exception) {
                Log.e("PowerManagerAPI", "Error showing battery optimization dialog", e)
            }
        }
    }

    private fun showBatteryOptimizationDialog(context: Context) {
        AlertDialog.Builder(context)
            .setTitle(R.string.battery_dialog_title)
            .setMessage(R.string.battery_dialog_message)
            .setPositiveButton(R.string.ok) { _, _ ->
                val intent = Intent()
                intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                intent.data = Uri.fromParts("package", packageName, null)
                context.startActivity(intent, Bundle())
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun isPhoneAndRestricted(): Boolean {
        return SettingsFragment.isTruePhone() && isAppRestricted(context)
    }
}
