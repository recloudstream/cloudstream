package com.lagradost.cloudstream3.compose.theme

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun resolveDynamicPrimaryColor(): Color {
    val resources = LocalContext.current.resources
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        Color(resources.getColor(android.R.color.system_accent1_200, null))
    else CloudStreamPrimaryColor.NORMAL.color
}

@Composable
actual fun resolveDynamicSecondaryColor(): Color {
    val resources = LocalContext.current.resources
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        Color(resources.getColor(android.R.color.system_accent2_200, null))
    else CloudStreamPrimaryColor.NORMAL.color
}
