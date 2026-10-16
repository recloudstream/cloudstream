package com.lagradost.cloudstream4.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * Frosted glass in the theme's colours, floating over the page: what is behind it shows blurred
 * through, as on the dock and the Home page's bar. [state] is what it blurs, marked with hazeSource.
 */
@Composable
fun Modifier.glass(state: HazeState, shape: Shape): Modifier {
    val background = MaterialTheme.colorScheme.background
    val light = background.luminance() > 0.5f
    // A light edge along the top, where light would catch the glass; on a light theme a faint dark
    // one, as a white edge would not show
    val edge = if (light) {
        listOf(Color.Black.copy(alpha = 0.10f), Color.Black.copy(alpha = 0.05f))
    } else {
        listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.04f))
    }
    val shadow = Color.Black.copy(alpha = if (light) 0.35f else 1f)
    return this
        .shadow(if (light) 16.dp else 24.dp, shape, ambientColor = shadow, spotColor = shadow)
        .clip(shape)
        .hazeEffect(
            state,
            HazeStyle(
                backgroundColor = background,
                tint = HazeTint(background.copy(alpha = if (light) 0.65f else 0.55f)),
                blurRadius = 28.dp,
                noiseFactor = 0.06f,
            ),
        )
        .border(1.dp, Brush.verticalGradient(edge), shape)
}

/**
 * A button on glass: a pill, or a circle when [round], lit under the mouse and while [active], such
 * as while the menu it opens is open
 */
@Composable
fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    active: Boolean = false,
    round: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val onBackground = MaterialTheme.colorScheme.onBackground
    Row(
        modifier
            .height(44.dp)
            .then(if (round) Modifier.width(44.dp) else Modifier)
            .clip(RoundedCornerShape(percent = 50))
            .background(
                when {
                    active -> onBackground.copy(alpha = 0.12f)
                    hovered && enabled -> onBackground.copy(alpha = 0.08f)
                    else -> Color.Transparent
                },
            )
            .hoverable(interaction)
            .clickable(enabled = enabled, onClick = onClick)
            .then(if (round) Modifier else Modifier.padding(horizontal = 16.dp)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides onBackground.copy(alpha = if (enabled) 1f else 0.6f)) { content() }
    }
}

/** The name of a button on glass, under the mouse */
@Composable
fun GlassTooltip(text: String) {
    Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp) {
        Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
    }
}
