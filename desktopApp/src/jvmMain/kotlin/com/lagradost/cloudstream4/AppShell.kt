package com.lagradost.cloudstream4

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.animateContentSize
import com.lagradost.cloudstream4.browse.DockClearance
import com.lagradost.cloudstream4.browse.glass
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpOffset
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream4.generated.resources.*
import com.lagradost.cloudstream3.APIHolder
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lagradost.cloudstream4.android.DesktopAndroid
import com.lagradost.cloudstream4.browse.DetailScreen
import com.lagradost.cloudstream4.browse.DownloadsScreen
import com.lagradost.cloudstream4.download.Downloads
import com.lagradost.cloudstream4.browse.HomeScreen
import com.lagradost.cloudstream4.browse.LibraryScreen
import com.lagradost.cloudstream4.browse.Message
import com.lagradost.cloudstream4.detail.DetailViewModel
import com.lagradost.cloudstream4.detail.PlayRequest
import com.lagradost.cloudstream4.library.DataStoreWatchStore
import com.lagradost.cloudstream4.player.LinksViewModel
import com.lagradost.cloudstream4.player.PlayerScreen
import com.lagradost.cloudstream4.browse.SearchScreen
import com.lagradost.cloudstream4.home.HomeAction
import com.lagradost.cloudstream4.home.HomeViewModel
import com.lagradost.cloudstream4.plugins.DesktopPluginManager
import com.lagradost.cloudstream4.search.DataStoreSearchHistory
import com.lagradost.cloudstream4.search.SearchAction
import com.lagradost.cloudstream4.search.SearchViewModel
import com.lagradost.cloudstream4.settings.ExtensionsScreen
import com.lagradost.cloudstream4.settings.SettingsHomeScreen
import com.lagradost.cloudstream4.browser.SystemBrowser
import com.lagradost.cloudstream4.torrent.TorrServer
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.consumeAsFlow
import com.mihon.presentation.LocalBackPress
import com.mihon.presentation.settings.SearchableSettings
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

enum class Destination(
    val title: StringResource,
    val icon: DrawableResource,
    val selectedIcon: DrawableResource,
) {
    Home(Res.string.title_home, Res.drawable.home_icon_outline_24, Res.drawable.home_icon_filled_24),
    Search(Res.string.title_search, Res.drawable.search_icon, Res.drawable.search_icon),
    Library(Res.string.library, Res.drawable.library_icon, Res.drawable.library_icon_filled),
    Downloads(Res.string.title_downloads, Res.drawable.download_24px, Res.drawable.download_24px),
    Settings(Res.string.title_settings, Res.drawable.settings_icon_outline, Res.drawable.settings_icon_filled),
}

/** A page opened on top of a destination. It owns the view models made for it, cleared when it closes */
sealed class Page : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()

    class Details(val apiName: String, val url: String) : Page() {
        /** Play where the title was left as soon as it loads, once: Continue watching does */
        var autoPlay = false
    }
    class Player(val request: PlayRequest) : Page()
}

/**
 * Navigation for the desktop app: the selected destination, with a dock floating at the bottom to switch,
 * with pages such as details and the player opened on top.
 * Ctrl+1 to Ctrl+5 switch destination, Ctrl+F opens Search, Escape, Alt+Left or the mouse's back
 * button goes back. M pauses the player and hides the app, see [hidden].
 */
class AppNavigator {
    var destination by mutableStateOf(Destination.Home)
        private set

    /** Settings pages opened on top of the settings home, last is shown */
    val settingsStack = mutableStateListOf<SearchableSettings>()

    /** Details and player pages, last is shown */
    val pages = mutableStateListOf<Page>()

    /** The player fills the screen, without the window frame */
    var fullscreen by mutableStateOf(false)

    /**
     * Keys the open player handles before the app's own, whatever has the focus: its buttons come
     * and go, so focus cannot be relied on. Returns true for a key it used.
     */
    var playerKeys: ((KeyEvent) -> Boolean)? = null

    fun select(destination: Destination) {
        closePages()
        // Selecting Settings again returns to the settings home
        if (destination == Destination.Settings && this.destination == Destination.Settings) settingsStack.clear()
        this.destination = destination
    }

    fun openDetails(apiName: String, url: String) {
        pages.add(Page.Details(apiName, url))
    }

    /** Continue watching: plays the title where it was left, its page under the player to come back to */
    fun resume(apiName: String, url: String) {
        pages.add(Page.Details(apiName, url).apply { autoPlay = true })
    }

    fun play(request: PlayRequest) {
        pages.add(Page.Player(request))
    }

    fun toggleFullscreen() {
        fullscreen = !fullscreen && pages.lastOrNull() is Page.Player
    }

