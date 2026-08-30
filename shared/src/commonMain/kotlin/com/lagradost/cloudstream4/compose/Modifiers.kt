package com.lagradost.cloudstream4.compose

import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream4.theme.CloudStreamTheme.colors

@Composable
fun Modifier.ripple(
    interactionSource: MutableInteractionSource,
    bounded: Boolean = true,
): Modifier = indication(
    interactionSource = interactionSource,
    indication = ripple(bounded = bounded, color = colors.onBackground),
)

// no dimens.xml allowed in compose :/
fun RoundedShape() = RoundedCornerShape(10.dp)

@Composable
fun Modifier.rounded(): Modifier =
    clip(RoundedShape())


@Composable
fun Modifier.circle(): Modifier =
    clip(CircleShape)