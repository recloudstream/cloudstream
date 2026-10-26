package com.lagradost.cloudstream4.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream4.desktop.data.AppData
import com.lagradost.cloudstream4.desktop.data.PlayerKind
import com.lagradost.cloudstream4.desktop.data.WatchProgress
import com.lagradost.cloudstream4.desktop.generated.resources.Res
import com.lagradost.cloudstream4.desktop.generated.resources.arrow_back
import com.lagradost.cloudstream4.desktop.generated.resources.audio
import com.lagradost.cloudstream4.desktop.generated.resources.forward
import com.lagradost.cloudstream4.desktop.generated.resources.fullscreen
import com.lagradost.cloudstream4.desktop.generated.resources.fullscreen_exit
import com.lagradost.cloudstream4.desktop.generated.resources.pause
import com.lagradost.cloudstream4.desktop.generated.resources.play
import com.lagradost.cloudstream4.desktop.generated.resources.replay
import com.lagradost.cloudstream4.desktop.generated.resources.skip_next
import com.lagradost.cloudstream4.desktop.generated.resources.sources
import com.lagradost.cloudstream4.desktop.generated.resources.speed
import com.lagradost.cloudstream4.desktop.generated.resources.subtitles
import com.lagradost.cloudstream4.desktop.generated.resources.volume_mute
import com.lagradost.cloudstream4.desktop.generated.resources.volume_up
import com.lagradost.cloudstream4.desktop.player.ExternalPlayer
import com.lagradost.cloudstream4.desktop.player.Vlc
import com.lagradost.cloudstream4.desktop.player.VlcEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import java.awt.Cursor
import java.awt.Point
import java.awt.Toolkit
import java.awt.image.BufferedImage
import java.io.File

val LocalWindowState = compositionLocalOf<WindowState?> { null }

private val hiddenCursor by lazy {
    PointerIcon(
        Toolkit.getDefaultToolkit().createCustomCursor(
            BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), Point(0, 0), "hidden"
        )
    )
}

private fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%02d:%02d".format(minutes, seconds)
}