    private fun popPage() {
        val page = pages.removeAt(pages.lastIndex)
        if (page is Page.Player) fullscreen = false
        page.viewModelStore.clear()
    }

    private fun closePages() {
        while (pages.isNotEmpty()) popPage()
    }

    fun open(page: SearchableSettings) {
        settingsStack.add(page)
    }

    /** Settings > Extensions, where plugins are installed */
    fun openExtensions() {
        closePages()
        destination = Destination.Settings
        settingsStack.clear()
        settingsStack.add(ExtensionsScreen(::open))
    }

    /** Returns false when there is nothing to go back to */
    fun back(): Boolean {
        if (fullscreen) {
            fullscreen = false
            return true
        }
        if (pages.isNotEmpty()) {
            popPage()
            return true
        }
        if (destination == Destination.Settings && settingsStack.isNotEmpty()) {
            settingsStack.removeAt(settingsStack.lastIndex)
            return true
        }
        return false
    }

    /**
     * The whole app is out of sight, the window gone from the screen, the taskbar and Alt+Tab, after
     * M was pressed. The tray icon brings it back.
     */
    var hidden by mutableStateOf(false)

    private fun isHideKey(event: KeyEvent) = event.type == KeyEventType.KeyDown && event.key == Key.M &&
            !event.isCtrlPressed && !event.isAltPressed && !event.isMetaPressed && !event.isShiftPressed

    /** A text field being typed in gets the M press too, which must not hide the app halfway through a word */
    val typing = TypingTracker()

    fun onKeyEvent(event: KeyEvent): Boolean {
        if (pages.lastOrNull() is Page.Player && playerKeys?.invoke(event) == true) return true
        if (event.type != KeyEventType.KeyDown) return false
        if (event.isCtrlPressed) {
            if (event.key == Key.F) {
                select(Destination.Search)
                return true
            }
            val index = listOf(Key.One, Key.Two, Key.Three, Key.Four, Key.Five).indexOf(event.key)
            if (index >= 0) {
                select(Destination.entries[index])
                return true
            }
        }
        if (event.key == Key.Escape || (event.isAltPressed && event.key == Key.DirectionLeft)) return back()
        return false
    }

    /** Keys nothing on the page used. The open player has paused on M already */
    fun onUnhandledKeyEvent(event: KeyEvent): Boolean {
        if (!isHideKey(event) || typing.active) return false
        hidden = true
        return true
    }
}

/** All providers the loaded plugins registered */
private fun loadedProviders() = APIHolder.apis.withLock { APIHolder.apis.toList() }

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AppShell(navigator: AppNavigator) {
    val settings = rememberAppSettings()
    // Kept for the whole session, so switching destination keeps the results
    val home = remember { HomeViewModel(::loadedProviders, settings) }
    val search = remember { SearchViewModel(::loadedProviders, settings, DataStoreSearchHistory.instance) }
    // Plugins load in the background after the window opens, and the provider settings can change at any time
    LaunchedEffect(home) {
        combine(
            DesktopPluginManager.instance.loadedNames,
            settings.provider.extensionLanguages.changes(),
            settings.provider.preferredMedia.changes(),
        ) { _, _, _ -> }.collect { home.onAction(HomeAction.ProvidersChanged) }
    }

    val toasts = remember { SnackbarHostState() }
    ExtensionToasts(toasts)

    val page = navigator.pages.lastOrNull()
    // The player has its own controls along the bottom, which the dock would cover
    val showDock = page !is Page.Player
    // What the dock blurs behind its glass
    val glass = rememberHazeState()
    navigator.typing.Provide {
        Box(
            Modifier
                .fillMaxSize()
                // The mouse's back button goes back, as in a browser
                .onPointerEvent(PointerEventType.Press) { if (it.button == PointerButton.Back) navigator.back() },
        ) {
            Box(Modifier.fillMaxSize().hazeSource(glass)) {
                if (page != null) {
                    CompositionLocalProvider(LocalViewModelStoreOwner provides page) {
                        PageContent(page, navigator) { query ->
                            navigator.select(Destination.Search)
                            search.onAction(SearchAction.SearchFor(query))
                        }
                    }
                } else when (navigator.destination) {
                    Destination.Settings -> {
                        val settingsPage = navigator.settingsStack.lastOrNull()
                            ?: remember(navigator) { SettingsHomeScreen(navigator::open) }
                        val back: (() -> Unit)? = if (navigator.settingsStack.isEmpty()) null else ({ navigator.back() })
                        CompositionLocalProvider(LocalBackPress provides back) {
                            Box(Modifier.padding(bottom = DockClearance)) { settingsPage.Content() }
                        }
                    }

                    Destination.Home -> HomeScreen(home, navigator::openExtensions, navigator::openDetails, navigator::resume)
                    Destination.Search -> SearchScreen(search, navigator::openExtensions, navigator::openDetails)
                    Destination.Library -> LibraryScreen(DataStoreWatchStore.instance, navigator::openDetails)
                    Destination.Downloads -> DownloadsScreen(Downloads.instance, navigator::play, navigator::openDetails)
                    else -> ComingSoon(navigator.destination)
                }
            }
            if (showDock) FloatingDock(navigator, glass, Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp))
            SnackbarHost(toasts, Modifier.align(Alignment.BottomCenter).padding(start = 16.dp, end = 16.dp, bottom = if (showDock) DockClearance else 16.dp))
        }
    }
}

