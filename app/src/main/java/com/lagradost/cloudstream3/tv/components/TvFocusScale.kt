package com.lagradost.cloudstream3.tv.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.CardGlow
import androidx.tv.material3.CardScale
import androidx.tv.material3.Glow
import androidx.tv.material3.MaterialTheme

/**
 * Reusable TV focus scale / glow tokens — obvious at 10ft, D-pad friendly.
 * Uses only androidx.tv.material3 APIs available in tv-material 1.1.0.
 */
object TvFocusScale {
    const val CardFocused = 1.12f
    const val CardPressed = 1.04f
    const val ButtonFocused = 1.08f
    const val HeroButtonFocused = 1.06f

    fun cardScale(
        focused: Float = CardFocused,
        pressed: Float = CardPressed,
    ): CardScale = CardDefaults.scale(
        focusedScale = focused,
        pressedScale = pressed,
    )

    @Composable
    fun cardGlow(
        focusedColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
        elevation: androidx.compose.ui.unit.Dp = 14.dp,
    ): CardGlow = CardDefaults.glow(
        focusedGlow = Glow(elevationColor = focusedColor, elevation = elevation),
        pressedGlow = Glow(elevationColor = focusedColor.copy(alpha = 0.35f), elevation = 8.dp),
    )

    @Composable
    fun cardBorder(
        focusedColor: Color = MaterialTheme.colorScheme.primary,
    ) = CardDefaults.border(
        focusedBorder = Border(
            border = androidx.compose.foundation.BorderStroke(3.dp, focusedColor),
            shape = MaterialTheme.shapes.medium,
        ),
    )
}
