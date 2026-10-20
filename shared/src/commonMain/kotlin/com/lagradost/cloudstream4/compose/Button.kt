package com.lagradost.cloudstream4.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream4.generated.resources.Res
import com.lagradost.cloudstream4.generated.resources.check
import org.jetbrains.compose.resources.painterResource


@Composable
fun WhiteButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    BaseButton(
        text = text,
        onClick = onClick,
        buttonColors = Colors.whiteButton,
        modifier = modifier
    )
}

@Composable
fun BlackButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    BaseButton(
        text = text,
        onClick = onClick,
        buttonColors = Colors.blackButton,
        modifier = modifier
    )
}

@Composable
fun IconButtonOutline(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    shape: Shape = IconButtonDefaults.standardShape,
    content: @Composable () -> Unit,
) =
    IconButton(
        onClick = onClick,
        modifier = modifier.focusOutline(shape = CircleShape),
        enabled = enabled,
        colors = colors,
        interactionSource = interactionSource,
        shape = shape,
        content = content
    )

@Composable
fun BaseButton(
    text: String, onClick: () -> Unit, buttonColors: ButtonColors,
    modifier: Modifier = Modifier,
) {
    var hasFocus by remember { mutableStateOf(false) }
    val canHaveFocus = LocalFocusOutlineDefault.current
    Button(
        onClick = onClick,
        colors = buttonColors,
        modifier = modifier.onFocusChanged { newFocus ->
            hasFocus = canHaveFocus && newFocus.hasFocus
        },
        border = if (hasFocus) BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground) else null
    ) {
        AnimatedVisibility(hasFocus) {
            Icon(
                painter = painterResource(Res.drawable.check),
                modifier = Modifier.padding(end = 5.dp),
                contentDescription = null
            )
        }
        Text(text = text)
    }
}