/**
 * Shows the toasts extensions raise, one after another. They can come from any thread, so they are
 * queued, and when many arrive at once the oldest are dropped.
 */
@Composable
private fun ExtensionToasts(host: SnackbarHostState) {
    val queue = remember { Channel<String>(capacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST) }
    DisposableEffect(queue) {
        DesktopAndroid.toastHandler = { queue.trySend(it) }
        onDispose { DesktopAndroid.toastHandler = null }
    }
    LaunchedEffect(queue) { queue.consumeAsFlow().collect { host.showSnackbar(it) } }
}

@Composable
private fun PageContent(page: Page, navigator: AppNavigator, onSearch: (query: String) -> Unit) {
    val apiName = when (page) {
        is Page.Details -> page.apiName
        is Page.Player -> page.request.apiName
    }
    // The provider can be gone if its plugin was uninstalled while the page was open. A download plays without it
    val api = remember(apiName) { APIHolder.getApiFromNameNull(apiName) }
    if (page is Page.Player && page.request.localFile != null) {
        PlayerScreen(
            viewModel = viewModel { LinksViewModel(api, page.request) },
            fullscreen = navigator.fullscreen,
            onBack = { navigator.back() },
            onToggleFullscreen = navigator::toggleFullscreen,
            onKeys = { navigator.playerKeys = it },
        )
        return
    }
    if (api == null) {
        Message("$apiName is not loaded", "Its extension may have been uninstalled.")
        return
    }
    when (page) {
        is Page.Details -> DetailScreen(
            viewModel = viewModel { DetailViewModel(api, page.url, DataStoreWatchStore.instance, torrents = TorrServer.supported) },
            onBack = { navigator.back() },
            onPlay = navigator::play,
            onOpen = navigator::openDetails,
            onSearch = onSearch,
            autoPlay = page.autoPlay,
            onAutoPlayed = { page.autoPlay = false },
        )

        is Page.Player -> PlayerScreen(
            viewModel = viewModel { LinksViewModel(api, page.request, torrents = TorrServer.supported, checks = SystemBrowser.instance) },
            fullscreen = navigator.fullscreen,
            onBack = { navigator.back() },
            onToggleFullscreen = navigator::toggleFullscreen,
            onKeys = { navigator.playerKeys = it },
        )
    }
}

/** Placeholder for the destinations built in later phases */
@Composable
private fun ComingSoon(destination: Destination) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(destination.title), style = MaterialTheme.typography.headlineMedium)
        Text(
            "Not built yet on desktop",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The destinations in a pill of frosted glass floating over the bottom of the page, as streaming
 * apps have it: the page shows blurred through it. The current one is lit and named, the others
 * are icons with their names on hover.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FloatingDock(navigator: AppNavigator, glass: HazeState, modifier: Modifier = Modifier) {
    Row(
        modifier
            .glass(glass, RoundedCornerShape(percent = 50))
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Destination.entries.forEach { destination ->
            val selected = navigator.destination == destination
            val title = stringResource(destination.title)
            TooltipArea(
                tooltip = {
                    if (!selected) Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp) {
                        Text(title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                    }
                },
                delayMillis = 300,
                tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.TopCenter, alignment = Alignment.TopCenter, offset = DpOffset(0.dp, (-8).dp)),
            ) {
                DockItem(destination, title, selected) { navigator.select(destination) }
            }
        }
    }
}

@Composable
private fun DockItem(destination: Destination, title: String, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val primary = MaterialTheme.colorScheme.primary
    Row(
        Modifier
            .height(48.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(
                when {
                    selected -> primary.copy(alpha = 0.22f)
                    hovered -> MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)
                    else -> Color.Transparent
                },
            )
            .hoverable(interaction)
            .clickable(onClick = onClick)
            .animateContentSize()
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(if (selected) destination.selectedIcon else destination.icon),
            contentDescription = title,
            tint = if (selected) primary else MaterialTheme.colorScheme.onBackground,
        )
        if (selected) Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            modifier = Modifier.padding(start = 8.dp, end = 4.dp),
        )
    }
}
