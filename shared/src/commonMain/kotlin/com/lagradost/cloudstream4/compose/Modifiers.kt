package com.lagradost.cloudstream4.compose

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/*
@Composable
fun Modifier.ripple(
    interactionSource: MutableInteractionSource,
    bounded: Boolean = true,
): Modifier = indication(
    interactionSource = interactionSource,
    indication = ripple(bounded = bounded, color = MaterialTheme.colorScheme.onBackground),
)*/

// no dimens.xml allowed in compose :/
fun RoundedShape() = RoundedCornerShape(10.dp)

@Composable
fun Modifier.rounded(): Modifier =
    clip(RoundedShape())


@Composable
fun Modifier.circle(): Modifier =
    clip(CircleShape)

/**
 * If we should have outlines on items when focused by default, this should be false for desktop
 * as we do not use a dpad for that
 * */
val LocalFocusOutlineDefault: ProvidableCompositionLocal<Boolean> = staticCompositionLocalOf { true }

@Composable
fun Modifier.focusOutline(
    enabled : Boolean = LocalFocusOutlineDefault.current,
    shape: Shape = RoundedShape()
): Modifier {
    if(!enabled) return this

    var hasFocus by remember { mutableStateOf(false) }

    return this.onFocusChanged { newFocus ->
        hasFocus = newFocus.isFocused
    }.whiteOutline(hasFocus = hasFocus, shape = shape)
}

@Composable
fun Modifier.whiteOutline(hasFocus: Boolean, shape: Shape = RoundedShape()): Modifier {
    return if (hasFocus) {
        this.border(
            width = 1.dp,
            color = MaterialTheme.colorScheme.onBackground,
            shape = shape
        )
    } else {
        this
    }
}