package com.lagradost.cloudstream4

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import coil3.compose.setSingletonImageLoaderFactory
import com.lagradost.cloudstream4.browse.desktopImageLoader
import com.lagradost.cloudstream4.compose.LocalFocusOutlineDefault
import com.lagradost.cloudstream4.generated.resources.Res
import com.lagradost.cloudstream4.generated.resources.app_name
import com.lagradost.cloudstream4.generated.resources.default_icon
import com.lagradost.cloudstream4.android.DesktopAndroid
import com.lagradost.cloudstream4.browser.SystemBrowser
import com.lagradost.cloudstream4.network.DesktopHttp
import com.lagradost.cloudstream4.plugins.DesktopPluginManager
import com.lagradost.cloudstream4.settings.AppUpdatePrompt
import com.lagradost.cloudstream4.settings.autoUpdateExtensions
import com.lagradost.cloudstream4.settings.desktopPrimaryColor
import com.lagradost.cloudstream4.settings.desktopThemeMode
import com.lagradost.cloudstream4.theme.BlackButton
import com.lagradost.cloudstream4.theme.CloudStreamTheme
import com.lagradost.cloudstream4.theme.WhiteButton
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

fun main() {
    // Before anything opens the app's files, which a reset may delete
    val appData = AppData.instance
    appData.runPendingReset()
    if (appData.isReinstallWithOldData() && askToStartFresh()) appData.reset(AppData.Reset.EVERYTHING)
    appData.rememberInstall()

    DesktopLog.install(AppDirs.logs)
    // Extensions and the library keep their data in the Android data store, see DataStoreWatchStore
    DesktopAndroid.init(AppDirs.root.resolve("android"))
    // Before the extensions load: they make their requests through it
    @OptIn(DelicateCoroutinesApi::class)
    DesktopHttp.start(GlobalScope)
    // Edge or Chrome, without a window, stands in for Android's WebView, for extensions behind Cloudflare and the like.
    // It only shows a window when the user asks to pass a site's check, and lets that window come in front of the app
    SystemBrowser.install()?.onShow = WindowsForeground::allow
    // Plugins load in the background so a slow or broken plugin never delays the window
    @OptIn(DelicateCoroutinesApi::class)
    GlobalScope.launch(Dispatchers.IO) {
        val manager = DesktopPluginManager.instance
        manager.loadAll()
        if (autoUpdateExtensions.get()) {
            val updates = runCatching { manager.findUpdates() }.getOrDefault(emptyList())
            if (updates.isNotEmpty()) {
                val failed = manager.installUpdates(updates)
                println("INFO Main: Updated ${updates.size - failed.size} extensions" + if (failed.isEmpty()) "" else ", failed: $failed")
            }
        }
    }
    application {
        setSingletonImageLoaderFactory(::desktopImageLoader)
        val savedWindow = remember { SavedWindowState(desktopPreferences) }
        val windowState = rememberSavedWindowState(savedWindow)
        val navigator = remember { AppNavigator() }
        var appWindow by remember { mutableStateOf<java.awt.Window?>(null) }
        val quit = {
            // So the window's normal size is saved, not the whole screen
            appWindow?.let(WindowsFullscreen::exit)
            savedWindow.save(windowState)
            exitApplication()
        }
        val icon = painterResource(Res.drawable.default_icon)
        Window(
            onCloseRequest = quit,
            state = windowState,
            visible = WindowsHide.supported || !navigator.hidden,
            title = stringResource(Res.string.app_name),
            icon = icon,
            onPreviewKeyEvent = navigator::onKeyEvent,
            onKeyEvent = navigator::onUnhandledKeyEvent,
        ) {
            appWindow = window
            MainContent(navigator, window)
            FullscreenEffect(navigator, windowState, window)
            LaunchedEffect(navigator.hidden) {
                if (WindowsHide.supported) {
                    if (navigator.hidden) WindowsHide.hide(window) else WindowsHide.show(window)
                }
                if (navigator.hidden) return@LaunchedEffect
                window.toFront()
                // A window shown again doesn't hand the keys back to its content by itself, and the
                // window itself taking them leaves every shortcut dead
                (window.mostRecentFocusOwner ?: window.focusTraversalPolicy?.getDefaultComponent(window))?.requestFocus()
            }
        }
        // While the app is hidden, the only way back. Double click, or right click for the menu
        if (navigator.hidden) Tray(
            icon = icon,
            tooltip = stringResource(Res.string.app_name),
            onAction = { navigator.hidden = false },
            menu = {
                Item("Show CloudStream", onClick = { navigator.hidden = false })
                Item("Quit", onClick = quit)
            },
        )
    }
}

