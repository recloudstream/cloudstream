package com.lagradost.cloudstream4.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalDensity
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream4.browse.Message
import com.lagradost.cloudstream4.browse.formatTime
import com.lagradost.cloudstream4.generated.resources.Res
import com.lagradost.cloudstream4.generated.resources.arrow_back
import com.lagradost.cloudstream4.generated.resources.baseline_fullscreen_24
import com.lagradost.cloudstream4.generated.resources.baseline_fullscreen_exit_24
import com.lagradost.cloudstream4.generated.resources.ic_baseline_audiotrack_24
import com.lagradost.cloudstream4.generated.resources.ic_baseline_hd_24
import com.lagradost.cloudstream4.generated.resources.ic_baseline_replay_24
import com.lagradost.cloudstream4.generated.resources.ic_baseline_speed_24
import com.lagradost.cloudstream4.generated.resources.ic_baseline_subtitles_24
import com.lagradost.cloudstream4.generated.resources.ic_baseline_volume_mute_24
import com.lagradost.cloudstream4.generated.resources.ic_baseline_volume_up_24
import com.lagradost.cloudstream4.generated.resources.pause_24px
import com.lagradost.cloudstream4.generated.resources.play_arrow_24px
import com.lagradost.cloudstream4.library.DataStoreWatchStore
import com.lagradost.cloudstream4.library.saveProgress
import com.lagradost.cloudstream4.theme.BlackButton
import com.lagradost.cloudstream4.theme.WhiteButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream4.torrent.TorrServer
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import java.awt.Rectangle
import java.io.File
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/** Which subtitle the user picked, so it is shown again when the source changes */
private sealed interface SubtitleChoice {
    /** Nothing picked: mpv's own choice */
    data object Default : SubtitleChoice
    data object Off : SubtitleChoice
    /** An extension's subtitle, by its url */
    data class Extension(val url: String) : SubtitleChoice
}

