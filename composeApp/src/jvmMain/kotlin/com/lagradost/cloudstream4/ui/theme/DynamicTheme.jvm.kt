package com.lagradost.cloudstream4.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable

@Composable
@ReadOnlyComposable
actual fun resolveDynamicTheme(): CloudStreamColorScheme = darkScheme()
