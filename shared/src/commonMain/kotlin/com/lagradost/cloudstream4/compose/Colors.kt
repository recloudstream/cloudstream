package com.lagradost.cloudstream4.compose

import androidx.compose.material3.ButtonColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import com.lagradost.cloudstream4.theme.CloudStreamTheme.colors

object Colors {
    val blackButton
        @Composable @ReadOnlyComposable get() = ButtonColors(
            containerColor = colors.surfaceVariant,
            contentColor = colors.onBackground,
            disabledContainerColor = colors.surface.copy(alpha = 0.9f),
            disabledContentColor = colors.onBackground.copy(alpha = 0.9f)
        )

    val whiteButton
        @Composable @ReadOnlyComposable get() = ButtonColors(
            containerColor = colors.onBackground,
            contentColor = colors.surfaceVariant,
            disabledContainerColor = colors.onBackground.copy(alpha = 0.9f),
            disabledContentColor = colors.surface.copy(alpha = 0.9f)
        )
}