/**
 * Plays a movie or episode. The links load in the background and the best one starts on its own.
 * The bar on top shows the title and source; the bar below plays, seeks, sets the volume and opens
 * the subtitle, audio, speed and source pickers. In full screen the bars show while the mouse moves and hide after a few seconds.
 * Space or K pauses, the left and right arrows or J and L go 10 s back and forward, the up and down
 * arrows change the volume, F toggles full screen, M pauses and hides the app. Escape closes an open panel, then
 * leaves full screen, then goes back. A click on the video closes an open panel, or pauses.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PlayerScreen(
    viewModel: LinksViewModel,
    fullscreen: Boolean,
    onBack: () -> Unit,
    onToggleFullscreen: () -> Unit,
    /** Hands the player's key handler to the window, null when the player closes */
    onKeys: (((KeyEvent) -> Boolean)?) -> Unit = {},
    subtitleSettings: SubtitleSettingsStore = DataStoreSubtitleSettings.instance,
) {
    val lib = Mpv.library
    if (lib == null) {
        Message("The video player is missing", Mpv.loadError)
        return
    }
    val state by viewModel.state.collectAsState()
    val player = remember { MpvPlayer(lib) }
    var status by remember { mutableStateOf(MpvStatus()) }
    val scope = rememberCoroutineScope()
    var subtitleMessage by remember { mutableStateOf<String?>(null) }
    var panel by remember { mutableStateOf<Panel?>(null) }
    val currentFullscreen by rememberUpdatedState(onToggleFullscreen)
    val currentBack by rememberUpdatedState(onBack)

    // Extension subtitles downloaded so far, by url, and which one is shown
    val downloaded = remember { mutableStateMapOf<String, File>() }
    var subtitleChoice by remember { mutableStateOf<SubtitleChoice>(SubtitleChoice.Default) }
    var loadingSubtitle by remember { mutableStateOf<String?>(null) }

    // Where playback is, in ms: it starts where the user left off, and a new source carries on from there
    val tracking = viewModel.request.tracking
    val playedTo = remember { longArrayOf(tracking?.startPositionMs ?: 0, 0) }
    fun saveProgress(positionMs: Long = playedTo[0], durationMs: Long = playedTo[1]) {
        if (tracking != null) runCatching { DataStoreWatchStore.instance.saveProgress(tracking, positionMs, durationMs) }
            .onFailure { println("ERROR PlayerScreen: Could not save the playback position: $it") }
    }
    DisposableEffect(Unit) { onDispose { saveProgress() } }

    // Seconds really played on the current link, and the last position seen, to tell a link that
    // ends right after it starts (a broken file) from one watched to the end
    val playedOnLink = remember { doubleArrayOf(0.0, -1.0) }
    val currentState by rememberUpdatedState(state)

    // Torrents stream through TorrServer, once the user agreed to it this session: mpv plays the
    // stream's url, which failures are reported back from to the torrent's own link
    val torrents = TorrServer.instance
    var torrentAccepted by remember { mutableStateOf(TorrServer.accepted) }
    var torrentStatus by remember { mutableStateOf<String?>(null) }
    val torrentLinks = remember { HashMap<String, String>() }
    val torrentHashes = remember { mutableSetOf<String>() }
    DisposableEffect(Unit) {
        onDispose {
            val hashes = torrentHashes.toList()
            @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
            kotlinx.coroutines.GlobalScope.launch { hashes.forEach { torrents.drop(it) } }
        }
    }

    // A new link was picked, by hand or because the last one failed. The subtitle picked stays on
    val selected = state.selectedLink
    val askTorrent = selected != null && TorrServer.isTorrent(selected) && !torrentAccepted
    LaunchedEffect(selected?.url, torrentAccepted) {
        if (selected == null || askTorrent) return@LaunchedEffect
        playedOnLink[0] = 0.0
        playedOnLink[1] = -1.0
        val playing = if (TorrServer.isTorrent(selected)) {
            try {
                if (!torrents.installed) torrents.install { done, total ->
                    torrentStatus = if (total > 0) "Downloading TorrServer ${done * 100 / total}%" else "Downloading TorrServer"
                }
                torrentStatus = "Connecting to peers…"
                val stream = torrents.stream(selected)
                torrentHashes += stream.hash
                torrentLinks[stream.url] = selected.url
                @Suppress("DEPRECATION")
                ExtractorLink(selected.source, selected.name, stream.url, "", selected.quality, type = ExtractorLinkType.VIDEO)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                viewModel.onAction(LinksAction.Failed(selected.url, e.message ?: "The torrent could not start"))
                return@LaunchedEffect
            } finally {
                torrentStatus = null
            }
        } else selected
        val choice = subtitleChoice
        val subtitles = state.subtitles.mapNotNull { sub ->
            downloaded[sub.url]?.let { MpvSubtitle(it, sub.lang, select = choice == SubtitleChoice.Extension(sub.url)) }
        }
        player.play(playing, playedTo[0].takeIf { it > 0 }?.let { it / 1000.0 }, subtitles)
        if (choice == SubtitleChoice.Off) player.hideSubtitlesWhenLoaded()
    }
    // In full screen the bars show on mouse moves and hide once the mouse rests, unless a panel is open
    var activity by remember { mutableStateOf(0L) }
    var barsShown by remember { mutableStateOf(true) }
    // Showing or hiding the bars resizes what is under the mouse, which reports a move too: only a
    // pointer that really moved on screen counts
    val lastPointer = remember { arrayOfNulls<java.awt.Point>(1) }
    fun pointerMoved() {
        val at = java.awt.MouseInfo.getPointerInfo()?.location ?: return
        if (at == lastPointer[0]) return
        lastPointer[0] = at
        activity = System.nanoTime()
    }
    DisposableEffect(player) {
        player.onMouseMove = ::pointerMoved
        onDispose { player.onMouseMove = null }
    }
    LaunchedEffect(fullscreen, activity, panel) {
        barsShown = true
        if (fullscreen && panel == null) {
            delay(3000)
            barsShown = false
        }
    }
    val showBars = !fullscreen || barsShown
    // The mouse wheel over the video works as swipes do in the Android player: on the right half it
    // sets the volume, on the left half the brightness, each shown by a level on the other side
    var level by remember { mutableStateOf<Level?>(null) }
    var brightness by remember { mutableStateOf(100) }
    val wheelRest = remember { doubleArrayOf(0.0) }
    fun changeLevel(kind: LevelKind, delta: Int) {
        // The level shown is newer than the status, which is read twice a second
        val now = level?.takeIf { it.kind == kind }?.percent ?: if (kind == LevelKind.Volume) status.volume else brightness
        val next = (now + delta).coerceIn(0, 100)
        when (kind) {
            LevelKind.Volume -> {
                if (status.muted && delta > 0) player.toggleMute()
                player.setVolume(next)
            }
            LevelKind.Brightness -> {
                brightness = next
                player.setBrightness(next)
            }
        }
        level = Level(kind, next, System.nanoTime())
    }
    LaunchedEffect(level) {
        val shown = level
        if (shown == null) {
            player.overlay(null, LEVEL_LAYER)
            return@LaunchedEffect
        }
        player.overlay(levelAss(shown), LEVEL_LAYER)
        delay(1500)
        level = null
    }
    LaunchedEffect(player) {
        player.events.collect { event ->
            when (event) {
                // 5 % a notch of the wheel, and a touchpad's small turns add up to it
                is MpvEvent.Wheel -> {
                    wheelRest[0] += event.notches * 5
                    val steps = wheelRest[0].toInt()
                    if (steps != 0) {
                        wheelRest[0] -= steps
                        changeLevel(if (event.x >= 0.5f) LevelKind.Volume else LevelKind.Brightness, steps)
                    }
                }
                MpvEvent.ToggleFullscreen -> currentFullscreen()
                MpvEvent.Back -> currentBack()
                MpvEvent.Click -> if (panel != null) panel = null else player.togglePause()
                is MpvEvent.Failed -> viewModel.onAction(LinksAction.Failed(torrentLinks[event.url] ?: event.url, event.message))
                MpvEvent.Ended -> {
                    val url = currentState.selected
                    // A file that stops within seconds of starting is broken: it must not count as watched
                    // unless it was started in its last half minute
                    val nearEnd = playedTo[1] > 0 && playedTo[0] >= playedTo[1] - 30_000
                    if (playedOnLink[0] < 10 && !nearEnd && url != null) viewModel.onAction(LinksAction.Failed(url, "Stopped right after it started"))
                    else saveProgress(playedTo[1], playedTo[1])
                }
            }
        }
    }
    LaunchedEffect(player) {
        var ticks = 0
        while (true) {
            status = player.status()
            val last = playedOnLink[1]
            if (last >= 0 && status.position > last && status.position - last < 2) playedOnLink[0] += status.position - last
            playedOnLink[1] = status.position
            // Only once the link really plays: a broken one can jump to its end, which is no progress
            if (status.duration > 0 && status.position > 0 && playedOnLink[0] >= 3) {
                playedTo[0] = (status.position * 1000).toLong()
                playedTo[1] = (status.duration * 1000).toLong()
                // Every 10 seconds
                if (++ticks % 20 == 0) saveProgress()
            }
            delay(500)
        }
    }

    // While links are found and the video opens or buffers, a spinner turns over the video
    val indicator = when {
        torrentStatus != null -> torrentStatus
        askTorrent -> null
        state.exhausted -> null
        state.selected == null -> if (state.links.isEmpty()) "Finding links…" else "Finding links · ${state.links.size} found"
        status.buffering -> status.bufferPercent?.takeIf { it in 1..99 }?.let { "Buffering $it%" } ?: "Loading…"
        else -> null
    }
    val currentIndicator by rememberUpdatedState(indicator)
    LaunchedEffect(player, indicator != null) {
        if (indicator == null) {
            player.overlay(null)
            return@LaunchedEffect
        }
        var step = 0
        while (true) {
            currentIndicator?.let { player.overlay(spinnerAss(step++, it)) }
            delay(80)
        }
    }

    fun showSubtitle(subtitle: SubtitleFile) {
        subtitleMessage = null
        subtitleChoice = SubtitleChoice.Extension(subtitle.url)
        val file = downloaded[subtitle.url]
        val track = file?.let { status.tracks.firstOrNull { track -> track.isFile(it) } }
        if (track != null) {
            player.selectSubtitle(track.id)
            return
        }
        loadingSubtitle = subtitle.url
        scope.launch {
            runCatching { Subtitles.download(subtitle) }
                .onSuccess {
                    downloaded[subtitle.url] = it
                    player.addSubtitle(it, subtitle.lang)
                }
                .onFailure { subtitleMessage = "Subtitle could not be loaded: ${it.message}" }
            loadingSubtitle = null
        }
    }

    // The subtitle settings apply at once, also while a subtitle shows
    val subtitleStyle by subtitleSettings.style.collectAsState()
    LaunchedEffect(player, subtitleStyle) { player.setSubtitleStyle(subtitleStyle.mpvOptions()) }
    val autoSelect by subtitleSettings.autoSelect.collectAsState()
    LaunchedEffect(player, autoSelect) { player.setSubtitleLanguages(autoSelect?.let(SubtitleLanguages::codesOf).orEmpty()) }

    // Auto-select, as on Android: once the video plays, if it shows no subtitle in the chosen language,
    // the first from the extension in that language shows. Only until one is picked or turned off by hand
    var autoSelected by remember { mutableStateOf(false) }
    val started = status.duration > 0
    LaunchedEffect(autoSelect, state.subtitles, started) {
        val tag = autoSelect ?: return@LaunchedEffect
        if (autoSelected || !started || subtitleChoice != SubtitleChoice.Default) return@LaunchedEffect
        if (status.tracks.any { it.type == "sub" && it.selected && SubtitleLanguages.tagOf(it.lang) == tag }) {
            autoSelected = true
            return@LaunchedEffect
        }
        val subtitle = state.subtitles.sortedBy { it.lang.lowercase() }.firstOrNull { SubtitleLanguages.tagOf(it.lang) == tag }
            ?: return@LaunchedEffect
        autoSelected = true
        showSubtitle(subtitle)
    }

    // Keys reach the player through the window, before anything focused: the buttons that could hold
    // the focus hide in full screen, which used to leave the keys going nowhere
    fun onKey(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown || event.isCtrlPressed || event.isAltPressed || event.isMetaPressed) return false
        when (event.key) {
            Key.Spacebar, Key.K -> player.togglePause()
            Key.DirectionLeft, Key.J -> player.seek(-10.0)
            Key.DirectionRight, Key.L -> player.seek(10.0)
            Key.DirectionUp -> changeLevel(LevelKind.Volume, 5)
            Key.DirectionDown -> changeLevel(LevelKind.Volume, -5)
            // The app hides at once, and the video must not carry on behind it: the window handles M next
            Key.M -> {
                player.pause()
                return false
            }
            Key.F -> currentFullscreen()
            Key.Escape -> if (panel != null) panel = null else return false
            else -> return false
        }
        // What the key changed shows in the bars, also in full screen
        if (event.key != Key.Escape) activity = System.nanoTime()
        return true
    }
    DisposableEffect(Unit) {
        onKeys(::onKey)
        onDispose { onKeys(null) }
    }
    // Clicks on the bars outside their buttons close an open panel
    val closePanel = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { panel = null }
    // Moves over the bars, which the video does not see
    val barMoves = Modifier.onPointerEvent(PointerEventType.Move) { pointerMoved() }
    val topBar: @Composable (Modifier) -> Unit = { modifier ->
        TopBar(
            title = listOfNotNull(viewModel.request.title, viewModel.request.episodeLabel).joinToString(" · "),
            line = subtitleMessage ?: when {
                state.exhausted && state.checkSites.isNotEmpty() ->
                    "More links wait on a check that you are human" + if (state.links.isNotEmpty()) ", the other ${state.links.size} could not be played" else ""
                state.exhausted && state.links.isEmpty() -> "No playable links found" +
                        if (state.skipped > 0) " (${state.skipped} links cannot play on desktop, such as DRM)" else ""
                state.exhausted -> "None of the ${state.links.size} links could be played"
                state.selected == null -> "Finding links… ${state.links.size} found"
                else -> state.selectedLink?.let(LinksViewModel::label)
            },
            error = subtitleMessage != null || state.exhausted,
            loading = state.loading,
            onBack = onBack,
            onRetry = if (state.exhausted) ({ viewModel.onAction(LinksAction.Retry) }) else null,
            // Some extensions keep finding links for minutes: what is found so far can be enough
            onSkip = if (state.loading && state.links.isNotEmpty()) ({ viewModel.onAction(LinksAction.Skip) }) else null,
            modifier = modifier.then(closePanel).then(barMoves),
        )
    }
    val controlBar: @Composable (Modifier) -> Unit = { modifier ->
        ControlBar(
            player = player,
            status = status,
            state = state,
            panel = panel,
            subtitleOn = status.tracks.any { it.type == "sub" && it.selected },
            fullscreen = fullscreen,
            onPanel = { panel = it },
            onToggleFullscreen = onToggleFullscreen,
            modifier = modifier.then(closePanel).then(barMoves),
        )
    }
    // The picture keeps its size and place whatever opens around it. In a window the bars sit above and
    // below the video and stay. In full screen the video fills the screen, and the bars that come and
    // go cover its edges, as do the panels in both. The video is a native window that nothing can be
    // drawn over: it keeps its size, and the part they cover is cut out of it, see MpvPlayer.anchor
    var area by remember { mutableStateOf(IntSize.Zero) }
    var topBarHeight by remember { mutableStateOf(0) }
    var controlBarHeight by remember { mutableStateOf(0) }
    var sideWidth by remember { mutableStateOf(0) }
    val overlaid = fullscreen && showBars
    val open = panel?.takeIf { showBars }
    val prompt = state.exhausted && state.checkSites.isNotEmpty()
    val side = open != null || prompt || askTorrent
    val visible = IntRect(
        left = 0,
        top = if (overlaid) topBarHeight else 0,
        right = area.width - if (side) sideWidth else 0,
        bottom = area.height - if (overlaid) controlBarHeight else 0,
    )
    val density = LocalDensity.current
    fun Int.dp() = with(density) { toDp() }
    // Where the video's area is in the window, in pixels, from the last layout
    val placed = remember { arrayOfNulls<Rect>(1) }
    fun placeVideo() {
        val at = placed[0] ?: return
        // The window's units are 1.25 pixels at 125 % scaling: the video's window is rounded outward,
        // and the part to show is in its own pixels
        val scale = density.density
        val left = floor(at.left / scale).toInt()
        val top = floor(at.top / scale).toInt()
        val right = ceil(at.right / scale).toInt()
        val bottom = ceil(at.bottom / scale).toInt()
        val dx = at.left - left * scale
        val dy = at.top - top * scale
        val part = visible.takeIf { it != IntRect(0, 0, area.width, area.height) }?.let {
            Rectangle((it.left + dx).roundToInt(), (it.top + dy).roundToInt(), it.width, it.height)
        }
        player.place(Rectangle(left, top, right - left, bottom - top), part)
    }
    SideEffect { placeVideo() }
    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .then(barMoves),
    ) {
        if (!fullscreen) topBar(Modifier)
        Box(
            Modifier.fillMaxWidth().weight(1f).onSizeChanged { area = it }.onGloballyPositioned {
                placed[0] = it.boundsInWindow()
                placeVideo()
            },
        ) {
            SwingPanel(factory = { player.anchor }, modifier = Modifier.size(0.dp))
            if (overlaid) topBar(Modifier.align(Alignment.TopCenter).onSizeChanged { topBarHeight = it.height })
            if (overlaid) controlBar(Modifier.align(Alignment.BottomCenter).onSizeChanged { controlBarHeight = it.height })
            if (side) Row(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = visible.top.dp(), bottom = (area.height - visible.bottom).dp())
                    .fillMaxHeight()
                    .onSizeChanged { sideWidth = it.width },
            ) {
                if (prompt) CheckPrompt(
                    sites = state.checkSites,
                    checking = state.checking,
                    onOpen = { viewModel.onAction(LinksAction.PassChecks) },
                )
                if (askTorrent) TorrentPrompt(
                    onPlay = {
                        TorrServer.accepted = true
                        torrentAccepted = true
                    },
                    onSkip = { selected?.let { viewModel.onAction(LinksAction.Failed(it.url, "Torrent not played")) } },
                )
                if (open != null) SidePanel(
                    panel = open,
                    state = state,
                    status = status,
                    downloaded = downloaded,
                    loadingSubtitle = loadingSubtitle,
                    onSelect = { viewModel.onAction(LinksAction.Select(it)) },
                    onSubtitle = ::showSubtitle,
                    onSubtitleTrack = { id ->
                        subtitleChoice = if (id == null) SubtitleChoice.Off else SubtitleChoice.Default
                        player.selectSubtitle(id)
                    },
                    onSubDelay = { if (it == 0.0) player.resetSubDelay() else player.changeSubDelay(it) },
                    textSize = subtitleStyle.textSize,
                    onTextSize = { size -> subtitleSettings.setStyle(subtitleSettings.style.value.copy(fixedTextSize = size)) },
                    onAudioTrack = player::selectAudio,
                    onSpeed = player::setSpeed,
                )
            }
        }
        if (!fullscreen) controlBar(Modifier)
    }
}

