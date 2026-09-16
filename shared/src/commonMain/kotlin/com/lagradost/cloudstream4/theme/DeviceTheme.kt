package com.lagradost.cloudstream4.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

internal expect object DeviceTheme {
    @Composable
    @ReadOnlyComposable
    fun resolveDynamicTheme(): CloudStreamColorScheme
    @Composable
    @ReadOnlyComposable
    fun resolveDynamicPrimaryColor(): Color
    @Composable
    @ReadOnlyComposable
    fun resolveDynamicSecondaryColor(): Color
}