package com.lagradost.cloudstream4.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream4.ui.theme.CloudStreamTheme

/**
 * Reusable TV focusable modifier with built-in focus border.
 *
 * @param isTV Whether to use TV focus behavior
 * @param onClick Action to perform when item is clicked/selected
 * @param focusRequester Optional external FocusRequester
 * @param onFocusChanged Optional callback when focus state changes
 * @param shape Shape of the focus border
 * @param interactionSource MutableInteractionSource for ripple indication
 */
@Composable
fun Modifier.tvFocusable(
    isTV: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
    onFocusChanged: ((Boolean) -> Unit)? = null,
    shape: RoundedCornerShape = RoundedCornerShape(4.dp),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
): Modifier {
    val colors = CloudStreamTheme.colors
    var isFocused by remember { mutableStateOf(false) }
    val focusRequesterLocal = remember { FocusRequester() }
    val effectiveFocusRequester = focusRequester ?: focusRequesterLocal

    return if (isTV) {
        this
            .focusRequester(effectiveFocusRequester)
            .onFocusChanged {
                isFocused = it.isFocused
                onFocusChanged?.invoke(it.isFocused)
            }
            .focusable()
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) colors.onBackground else Color.Transparent,
                shape = shape,
            )
            .pointerInput(isFocused) {
                detectTapGestures(
                    onPress = {
                        val press = PressInteraction.Press(it)
                        interactionSource.emit(press)
                        tryAwaitRelease()
                        interactionSource.emit(PressInteraction.Release(press))
                    },
                    onTap = {
                        if (!isFocused) effectiveFocusRequester.requestFocus() else onClick()
                    },
                )
            }
    } else {
        this.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick,
        )
    }
}