/**
 * Asks before the first torrent of the session, as the Android app does: playing one shares it with
 * other people, who see the user's IP address, and needs TorrServer the first time
 */
@Composable
private fun TorrentPrompt(onPlay: () -> Unit, onSkip: () -> Unit) {
    Column(
        Modifier.width(360.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surface).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text("Play a torrent?", style = MaterialTheme.typography.titleLarge)
        Text(
            "This source is a torrent. It plays while it downloads from other people sharing it, and while it " +
                "plays your connection shares it too: they can see your IP address.",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (!TorrServer.instance.installed) Text(
            "The first time, CloudStream downloads TorrServer (63 MB, github.com/YouROK/TorrServer) to stream it. " +
                "Windows may then ask whether TorrServer can use the network: it plays either way, Allow lets more people connect.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        WhiteButton(onClick = onPlay, modifier = Modifier.fillMaxWidth()) { Text("Play torrent") }
        BlackButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) { Text("Use another source") }
    }
}

/**
 * Offered once nothing else plays: sites that only give their links after a check that a person has to
 * pass, such as Cloudflare's tick box. The browser opens a window only when the user asks for it here
 */
@Composable
private fun CheckPrompt(sites: List<String>, checking: Boolean, onOpen: () -> Unit) {
    Column(
        Modifier.width(360.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surface).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text("Open a browser for more links?", style = MaterialTheme.typography.titleLarge)
        Text(
            "${if (sites.size == 1) "This site wants" else "These sites want"} to check that you are human " +
                "before giving their links:",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(sites.joinToString(separator = "\n"), style = MaterialTheme.typography.bodyMedium)
        Text(
            "A browser window opens on each in turn. Tick the box there: the window closes by itself once the check " +
                "is passed, and their links load here.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        WhiteButton(onClick = onOpen, enabled = !checking, modifier = Modifier.fillMaxWidth()) {
            Text(if (checking) "Waiting for the check…" else "Open browser")
        }
    }
}

/** "English", or "English 1", "English 2" when there are several, by url */
private fun subtitleLabels(subtitles: List<SubtitleFile>): Map<String, String> {
    // Extensions spell a language with stray spaces at times, which must not make it a second language
    fun name(sub: SubtitleFile) = sub.lang.trim().replace(Regex("\\s+"), " ")
    val counts = subtitles.groupingBy(::name).eachCount()
    val seen = HashMap<String, Int>()
    return subtitles.associate { sub ->
        val lang = name(sub)
        val n = seen.merge(lang, 1, Int::plus)!!
        sub.url to if (counts.getValue(lang) > 1) "$lang $n" else lang
    }
}

private fun host(url: String): String? = runCatching { java.net.URI(url).host?.removePrefix("www.") }.getOrNull()

private fun MpvTrack.isFile(file: File) = external && externalFilename?.let { File(it).absolutePath.equals(file.absolutePath, ignoreCase = true) } == true

private enum class Panel(val title: String) { Source("Source"), Subtitles("Subtitles"), Audio("Audio"), Speed("Speed") }

private val speeds = listOf(0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0)

private fun speedLabel(speed: Double) = "%s×".format(if (speed % 1.0 == 0.0) speed.toInt().toString() else speed.toString())

/** The player is always dark, whatever the app's theme, like most video players */
private object PlayerColors {
    val bar = Color(0xFF111114)
    val panel = Color(0xFF18181C)
    val content = Color.White
    val secondary = Color.White.copy(alpha = 0.68f)
    val track = Color.White.copy(alpha = 0.22f)
    val buffered = Color.White.copy(alpha = 0.42f)
    val selected = Color.White.copy(alpha = 0.10f)
}

/** The app's accent, in the tone that reads on a dark background */
@Composable
private fun accent(): Color {
    val scheme = MaterialTheme.colorScheme
    // A light theme's primary is dark, its inverse is the light tone of the same colour
    return if (scheme.background.luminance() > 0.5f) scheme.inversePrimary else scheme.primary
}

/**
 * A spinner and a line of text, drawn by mpv over the video: twelve dots around a dim disc, the
 * brightest one moving round as [step] grows.
 */
private fun spinnerAss(step: Int, text: String): String {
    val cx = 640.0
    val cy = 330.0
    val lines = mutableListOf<String>()
    // Dim disc behind, so the spinner reads on a bright frame
    lines += "{\\an7\\pos(${cx - 56},${cy - 56})\\bord0\\shad0\\1c&H000000&\\1a&H70&\\p1}" + circle(56.0) + "{\\p0}"
    for (i in 0 until 12) {
        val angle = Math.toRadians(i * 30.0 - 90)
        val x = cx + 30 * kotlin.math.cos(angle)
        val y = cy + 30 * kotlin.math.sin(angle)
        // The head is opaque, the dots behind it fade out
        val age = ((step - i) % 12 + 12) % 12
        val alpha = (age * 20).coerceAtMost(0xD0)
        lines += "{\\an7\\pos(${x - 4.5},${y - 4.5})\\bord0\\shad0\\1c&HFFFFFF&\\1a&H%02X&\\p1}".format(alpha) + circle(4.5) + "{\\p0}"
    }
    lines += "{\\an8\\pos($cx,${cy + 70})\\fs24\\bord1.5\\shad0\\3c&H000000&\\1c&HFFFFFF&}" + text.replace("{", "(").replace("}", ")")
    return lines.joinToString("\n")
}

private enum class LevelKind { Volume, Brightness }

/** A level set with the wheel or the keys, [at] telling apart two changes to the same percent */
private data class Level(val kind: LevelKind, val percent: Int, val at: Long)

/** The overlay layer of the levels, above the spinner's */
private const val LEVEL_LAYER = 2

/**
 * A level as the Android player shows it, drawn by mpv over the video: a thin bar filled from the
 * bottom, an icon above and the percent below. The volume's on the left, the brightness's on the
 * right, away from the hand that sets it.
 */
private fun levelAss(level: Level): String {
    fun n(v: Double) = "%.1f".format(java.util.Locale.ROOT, v)
    fun rect(w: Double, h: Double) = "m 0 0 l ${n(w)} 0 ${n(w)} ${n(h)} 0 ${n(h)}"
    val volume = level.kind == LevelKind.Volume
    val cx = if (volume) 110.0 else 1170.0
    val top = 260.0
    val height = 200.0
    val width = 6.0
    val edge = "\\bord1\\shad0\\3c&H000000&\\3a&H90&"
    val lines = mutableListOf<String>()
    // The track, then the level from the bottom up
    lines += "{\\an7\\pos(${n(cx - width / 2)},${n(top)})$edge\\1c&HFFFFFF&\\1a&HB0&\\p1}${rect(width, height)}{\\p0}"
    val filled = height * level.percent / 100
    if (filled > 0) lines += "{\\an7\\pos(${n(cx - width / 2)},${n(top + height - filled)})\\bord0\\shad0\\1c&HFFFFFF&\\p1}${rect(width, filled)}{\\p0}"
    // The icon, drawn on a 24 by 24 grid as Material's icons are
    val icon = if (volume) {
        // A speaker, with sound waves while there is any volume
        "m 3 9 l 7 9 l 12 4 l 12 20 l 7 15 l 3 15" + if (level.percent > 0) {
            " m 14 8.4 b 16.2 9.6 16.2 14.4 14 15.6 l 14.9 17.1 b 18.3 15.2 18.3 8.8 14.9 6.9" +
                if (level.percent > 50) " m 16.4 4.6 b 21.4 7.6 21.4 16.4 16.4 19.4 l 17.3 20.9 b 23.4 17.2 23.4 6.8 17.3 3.1" else ""
        } else ""
    } else {
        // A sun: a disc and eight rays
        val rays = (0 until 8).joinToString(" ") { i ->
            val angle = Math.toRadians(i * 45.0)
            val x = 12 + 9 * kotlin.math.cos(angle)
            val y = 12 + 9 * kotlin.math.sin(angle)
            "m ${n(x - 1.3)} ${n(y - 1.3)} l ${n(x + 1.3)} ${n(y - 1.3)} ${n(x + 1.3)} ${n(y + 1.3)} ${n(x - 1.3)} ${n(y + 1.3)}"
        }
        "m 7.5 12 b 7.5 6 16.5 6 16.5 12 b 16.5 18 7.5 18 7.5 12 $rays"
    }
    lines += "{\\an7\\pos(${n(cx - 18)},${n(top - 48)})\\fscx150\\fscy150$edge\\1c&HFFFFFF&\\p1}$icon{\\p0}"
    lines += "{\\an8\\pos(${n(cx)},${n(top + height + 12)})\\fs22\\bord1.5\\shad0\\3c&H000000&\\1c&HFFFFFF&}${level.percent}%"
    return lines.joinToString("\n")
}

/** An ASS drawing of a circle of radius [r], from 0,0 to 2r,2r */
private fun circle(r: Double): String {
    val k = r * 0.5523
    fun n(v: Double) = "%.1f".format(java.util.Locale.ROOT, v)
    return "m ${n(r)} 0 b ${n(r + k)} 0 ${n(2 * r)} ${n(r - k)} ${n(2 * r)} ${n(r)} " +
        "b ${n(2 * r)} ${n(r + k)} ${n(r + k)} ${n(2 * r)} ${n(r)} ${n(2 * r)} " +
        "b ${n(r - k)} ${n(2 * r)} 0 ${n(r + k)} 0 ${n(r)} " +
        "b 0 ${n(r - k)} ${n(r - k)} 0 ${n(r)} 0"
}

@Composable
private fun TopBar(
    title: String,
    line: String?,
    error: Boolean,
    loading: Boolean,
    onBack: () -> Unit,
    onRetry: (() -> Unit)?,
    onSkip: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().background(PlayerColors.bar).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(painterResource(Res.drawable.arrow_back), contentDescription = "Back", tint = PlayerColors.content)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = PlayerColors.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
            line?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (error) MaterialTheme.colorScheme.error else PlayerColors.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = PlayerColors.secondary)
        if (onSkip != null) TextButton(onClick = onSkip) { Text("Skip", color = accent()) }
        if (onRetry != null) TextButton(onClick = onRetry) { Text("Try again", color = accent()) }
    }
}

