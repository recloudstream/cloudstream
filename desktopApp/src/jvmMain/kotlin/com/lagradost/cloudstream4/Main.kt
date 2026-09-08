package com.lagradost.cloudstream4

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.lagradost.cloudstream4.compose.BlackButton
import com.lagradost.cloudstream4.compose.WhiteButton
import com.lagradost.cloudstream4.generated.resources.Res
import com.lagradost.cloudstream4.generated.resources.app_name
import com.lagradost.cloudstream4.generated.resources.default_icon
import com.lagradost.cloudstream4.generated.resources.preview
import com.lagradost.cloudstream4.theme.CloudStreamTheme
import com.lagradost.cloudstream4.theme.CloudStreamTheme.colors
import com.lagradost.cloudstream4.theme.CloudStreamThemeMode
import com.mihon.presentation.settings.widget.SwitchPreferenceWidget
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = stringResource(Res.string.app_name),
        icon = painterResource(Res.drawable.default_icon)
    ) {
        CloudStreamTheme(mode = CloudStreamThemeMode.Dark) {
            Scaffold(
                containerColor = colors.background,
                contentColor = colors.onBackground
            ) {
                Column {
                    Text("Hello, World!")
                    Row {
                        WhiteButton("Hello in White") {
                        }
                        BlackButton("Hello in Black") {
                        }
                    }
                    var checked by remember { mutableStateOf(false) }
                    SwitchPreferenceWidget(
                        title = "hello", subtitle = "world", icon = painterResource(
                            Res.drawable.preview
                        ),
                        checked = checked,
                        onCheckedChanged = { checked = !checked }
                    )
                }
            }
        }
    }
}