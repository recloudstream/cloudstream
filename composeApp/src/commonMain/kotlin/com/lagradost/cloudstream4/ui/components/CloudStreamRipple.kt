package com.lagradost.cloudstream4.ui.components

import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lagradost.cloudstream4.ui.theme.CloudStreamTheme

/**
 * Applies a ripple indication styled to match the app's selected theme.
 */
@Composable
fun Modifier.cloudStreamRipple(
    interactionSource: MutableInteractionSource,
    bounded: Boolean = true,
): Modifier {
    val colors = CloudStreamTheme.colors
    return this.indication(
        interactionSource = interactionSource,
        indication = ripple(bounded = bounded, color = colors.onBackground),
    )
}
