package com.lagradost.cloudstream4.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

@Composable
@ReadOnlyComposable
actual fun resolveDynamicPrimaryColor(): Color = CloudStreamPrimaryColor.NORMAL.color

@Composable
@ReadOnlyComposable
actual fun resolveDynamicSecondaryColor(): Color = CloudStreamPrimaryColor.NORMAL.color
