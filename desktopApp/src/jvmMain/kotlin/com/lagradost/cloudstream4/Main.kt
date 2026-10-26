package com.lagradost.cloudstream4

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.disk.DiskCache
import coil3.network.ktor3.KtorNetworkFetcherFactory
import com.lagradost.cloudstream4.compose.LocalFocusOutlineDefault
import com.lagradost.cloudstream4.desktop.data.AppData
import com.lagradost.cloudstream4.desktop.data.AppDirs
import com.lagradost.cloudstream4.desktop.ui.App
import com.lagradost.cloudstream4.desktop.update.Updater
import com.lagradost.cloudstream4.desktop.ui.LocalWindowState
import com.lagradost.cloudstream4.desktop.ui.Navigator
import com.lagradost.cloudstream4.desktop.ui.Route
import com.lagradost.cloudstream4.generated.resources.Res
import com.lagradost.cloudstream4.generated.resources.app_name
import com.lagradost.cloudstream4.generated.resources.default_icon
import com.lagradost.cloudstream4.theme.CloudStreamTheme
import okio.Path.Companion.toOkioPath
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

fun main() {
    System.setProperty("apple.awt.application.name", "CloudStream")
    System.setProperty("apple.awt.application.appearance", "system")
    // Covers closing the window, Cmd+Q and logging out
    Runtime.getRuntime().addShutdownHook(Thread { Updater.installOnExitIfReady() })

    application {
        setSingletonImageLoaderFactory { context ->
            ImageLoader.Builder(context)
                .components { add(KtorNetworkFetcherFactory()) }
                .diskCache {
                    DiskCache.Builder()
                        .directory(AppDirs.cacheDir.resolve("images").toOkioPath())
                        .maxSizeBytes(256L * 1024 * 1024)
                        .build()
                }
                .build()
        }

        val navigator = remember { Navigator() }
        val windowState = rememberWindowState(width = 1280.dp, height = 820.dp)
        val settings by AppData.settings.collectAsState()

        Window(
            onCloseRequest = ::exitApplication,
            title = stringResource(Res.string.app_name),
            icon = painterResource(Res.drawable.default_icon),
            state = windowState,
            onPreviewKeyEvent = { event ->
                // Escape/Alt+Left/Cmd+[ go back everywhere except the player, which handles its own keys
                val isBack = event.type == KeyEventType.KeyDown && (
                    event.key == Key.Escape ||
                        (event.isAltPressed && event.key == Key.DirectionLeft) ||
                        (event.isMetaPressed && event.key == Key.LeftBracket)
                    )
                isBack && navigator.current !is Route.Player && navigator.back()
            },
        ) {
            window.minimumSize = java.awt.Dimension(800, 560)
            CloudStreamTheme(mode = settings.theme, primaryColor = settings.primaryColor) {
                CompositionLocalProvider(LocalFocusOutlineDefault provides false, LocalWindowState provides windowState) {
                    App(navigator)
                }
            }
        }
    }
}