/**
 * The bar under the video: a seek bar across the top showing what is played and what is loaded,
 * then play, skip, volume and the time on the left, and the pickers and full screen on the right.
 */
@Composable
private fun ControlBar(
    player: MpvPlayer,
    status: MpvStatus,
    state: LinksState,
    panel: Panel?,
    subtitleOn: Boolean,
    fullscreen: Boolean,
    onPanel: (Panel?) -> Unit,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // While dragging, and after letting go until mpv got there, the bar shows the target, not the old position
    var dragging by remember { mutableStateOf<Float?>(null) }
    var hovering by remember { mutableStateOf<Float?>(null) }
    var seekingTo by remember { mutableStateOf<Pair<Float, Long>?>(null) }
    LaunchedEffect(status) {
        seekingTo?.let { (target, at) ->
            if (abs(status.position - target) < 2 || System.currentTimeMillis() - at > 3000) seekingTo = null
        }
    }
    val duration = status.duration.toFloat()
    val position = dragging ?: seekingTo?.first ?: status.position.toFloat()
    val accent = accent()

    Column(modifier.fillMaxWidth().background(PlayerColors.bar).padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 4.dp)) {
        SeekBar(
            value = if (duration > 0) position / duration else 0f,
            buffered = if (duration > 0) (status.bufferedTo.toFloat() / duration).coerceIn(0f, 1f) else 0f,
            enabled = duration > 0,
            accent = accent,
            onChange = { dragging = it * duration },
            onHover = { hovering = it?.times(duration) },
            onDone = {
                dragging?.let {
                    player.seekTo(it.toDouble())
                    seekingTo = it to System.currentTimeMillis()
                }
                dragging = null
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlayerIcon(
                if (status.paused) Res.drawable.play_arrow_24px else Res.drawable.pause_24px,
                if (status.paused) "Play" else "Pause",
                size = 30.dp,
                onClick = player::togglePause,
            )
            SkipButton(forward = false) { player.seek(-10.0) }
            SkipButton(forward = true) { player.seek(10.0) }
            var volume by remember { mutableStateOf<Float?>(null) }
            PlayerIcon(
                if (status.muted || status.volume == 0) Res.drawable.ic_baseline_volume_mute_24 else Res.drawable.ic_baseline_volume_up_24,
                if (status.muted) "Unmute" else "Mute",
                onClick = player::toggleMute,
            )
            SeekBar(
                value = (volume ?: status.volume.toFloat()) / 100f,
                accent = PlayerColors.content,
                onChange = {
                    volume = it * 100
                    player.setVolume((it * 100).toInt())
                },
                onDone = { volume = null },
                modifier = Modifier.width(96.dp),
            )
            // The hovered time shows in place of the position, to see where a click would go
            val shown = hovering ?: position
            Text(
                if (duration > 0) "${formatTime((shown * 1000).toLong())} / ${formatTime((duration * 1000).toLong())}" else "",
                style = MaterialTheme.typography.bodyMedium,
                color = if (hovering != null) accent else PlayerColors.content,
                modifier = Modifier.padding(start = 16.dp),
            )
            Box(Modifier.weight(1f))

            val subtitleTracks = state.subtitles.isNotEmpty() || status.tracks.any { it.type == "sub" }
            PanelButton(Res.drawable.ic_baseline_subtitles_24, "Subtitles", panel == Panel.Subtitles, active = subtitleOn, enabled = subtitleTracks) {
                onPanel(if (panel == Panel.Subtitles) null else Panel.Subtitles)
            }
            val audio = status.tracks.count { it.type == "audio" }
            PanelButton(
                Res.drawable.ic_baseline_audiotrack_24, "Audio", panel == Panel.Audio,
                label = if (audio > 1) "$audio" else null,
            ) { onPanel(if (panel == Panel.Audio) null else Panel.Audio) }
            PanelButton(
                Res.drawable.ic_baseline_speed_24, "Speed", panel == Panel.Speed,
                label = if (status.speed != 1.0) speedLabel(status.speed) else null,
                active = status.speed != 1.0,
            ) { onPanel(if (panel == Panel.Speed) null else Panel.Speed) }
            PanelButton(
                Res.drawable.ic_baseline_hd_24, "Source", panel == Panel.Source,
                label = state.selectedLink?.let { Qualities.getStringByInt(it.quality).ifBlank { null } } ?: "Source",
                enabled = state.links.isNotEmpty(),
            ) { onPanel(if (panel == Panel.Source) null else Panel.Source) }
            PlayerIcon(
                if (fullscreen) Res.drawable.baseline_fullscreen_exit_24 else Res.drawable.baseline_fullscreen_24,
                if (fullscreen) "Leave full screen" else "Full screen",
                onClick = onToggleFullscreen,
            )
        }
    }
}

