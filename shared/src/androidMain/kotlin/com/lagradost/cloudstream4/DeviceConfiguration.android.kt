package com.lagradost.cloudstream4

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import com.lagradost.api.getContext

internal actual object DeviceConfiguration {
    fun isAutoTv(context: Context): Boolean {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager?
        // AFT = Fire TV
        val model = Build.MODEL.lowercase()
        return uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION || Build.MODEL.contains(
            "AFT"
        ) || model.contains("firestick") || model.contains("fire tv") || model.contains("chromecast")
    }

    internal actual val defaultLayout: DeviceLayout
        get() = if (isAutoTv(getContext() as Context)) DeviceLayout.TV else DeviceLayout.PHONE

    /** also `val isLandscape : Boolean @Composable @ReadOnlyComposable get() = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE` */
    internal actual val isLandscape: Boolean
        get() = Resources.getSystem().configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
}
