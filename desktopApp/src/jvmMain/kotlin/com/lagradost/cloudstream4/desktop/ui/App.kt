package com.lagradost.cloudstream4.desktop.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.cloudstream4.desktop.generated.resources.Res
import com.lagradost.cloudstream4.desktop.generated.resources.download
import com.lagradost.cloudstream4.desktop.generated.resources.extension
import com.lagradost.cloudstream4.desktop.generated.resources.home
import com.lagradost.cloudstream4.desktop.generated.resources.library
import com.lagradost.cloudstream4.desktop.generated.resources.search
import com.lagradost.cloudstream4.desktop.generated.resources.settings
import com.lagradost.cloudstream4.desktop.data.AppData
import com.lagradost.cloudstream4.desktop.plugins.PluginManager
import com.lagradost.cloudstream4.desktop.update.UpdateState
import com.lagradost.cloudstream4.desktop.update.Updater
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

private val Tab.icon: DrawableResource
    get() = when (this) {
        Tab.Home -> Res.drawable.home
        Tab.Search -> Res.drawable.search
        Tab.Library -> Res.drawable.library
        Tab.Downloads -> Res.drawable.download
        Tab.Extensions -> Res.drawable.extension
        Tab.Settings -> Res.drawable.settings
    }

@Composable
fun App(navigator: Navigator) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val notify: (String) -> Unit = { message -> scope.launch { snackbar.showSnackbar(message) } }

    // Screen state that should survive switching tabs
    val home = remember { HomeViewModel(scope) }
    val search = remember { SearchViewModel(scope) }
    val extensions = remember { ExtensionsViewModel(scope, notify) }

    // Extensions show toasts through CommonActivity
    LaunchedEffect(Unit) { CommonActivity.toasts.collect { notify(it) } }

    var loadingPlugins by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        PluginManager.loadAll()
        loadingPlugins = false
        val failed = PluginManager.errors.value.size
        if (failed > 0) notify("$failed extensions failed to load, see the Extensions tab")
        if (AppData.settings.value.checkForUpdates) Updater.check()
    }

    // Offer the update once it is downloaded (or found, when automatic updates are off)
    LaunchedEffect(Unit) {
        Updater.state.collect { update ->
            val (message, action, onAction) = when (update) {
                is UpdateState.Ready -> Triple("CloudStream ${update.update.version} is ready to install", "Restart", Updater::installAndRestart)
                is UpdateState.Available -> Triple("CloudStream ${update.update.version} is available", "Download") {
                    if (Updater.canInstall) Updater.download(update.update) else openInBrowser(update.update.asset.downloadUrl)
                }
                else -> return@collect
            }
            scope.launch {
                val result = snackbar.showSnackbar(message, actionLabel = action, duration = SnackbarDuration.Indefinite, withDismissAction = true)
                if (result == SnackbarResult.ActionPerformed) onAction()
            }
        }
    }

    val stateHolder = rememberSaveableStateHolder()
    val route = navigator.current

    CompositionLocalProvider(LocalNavigator provides navigator, LocalSnackbar provides snackbar) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
            if (route is Route.Player) {
                // The player takes over the whole window
                PlayerScreen(route)
                return@Scaffold
            }
            Row(Modifier.fillMaxSize()) {
                NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                    Tab.entries.forEach { tab ->
                        NavigationRailItem(
                            selected = navigator.tab == tab && route == null,
                            onClick = { navigator.select(tab) },
                            icon = { Icon(painterResource(tab.icon), tab.title) },
                            label = { Text(tab.title) },
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                }
                VerticalDivider()
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    when (route) {
                        is Route.Result -> stateHolder.SaveableStateProvider(route.toString()) { ResultScreen(route.media) }
                        null -> stateHolder.SaveableStateProvider(navigator.tab.name) {
                            when (navigator.tab) {
                                Tab.Home -> HomeScreen(home)
                                Tab.Search -> SearchScreen(search)
                                Tab.Library -> LibraryScreen()
                                Tab.Downloads -> DownloadsScreen()
                                Tab.Extensions -> ExtensionsScreen(extensions)
                                Tab.Settings -> SettingsScreen()
                            }
                        }

                        is Route.Player -> Unit
                    }
                    if (loadingPlugins) {
                        LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
                    }
                }
            }
        }
    }
}
