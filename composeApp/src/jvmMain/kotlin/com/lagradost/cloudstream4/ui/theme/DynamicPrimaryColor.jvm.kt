package com.lagradost.cloudstream4.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
actual fun resolveDynamicPrimaryColor(): Color = CloudStreamPrimaryColor.NORMAL.color

@Composable
actual fun resolveDynamicSecondaryColor(): Color = CloudStreamPrimaryColor.NORMAL.color
