package com.lagradost.cloudstream4.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

@Composable
@ReadOnlyComposable
expect fun resolveDynamicPrimaryColor(): Color

@Composable
@ReadOnlyComposable
expect fun resolveDynamicSecondaryColor(): Color
