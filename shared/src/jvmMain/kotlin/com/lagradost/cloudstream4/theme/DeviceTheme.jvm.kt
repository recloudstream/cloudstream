package com.lagradost.cloudstream4.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

internal actual object DeviceTheme {
    @Composable
    @ReadOnlyComposable
    actual fun resolveDynamicTheme(): CloudStreamColorScheme = darkScheme()

    @Composable
    @ReadOnlyComposable
    actual fun resolveDynamicPrimaryColor(): Color = CloudStreamPrimaryColor.NORMAL.color

    @Composable
    @ReadOnlyComposable
    actual fun resolveDynamicSecondaryColor(): Color = CloudStreamPrimaryColor.NORMAL.color
}