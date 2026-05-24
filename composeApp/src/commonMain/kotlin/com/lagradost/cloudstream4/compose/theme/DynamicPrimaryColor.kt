package com.lagradost.cloudstream4.compose.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
expect fun resolveDynamicPrimaryColor(): Color

@Composable
expect fun resolveDynamicSecondaryColor(): Color
