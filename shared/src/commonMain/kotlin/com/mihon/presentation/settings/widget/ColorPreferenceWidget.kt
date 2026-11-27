package com.mihon.presentation.settings.widget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream4.compose.ColorCircle
import com.lagradost.cloudstream4.compose.ColorDialog

@Composable
fun ColorPreferenceWidget(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String? = null,
    icon: Painter? = null,
    color : Color,
    onValueChange: (Color) -> Unit,
) {
    var isDialogShown by remember { mutableStateOf(false) }

    TextPreferenceWidget(
        modifier = modifier,
        title = title,
        subtitle = subtitle,
        icon = icon,
        onPreferenceClick = {
            isDialogShown = true
        },
        widget = {
            ColorCircle(color, size = 30.dp)
        }
    )

    if (isDialogShown) {
        ColorDialog(
            title = title,
            color = color,
            dismiss = { isDialogShown = false },
            confirm = { color ->
                isDialogShown = false
                onValueChange(color)
            }
        )
    }
}