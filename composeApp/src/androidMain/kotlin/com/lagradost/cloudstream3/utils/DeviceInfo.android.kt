package com.lagradost.cloudstream3

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.preference.PreferenceManager
import com.lagradost.api.getContext
import com.lagradost.cloudstream3.preferences.PreferenceDefaults
import com.lagradost.cloudstream3.preferences.PreferenceKeys

internal actual object DeviceInfo {
    actual fun getDeviceType(): DeviceType {
        val context = getContext() as? Context ?: return DeviceType.PHONE
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager?
        val isTelevisionMode = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
        val model = Build.MODEL.lowercase()
        return when {
            isTelevisionMode
                || Build.MODEL.contains("AFT") // AFT = Fire TV
                || model.contains("firestick")
                || model.contains("fire tv")
                || model.contains("chromecast") -> DeviceType.TV
            else -> DeviceType.PHONE
        }
    }

    actual fun isLandscape(): Boolean {
        val context = getContext() as? Context ?: return false
        return context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    }

    actual fun getLayoutPreference(): Int {
        val context = getContext() as? Context ?: return PreferenceDefaults.APP_LAYOUT
        return PreferenceManager.getDefaultSharedPreferences(context)
            .getInt(PreferenceKeys.APP_LAYOUT, PreferenceDefaults.APP_LAYOUT)
    }
}