@Composable
private fun PlayerIcon(
    icon: DrawableResource,
    description: String,
    size: Dp = 24.dp,
    tint: Color = PlayerColors.content,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(painterResource(icon), contentDescription = description, tint = if (enabled) tint else tint.copy(alpha = 0.3f), modifier = Modifier.size(size))
    }
}

/** Back or forward 10 seconds: the replay arrow with a 10 inside, mirrored to go forward */
@Composable
private fun SkipButton(forward: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painterResource(Res.drawable.ic_baseline_replay_24),
                contentDescription = if (forward) "Forward 10 seconds" else "Back 10 seconds",
                tint = PlayerColors.content,
                modifier = Modifier.size(28.dp).graphicsLayer { if (forward) scaleX = -1f },
            )
            Text("10", color = PlayerColors.content, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/** Opens a picker: tinted while it is open or its setting is on, with a short label when there is one */
@Composable
private fun PanelButton(
    icon: DrawableResource,
    description: String,
    open: Boolean,
    label: String? = null,
    active: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val color = when {
        !enabled -> PlayerColors.content.copy(alpha = 0.3f)
        open || active -> accent()
        else -> PlayerColors.content
    }
    Row(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (open) PlayerColors.selected else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(painterResource(icon), contentDescription = description, tint = color, modifier = Modifier.size(22.dp))
        label?.let { Text(it, color = color, style = MaterialTheme.typography.labelLarge) }
    }
}

/**
 * A thin bar that thickens under the mouse: [value] and [buffered] are fractions. Clicking or
 * dragging calls [onChange] with the fraction under the mouse, [onDone] when the mouse is let go.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SeekBar(
    value: Float,
    accent: Color,
    onChange: (Float) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    buffered: Float = 0f,
    enabled: Boolean = true,
    onHover: (Float?) -> Unit = {},
) {
    var width by remember { mutableStateOf(1) }
    var hovered by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    val currentChange by rememberUpdatedState(onChange)
    val currentDone by rememberUpdatedState(onDone)
    val currentHover by rememberUpdatedState(onHover)
    fun fraction(x: Float) = (x / width).coerceIn(0f, 1f)
    val thick = enabled && (hovered || pressed)
    Box(
        modifier
            .height(20.dp)
            .onSizeChanged { width = it.width.coerceAtLeast(1) }
            .onPointerEvent(PointerEventType.Enter) { hovered = true }
            .onPointerEvent(PointerEventType.Exit) {
                hovered = false
                currentHover(null)
            }
            .onPointerEvent(PointerEventType.Move) { if (enabled) currentHover(fraction(it.changes.first().position.x)) }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    pressed = true
                    currentChange(fraction(down.position.x))
                    drag(down.id) { change ->
                        currentChange(fraction(change.position.x))
                        change.consume()
                    }
                    pressed = false
                    currentDone()
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Canvas(Modifier.fillMaxWidth().height(if (thick) 6.dp else 3.dp)) {
            val r = CornerRadius(size.height / 2)
            drawRoundRect(PlayerColors.track, cornerRadius = r)
            if (buffered > 0) drawRoundRect(PlayerColors.buffered, size = Size(size.width * buffered, size.height), cornerRadius = r)
            drawRoundRect(accent, size = Size(size.width * value.coerceIn(0f, 1f), size.height), cornerRadius = r)
        }
        if (thick) Canvas(Modifier.fillMaxWidth().height(14.dp)) {
            drawCircle(accent, radius = size.height / 2, center = Offset(size.width * value.coerceIn(0f, 1f), size.height / 2))
        }
    }
}

@Composable
private fun SidePanel(
    panel: Panel,
    state: LinksState,
    status: MpvStatus,
    downloaded: Map<String, File>,
    loadingSubtitle: String?,
    onSelect: (String) -> Unit,
    onSubtitle: (SubtitleFile) -> Unit,
    onSubtitleTrack: (Int?) -> Unit,
    onSubDelay: (Double) -> Unit,
    textSize: Float,
    /** null for the normal size */
    onTextSize: (Float?) -> Unit,
    onAudioTrack: (Int) -> Unit,
    onSpeed: (Double) -> Unit,
) {
    LazyColumn(Modifier.width(380.dp).fillMaxHeight().background(PlayerColors.panel)) {
        item { Text(panel.title, style = MaterialTheme.typography.titleMedium, color = PlayerColors.content, modifier = Modifier.padding(16.dp)) }
        when (panel) {
            // Names are shown whole: many sources differ only at the end of their name
            Panel.Source -> items(state.links) { link ->
                Choice(
                    text = LinksViewModel.label(link),
                    detail = state.failed[link.url]?.let { "Failed: $it" } ?: link.source.takeIf { it != link.name },
                    selected = link.url == state.selected,
                    error = link.url in state.failed,
                    maxLines = Int.MAX_VALUE,
                ) { onSelect(link.url) }
            }

            Panel.Subtitles -> {
                val tracks = status.tracks.filter { it.type == "sub" }
                val shown = tracks.firstOrNull { it.selected }
                // Timing first: a video can hold dozens of subtitle tracks
                if (shown != null) item { SubtitleTiming(status.subDelay, onSubDelay) }
                if (shown != null) item { SubtitleSize(textSize, onTextSize) }
                item { Choice("Off", selected = shown == null) { onSubtitleTrack(null) } }
                // Sorted by language. Extensions often give several of one: they are numbered and show where they come from
                val subtitles = state.subtitles.sortedBy { it.lang.trim().lowercase() }
                val labels = subtitleLabels(subtitles)
                items(subtitles, key = { it.url }) { sub ->
                    val track = downloaded[sub.url]?.let { file -> tracks.firstOrNull { it.isFile(file) } }
                    Choice(
                        labels.getValue(sub.url),
                        detail = when {
                            loadingSubtitle == sub.url -> "Loading…"
                            !sub.url.contains("://") -> "Saved with the download"
                            else -> listOfNotNull("From the extension", host(sub.url)).joinToString(" · ")
                        },
                        selected = track?.selected == true,
                    ) { onSubtitle(sub) }
                }
                items(tracks.filter { !it.external }) { track ->
                    Choice(track.label, detail = listOf("In the video", track.details).filter { it.isNotEmpty() }.joinToString(" · "), selected = track.selected) {
                        onSubtitleTrack(track.id)
                    }
                }
            }

            Panel.Audio -> {
                val tracks = status.tracks.filter { it.type == "audio" }
                items(tracks) { track ->
                    Choice(
                        track.label,
                        detail = listOf(if (track.external) "From the extension" else "In the video", track.details).filter { it.isNotEmpty() }.joinToString(" · "),
                        selected = track.selected,
                    ) { onAudioTrack(track.id) }
                }
                // Many sources carry one language each: say so, rather than leave an empty choice
                if (tracks.size <= 1) item {
                    Text(
                        if (tracks.isEmpty()) "No audio track yet. It shows once the video starts."
                        else "This source has one audio track. Other sources may be in other languages: look in Source.",
                        style = MaterialTheme.typography.bodySmall,
                        color = PlayerColors.secondary,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }

            Panel.Speed -> items(speeds) { speed ->
                Choice(speedLabel(speed) + if (speed == 1.0) " (normal)" else "", selected = abs(status.speed - speed) < 0.01) { onSpeed(speed) }
            }
        }
    }
}

/** Moves the subtitles earlier or later when they are out of sync with the speech */
@Composable
private fun SubtitleTiming(delay: Double, onChange: (Double) -> Unit) {
    val accent = accent()
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text("Timing", style = MaterialTheme.typography.titleSmall, color = PlayerColors.content)
        Text(
            when {
                abs(delay) < 0.05 -> "In sync with the video"
                delay > 0 -> "Shown %.1f s later".format(delay)
                else -> "Shown %.1f s earlier".format(-delay)
            },
            style = MaterialTheme.typography.bodySmall,
            color = PlayerColors.secondary,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onChange(-0.5) }) { Text("Earlier", color = accent) }
            TextButton(onClick = { onChange(0.5) }) { Text("Later", color = accent) }
            if (abs(delay) >= 0.05) TextButton(onClick = { onChange(0.0) }) { Text("Reset", color = accent) }
        }
    }
}

