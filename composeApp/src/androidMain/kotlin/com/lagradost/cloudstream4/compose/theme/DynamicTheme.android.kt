package com.lagradost.cloudstream4.compose.theme

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun resolveDynamicTheme(): CloudStreamColorScheme {
    val context = LocalContext.current
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        context.buildMonetScheme()
    else darkScheme()
}

@RequiresApi(Build.VERSION_CODES.S)
fun Context.buildMonetScheme(): CloudStreamColorScheme {
    val isSystemDark = (resources.configuration.uiMode and
        Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    return if (isSystemDark) {
        CloudStreamColorScheme(
            background = Color(getColor(android.R.color.system_neutral1_900)),
            surfaceVariant = Color(getColor(android.R.color.system_neutral1_800)),
            surface = Color(getColor(android.R.color.system_neutral1_800)),
            surfaceContainer = Color(getColor(android.R.color.system_neutral1_800)),
            onBackground = Color(getColor(android.R.color.system_neutral1_100)),
            onSurfaceVariant = Color(getColor(android.R.color.system_neutral2_400)),
            icon = Color(getColor(android.R.color.system_neutral1_100)),
            primary = Color(getColor(android.R.color.system_accent1_200)),
            ongoing = CloudStreamPalette.Ongoing,
            isLight = false,
        )
    } else {
        CloudStreamColorScheme(
            background = Color(getColor(android.R.color.system_neutral1_10)),
            surfaceVariant = Color(getColor(android.R.color.system_neutral1_100)),
            surface = Color(getColor(android.R.color.system_neutral1_100)),
            surfaceContainer = Color(getColor(android.R.color.system_neutral1_100)),
            onBackground = Color(getColor(android.R.color.system_neutral1_900)),
            onSurfaceVariant = Color(getColor(android.R.color.system_neutral2_600)),
            icon = Color(getColor(android.R.color.system_neutral1_900)),
            primary = Color(getColor(android.R.color.system_accent1_600)),
            ongoing = CloudStreamPalette.Ongoing,
            isLight = true,
        )
    }
}