@Composable
fun PlayerScreen(route: Route.Player) {
    val navigator = LocalNavigator.current
    val settings by AppData.settings.collectAsState()
    val playable = route.playlist[route.index]
    val scope = rememberCoroutineScope()
    val viewModel = remember(route) { PlayerViewModel(scope, route.media.apiName, playable.data) }
    val state by viewModel.state.collectAsState()
    LaunchedEffect(viewModel) { viewModel.loadLinks() }

    val hasNext = route.index + 1 < route.playlist.size
    val playNext = { navigator.replace(route.copy(index = route.index + 1)) }

    if (settings.player == PlayerKind.External || !Vlc.isAvailable) {
        ExternalPlayerScreen(route, viewModel, state, hasNext, playNext)
        return
    }

    val engine = remember { VlcEngine(Vlc.factory!!) }
    val windowState = LocalWindowState.current
    DisposableEffect(engine) {
        onDispose {
            engine.release()
            if (windowState?.placement == WindowPlacement.Fullscreen) windowState.placement = WindowPlacement.Floating
        }
    }

    fun saveProgress() {
        if (engine.lengthMs <= 0) return
        AppData.saveProgress(
            WatchProgress(
                media = route.media,
                episodeName = if (route.playlist.size > 1 || playable.episode != null) playable.title else null,
                episodeData = playable.data,
                season = playable.season,
                episode = playable.episode,
                positionMs = engine.timeMs,
                durationMs = engine.lengthMs,
            )
        )
    }

    // Start (or switch) playback whenever the selected link changes, resuming where the user left off
    LaunchedEffect(state.selectedLink) {
        val link = state.selectedLink ?: return@LaunchedEffect
        val resume = AppData.positions.value[playable.data]?.takeIf { it.fraction < 0.95f }?.positionMs ?: 0L
        engine.play(link.playbackUrl(), if (engine.timeMs > 0) engine.timeMs else resume)
    }
    LaunchedEffect(engine.hasError) {
        if (engine.hasError) viewModel.nextLink()
    }
    LaunchedEffect(engine.hasFinished) {
        if (engine.hasFinished) {
            saveProgress()
            if (hasNext) playNext()
        }
    }
    LaunchedEffect(engine) {
        while (true) {
            delay(5_000)
            if (engine.isPlaying) saveProgress()
        }
    }
    DisposableEffect(engine) { onDispose { saveProgress() } }

    var lastInteraction by remember { mutableStateOf(System.currentTimeMillis()) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            now = System.currentTimeMillis()
        }
    }
    val controlsVisible = !engine.isPlaying || now - lastInteraction < 3_000
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    val isFullscreen = windowState?.placement == WindowPlacement.Fullscreen
    fun toggleFullscreen() {
        windowState ?: return
        windowState.placement = if (isFullscreen) WindowPlacement.Floating else WindowPlacement.Fullscreen
    }

    val seekMs = settings.seekSeconds * 1000L
    @OptIn(ExperimentalComposeUiApi::class)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .focusable()
            .onPointerEvent(PointerEventType.Move) { lastInteraction = System.currentTimeMillis() }
            .pointerHoverIcon(if (controlsVisible) PointerIcon.Default else hiddenCursor)
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                lastInteraction = System.currentTimeMillis()
                when (event.key) {
                    Key.Spacebar, Key.K -> engine.togglePause()
                    Key.DirectionLeft, Key.J -> engine.skip(-seekMs)
                    Key.DirectionRight, Key.L -> engine.skip(seekMs)
                    Key.DirectionUp -> engine.changeVolume(engine.volume + 5)
                    Key.DirectionDown -> engine.changeVolume(engine.volume - 5)
                    Key.M -> engine.changeVolume(if (engine.volume == 0) 100 else 0)
                    Key.F -> toggleFullscreen()
                    Key.N -> if (hasNext) playNext()
                    Key.Escape -> if (isFullscreen) toggleFullscreen() else navigator.back()
                    else -> return@onKeyEvent false
                }
                true
            }
    ) {
        engine.frame?.let { frame ->
            Image(
                frame,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    engine.togglePause()
                    focus.requestFocus()
                },
            )
        }

        if (state.selectedLink == null || engine.isBuffering) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                if (state.error == null) CircularProgressIndicator(color = Color.White)
                Spacer(Modifier.height(12.dp))
                Text(
                    when {
                        state.error != null -> state.error!!
                        state.selectedLink == null -> "Loading links (${state.links.size} found)"
                        else -> "Buffering ${state.selectedLink?.label()}"
                    },
                    color = Color.White,
                )
                if (state.error != null) {
                    TextButton(onClick = viewModel::loadLinks) { Text("Retry") }
                }
            }
        }

        AnimatedVisibility(controlsVisible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().background(BottomFadeReversed).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { navigator.back() }) {
                        Icon(painterResource(Res.drawable.arrow_back), "Back", tint = Color.White)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(playable.title, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        state.selectedLink?.let {
                            Text(it.label(), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(BottomFade).padding(horizontal = 16.dp, vertical = 8.dp)) {
                    var dragging by remember { mutableStateOf<Float?>(null) }
                    val progress = if (engine.lengthMs > 0) engine.timeMs.toFloat() / engine.lengthMs else 0f
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(formatTime(dragging?.let { (it * engine.lengthMs).toLong() } ?: engine.timeMs), color = Color.White)
                        Slider(
                            value = dragging ?: progress,
                            onValueChange = {
                                dragging = it
                                lastInteraction = System.currentTimeMillis()
                            },
                            onValueChangeFinished = {
                                dragging?.let { engine.seekTo((it * engine.lengthMs).toLong()) }
                                dragging = null
                                focus.requestFocus()
                            },
                            enabled = engine.lengthMs > 0,
                            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                        )
                        Text(formatTime(engine.lengthMs), color = Color.White)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ControlButton(if (engine.isPlaying) Res.drawable.pause else Res.drawable.play, "Play/Pause") { engine.togglePause() }
                        ControlButton(Res.drawable.replay, "Back ${settings.seekSeconds}s") { engine.skip(-seekMs) }
                        ControlButton(Res.drawable.forward, "Forward ${settings.seekSeconds}s") { engine.skip(seekMs) }
                        if (hasNext) ControlButton(Res.drawable.skip_next, "Next episode") { playNext() }
                        ControlButton(if (engine.volume == 0) Res.drawable.volume_mute else Res.drawable.volume_up, "Mute") {
                            engine.changeVolume(if (engine.volume == 0) 100 else 0)
                        }
                        Slider(
                            value = engine.volume / 200f,
                            onValueChange = { engine.changeVolume((it * 200).toInt()) },
                            colors = SliderDefaults.colors(thumbColor = Color.White),
                            modifier = Modifier.width(120.dp),
                        )
                        Text("${engine.volume}%", color = Color.White, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.weight(1f))

                        MenuButton(Res.drawable.sources, "Sources (${state.links.size})") { close ->
                            if (state.isLoadingLinks) DropdownMenuItem(text = { Text("Still loading links...") }, onClick = {}, enabled = false)
                            state.links.forEach { link ->
                                DropdownMenuItem(
                                    text = { Text(link.label(), fontWeight = if (link == state.selectedLink) FontWeight.Bold else null) },
                                    onClick = {
                                        close()
                                        viewModel.selectLink(link)
                                    },
                                )
                            }
                        }
                        MenuButton(Res.drawable.subtitles, "Subtitles") { close ->
                            DropdownMenuItem(text = { Text("Off") }, onClick = {
                                close()
                                engine.selectSubtitle(-1)
                                viewModel.clearSubtitle()
                            })
                            engine.subtitleTracks.filter { it.id >= 0 }.forEach { track ->
                                DropdownMenuItem(
                                    text = { Text(track.name, fontWeight = if (track.id == engine.subtitleTrack) FontWeight.Bold else null) },
                                    onClick = {
                                        close()
                                        engine.selectSubtitle(track.id)
                                    },
                                )
                            }
                            state.subtitles.forEach { subtitle ->
                                DropdownMenuItem(
                                    text = {
                                        Text(subtitle.lang, fontWeight = if (subtitle == state.selectedSubtitle) FontWeight.Bold else null)
                                    },
                                    onClick = {
                                        close()
                                        scope.launch { viewModel.downloadSubtitle(subtitle)?.let(engine::addSubtitleFile) }
                                    },
                                )
                            }
                        }
                        if (engine.audioTracks.size > 2) {
                            MenuButton(Res.drawable.audio, "Audio") { close ->
                                engine.audioTracks.filter { it.id >= 0 }.forEach { track ->
                                    DropdownMenuItem(
                                        text = { Text(track.name, fontWeight = if (track.id == engine.audioTrack) FontWeight.Bold else null) },
                                        onClick = {
                                            close()
                                            engine.selectAudio(track.id)
                                        },
                                    )
                                }
                            }
                        }
                        MenuButton(Res.drawable.speed, "Speed ${engine.rate}x") { close ->
                            listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f).forEach { rate ->
                                DropdownMenuItem(text = { Text("${rate}x") }, onClick = {
                                    close()
                                    engine.changeRate(rate)
                                })
                            }
                        }
                        ControlButton(if (isFullscreen) Res.drawable.fullscreen_exit else Res.drawable.fullscreen, "Fullscreen") {
                            toggleFullscreen()
                        }
                    }
                }
            }
        }
    }
}