/** The text size, kept for every video. The rest of the look is in Settings > Player */
@Composable
private fun SubtitleSize(size: Float, onChange: (Float?) -> Unit) {
    val accent = accent()
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Size", style = MaterialTheme.typography.titleSmall, color = PlayerColors.content)
        Text(
            if (size == SubtitleStyle.DEFAULT_TEXT_SIZE) "Normal · more in Settings > Player" else "${size.toInt()} · more in Settings > Player",
            style = MaterialTheme.typography.bodySmall,
            color = PlayerColors.secondary,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onChange((size - 3).coerceAtLeast(10f)) }, enabled = size > 10f) { Text("Smaller", color = accent) }
            TextButton(onClick = { onChange((size + 3).coerceAtMost(60f)) }, enabled = size < 60f) { Text("Larger", color = accent) }
            if (size != SubtitleStyle.DEFAULT_TEXT_SIZE) TextButton(onClick = { onChange(null) }) { Text("Reset", color = accent) }
        }
    }
}

@Composable
private fun Choice(
    text: String,
    detail: String? = null,
    selected: Boolean,
    error: Boolean = false,
    maxLines: Int = 2,
    onClick: () -> Unit,
) {
    val accent = accent()
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (selected) PlayerColors.selected else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) accent else PlayerColors.content,
                fontWeight = if (selected) FontWeight.SemiBold else null,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
            )
            detail?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (error) MaterialTheme.colorScheme.error else PlayerColors.secondary,
                    maxLines = maxLines,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (selected) Text("✓", color = accent, modifier = Modifier.padding(start = 8.dp))
    }
}