/**
 * After CloudStream was installed again, asks whether to keep the data from before or start fresh.
 * Shown in a window of its own before the app opens anything. Closing it keeps the data.
 */
private fun askToStartFresh(): Boolean {
    var fresh = false
    application(exitProcessOnExit = false) {
        DialogWindow(
            onCloseRequest = ::exitApplication,
            state = rememberDialogState(size = DpSize(480.dp, 260.dp)),
            title = "CloudStream",
            icon = painterResource(Res.drawable.default_icon),
            resizable = false,
        ) {
            CloudStreamTheme {
                ThemedFrame(window)
                Surface(color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
                    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Welcome back", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "CloudStream was installed again and found your data from before: settings, repositories " +
                                    "and extensions. Keep it, or start fresh as if this were a new install?",
                            modifier = Modifier.weight(1f),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            WhiteButton(onClick = ::exitApplication) { Text("Keep my data") }
                            BlackButton(onClick = {
                                fresh = true
                                exitApplication()
                            }) { Text("Start fresh") }
                        }
                    }
                }
            }
        }
    }
    return fresh
}

/**
 * Puts the window in full screen while the player asks for it, then back to how it was. On
 * Windows without the title bar, elsewhere with Compose's own full screen.
 */
@Composable
private fun FullscreenEffect(navigator: AppNavigator, windowState: WindowState, window: java.awt.Window) {
    var before by remember { mutableStateOf(windowState.placement) }
    LaunchedEffect(navigator.fullscreen) {
        if (WindowsFullscreen.supported) {
            if (navigator.fullscreen) WindowsFullscreen.enter(window) else WindowsFullscreen.exit(window)
            // The frame comes and goes without the window's size changing when it was maximized, so
            // nothing lays it out again: the content kept the frame's place, and the video moved apart
            // from the bars drawn around it. Once Windows has taken the frame away or put it back
            var waited = 0
            while ((window.insets.top == 0) != navigator.fullscreen && waited++ < 20) delay(25)
            window.revalidate()
        } else if (navigator.fullscreen) {
            before = windowState.placement
            windowState.placement = WindowPlacement.Fullscreen
        } else if (windowState.placement == WindowPlacement.Fullscreen) {
            windowState.placement = before
        }
    }
}

@Composable
private fun MainContent(navigator: AppNavigator, window: java.awt.Window) {
    val ui = rememberAppSettings().ui
    // Changing the theme or color in settings applies right away
    val theme by ui.theme.changes().collectAsState(ui.theme.get())
    val primaryColor by ui.primaryColor.changes().collectAsState(ui.primaryColor.get())

    CloudStreamTheme(mode = desktopThemeMode(theme), primaryColor = desktopPrimaryColor(primaryColor)) {
        ThemedFrame(window)
        CompositionLocalProvider(LocalFocusOutlineDefault provides false) {
            Surface(
                color = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onBackground,
            ) {
                AppShell(navigator)
                AppUpdatePrompt()
            }
        }
    }
}

/** The window's title bar and border in the theme's colours, again whenever the theme changes */
@Composable
private fun ThemedFrame(window: java.awt.Window) {
    val background = MaterialTheme.colorScheme.background
    val text = MaterialTheme.colorScheme.onBackground
    LaunchedEffect(window, background, text) { WindowsTitleBar.paint(window, background, text) }
}