private val BottomFadeReversed = Brush.verticalGradient(
    listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
)

@Composable
private fun ControlButton(icon: DrawableResource, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(painterResource(icon), description, tint = Color.White)
    }
}

@Composable
private fun MenuButton(icon: DrawableResource, description: String, content: @Composable (close: () -> Unit) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Icon(painterResource(icon), null, tint = Color.White, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text(description, color = Color.White)
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            content { expanded = false }
        }
    }
}

/** Used when VLC is not installed or the user prefers their own player */
@Composable
private fun ExternalPlayerScreen(
    route: Route.Player,
    viewModel: PlayerViewModel,
    state: PlayerState,
    hasNext: Boolean,
    playNext: () -> Unit,
) {
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val playable = route.playlist[route.index]
    var launched by remember { mutableStateOf<String?>(null) }

    fun launch(link: ExtractorLink) {
        scope.launch {
            val subtitle: File? = state.subtitles.firstOrNull { it.langTag?.startsWith("en") == true }
                ?.let { viewModel.downloadSubtitle(it) }
            launched = if (ExternalPlayer.launch(link.playbackUrl(), playable.title, listOfNotNull(subtitle))) {
                "Playing ${link.label()} in ${ExternalPlayer.detect()?.let { File(it).nameWithoutExtension }}"
            } else {
                "No external player found, install VLC or mpv, or set one in Settings"
            }
        }
    }

    LaunchedEffect(state.isLoadingLinks) {
        if (!state.isLoadingLinks && launched == null) state.links.firstOrNull()?.let(::launch)
    }

    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { navigator.back() }) { Icon(painterResource(Res.drawable.arrow_back), "Back") }
            Text(playable.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (hasNext) TextButton(onClick = playNext) { Text("Next episode") }
        }
        if (!Vlc.isAvailable && AppData.settings.value.player == PlayerKind.Internal) {
            Text(
                "The built-in player needs VLC installed. Install it from videolan.org to play inside CloudStream.",
                color = MaterialTheme.colorScheme.error,
            )
            TextButton(onClick = { openInBrowser("https://www.videolan.org/vlc/") }) { Text("Download VLC") }
        }
        launched?.let { Text(it) }
        if (state.isLoadingLinks) Text("Loading links (${state.links.size} found)...")
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(state.links, key = { it.url }) { link ->
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                        .clickable { launch(link) }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(painterResource(Res.drawable.play), null)
                    Spacer(Modifier.width(12.dp))
                    Text(link.label())
                }
            }
        }
    }
}
