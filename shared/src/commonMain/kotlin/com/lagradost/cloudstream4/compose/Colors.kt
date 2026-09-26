package com.lagradost.cloudstream4.compose

import androidx.compose.material3.ButtonColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable

object Colors {
    internal val blackButton
        @Composable @ReadOnlyComposable get() = ButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onBackground,
            disabledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            disabledContentColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f)
        )

    internal val whiteButton
        @Composable @ReadOnlyComposable get() = ButtonColors(
            containerColor = MaterialTheme.colorScheme.onBackground,
            contentColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContainerColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f),
            disabledContentColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
        )
}