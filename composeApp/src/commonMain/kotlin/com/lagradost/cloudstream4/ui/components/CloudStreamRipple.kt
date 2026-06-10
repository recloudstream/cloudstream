package com.lagradost.cloudstream4.ui.components

import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ripple
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import com.lagradost.cloudstream4.ui.theme.CloudStreamTheme

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
