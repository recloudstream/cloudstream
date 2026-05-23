package com.lagradost.cloudstream3.compose.components

import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ripple
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import com.lagradost.cloudstream3.compose.theme.CloudStreamTheme

/**
 * Applies a ripple indication styled to match the app's selected theme.
 */
fun Modifier.cloudStreamRipple(
    interactionSource: MutableInteractionSource,
    bounded: Boolean = true,
): Modifier = composed {
    val colors = CloudStreamTheme.colors
    this.indication(
        interactionSource = interactionSource,
        indication = ripple(bounded = bounded, color = colors.onBackground),
    )
}
