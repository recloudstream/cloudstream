package com.lagradost.cloudstream4.browse
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.EpisodeResponse
import com.lagradost.cloudstream4.generated.resources.baseline_theaters_24
import com.lagradost.cloudstream4.generated.resources.check
import com.lagradost.cloudstream4.generated.resources.content_copy_24px
import com.lagradost.cloudstream4.generated.resources.ic_baseline_add_24
import com.lagradost.cloudstream4.generated.resources.ic_baseline_replay_24
import com.lagradost.cloudstream4.generated.resources.open_in_new_24px
import com.lagradost.cloudstream4.generated.resources.person_24px
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.net.URI
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream4.download.DownloadRequest
import com.lagradost.cloudstream4.download.DownloadSource
import com.lagradost.cloudstream4.download.DownloadedEpisode
import com.lagradost.cloudstream4.download.DownloadedTitle
import com.lagradost.cloudstream4.download.Downloads
import com.lagradost.cloudstream4.detail.DetailAction
import com.lagradost.cloudstream4.detail.DetailState
import com.lagradost.cloudstream4.detail.DetailStatus
import com.lagradost.cloudstream4.detail.DetailViewModel
import com.lagradost.cloudstream4.detail.PlayRequest
import com.lagradost.cloudstream4.generated.resources.Res
import com.lagradost.cloudstream4.generated.resources.arrow_back
import com.lagradost.cloudstream4.generated.resources.play_arrow_24px
import com.lagradost.cloudstream4.generated.resources.search_icon
import com.lagradost.cloudstream4.library.PlaybackPosition
import com.lagradost.cloudstream4.library.WatchType
import com.lagradost.cloudstream4.theme.AppFilterChip
import com.lagradost.cloudstream4.theme.BlackButton
import com.lagradost.cloudstream4.theme.WhiteButton
import org.jetbrains.compose.resources.painterResource

/** The details of a movie or series, with its episodes and similar titles */
@Composable
fun DetailScreen(
    viewModel: DetailViewModel,
    onBack: () -> Unit,
    onPlay: (PlayRequest) -> Unit,
    onOpen: (apiName: String, url: String) -> Unit,
    /** Looks for the title in every extension, from the Search page */
    onSearch: (query: String) -> Unit,
    /** Play where the title was left as soon as it loads, from Continue watching */
    autoPlay: Boolean = false,
    onAutoPlayed: () -> Unit = {},
    downloads: Downloads = Downloads.instance,
) {
    val state by viewModel.state.collectAsState()
    Box(Modifier.fillMaxSize()) {
        when (val status = state.status) {
            DetailStatus.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is DetailStatus.Failed -> Message("Could not load this title", status.message) {
                WhiteButton(onClick = { viewModel.onAction(DetailAction.Retry) }) { Text("Try again") }
            }

            DetailStatus.Done -> Details(state, viewModel, onPlay, onOpen, onSearch, downloads, autoPlay, onAutoPlayed)
        }
        // Over the backdrop, in a disc of the page's colour so it shows on any picture
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .padding(12.dp)
                .shadow(8.dp, CircleShape)
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.7f), CircleShape),
        ) {
            Icon(painterResource(Res.drawable.arrow_back), contentDescription = "Back")
        }
    }
}

@Composable
private fun Details(
    state: DetailState,
    viewModel: DetailViewModel,
    onPlay: (PlayRequest) -> Unit,
    onOpen: (apiName: String, url: String) -> Unit,
    onSearch: (query: String) -> Unit,
    downloads: Downloads,
    autoPlay: Boolean,
    onAutoPlayed: () -> Unit,
) {
    val response = state.response ?: return
    val season = state.seasons.getOrNull(state.selectedSeason)
    val downloadStates by downloads.states.collectAsState()
    // A downloaded movie or episode plays its file, without finding links
    fun play(request: PlayRequest?, episode: Episode?) {
        request ?: return
        val id = downloadId(state, episode)
        val file = id?.let(downloads::file)
        onPlay(if (file == null) request else request.copy(localFile = file.absolutePath, localSubtitles = localSubtitles(downloads, id)))
    }
    // Once only, so coming back from the player leaves the page as it is
    LaunchedEffect(Unit) {
        if (!autoPlay) return@LaunchedEffect
        onAutoPlayed()
        val request = viewModel.resumeRequest() ?: return@LaunchedEffect
        play(request, state.resume?.episode)
    }
    fun downloadButton(episode: Episode?): @Composable () -> Unit = {
        val id = downloadId(state, episode)
        val request = downloadRequest(state, episode)
        // The source is picked first, as on Android
        var picking by remember { mutableStateOf(false) }
        if (id != null && request != null) {
            DownloadButton(
                state = downloadStates[id],
                onStart = { picking = true },
                onPause = { downloads.pause(id) },
                onResume = { downloads.start(request) },
                onDelete = { state.titleId?.let { downloads.delete(it, id) } },
                onPlay = { play(viewModel.playRequest(episode), episode) },
            )
            if (picking) DownloadSourcePicker(
                source = request.source,
                onPick = { link, subtitles ->
                    picking = false
                    downloads.start(request.copy(link = link, subtitles = subtitles))
                },
                onDismiss = { picking = false },
            )
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Episode cards fill the width, about 440 dp each
        val columns = ((maxWidth - PageGutter * 2) / 440.dp).toInt().coerceIn(1, 4)
        LazyColumn(contentPadding = PaddingValues(bottom = DockClearance), modifier = Modifier.fillMaxSize()) {
            item(key = "hero") {
                Hero(
                    response = response,
                    state = state,
                    onPlay = { fromStart -> play(viewModel.playRequest(fromStart = fromStart), null) },
                    onResume = { episode -> play(viewModel.playRequest(episode), episode) },
                    onBookmark = { viewModel.onAction(DetailAction.SetBookmark(it)) },
                    onSearch = { onSearch(response.name) },
                    download = if (state.movieData != null && response.type != TvType.Live) downloadButton(null) else null,
                )
            }

            if (season != null || state.dubs.size > 1 || state.seasons.size > 1) item(key = "episodes") {
                Column(Modifier.padding(horizontal = PageGutter).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionTitle("Episodes", season?.let { "${it.label} · ${it.episodes.size} episodes" })
                    Choices(state, viewModel::onAction)
                }
            }
            if (season != null) {
                items(season.episodes.chunked(columns)) { row ->
                    Row(
                        Modifier.padding(horizontal = PageGutter, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        row.forEach { episode ->
                            EpisodeCard(episode, state.progressOf(episode), downloadButton(episode), Modifier.weight(1f)) {
                                play(viewModel.playRequest(episode), episode)
                            }
                        }
                        // The last row's cards keep the others' width
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }

            val actors = response.actors.orEmpty()
            if (actors.isNotEmpty()) item(key = "cast") { CastRow(actors) }

            val recommendations = response.recommendations.orEmpty()
            if (recommendations.isNotEmpty()) item(key = "more") {
                PosterRow(
                    title = "More like this",
                    items = recommendations,
                    horizontal = false,
                    modifier = Modifier.padding(top = 24.dp),
                    onItemClick = { onOpen(it.apiName, it.url) },
                )
            }

            item(key = "about") { About(response) }
        }
    }
}

/** "12:30", or "1:02:03" from an hour on */
fun formatTime(ms: Long): String {
    val total = ms / 1000
    val (h, m, s) = Triple(total / 3600, total / 60 % 60, total % 60)
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** A section's title, with a line of grey after it */
@Composable
private fun SectionTitle(title: String, detail: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        RowTitle(title)
        detail?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/**
 * The top of the page, as on Home: the title's wide picture across most of the window, faded into
 * the page, with its poster, name or logo, facts, genres and story, then what can be done with it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LazyItemScope.Hero(
    response: LoadResponse,
    state: DetailState,
    onPlay: (fromStart: Boolean) -> Unit,
    onResume: (Episode) -> Unit,
    onBookmark: (WatchType) -> Unit,
    onSearch: () -> Unit,
    /** The movie's download button, null for a series or a live stream */
    download: (@Composable () -> Unit)?,
) {
    val background = MaterialTheme.colorScheme.background
    Box(Modifier.fillParentMaxWidth().heightIn(min = 460.dp).fillParentMaxHeight(0.74f)) {
        RemoteImage(
            response.backgroundPosterUrl?.takeIf { it.isNotBlank() } ?: response.posterUrl,
            response.posterHeaders,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            corner = 0.dp,
        )
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(0f to background.copy(alpha = 0.25f), 0.5f to Color.Transparent, 1f to background)))
        // Strong on the side of the text, so it reads on a bright picture
        Box(Modifier.matchParentSize().background(Brush.horizontalGradient(0f to background.copy(alpha = 0.95f), 0.45f to background.copy(alpha = 0.75f), 0.8f to background.copy(alpha = 0.1f), 1f to Color.Transparent)))
        Row(
            Modifier.align(Alignment.BottomStart).padding(start = PageGutter, end = PageGutter, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            RemoteImage(
                response.posterUrl,
                response.posterHeaders,
                contentDescription = response.name,
                modifier = Modifier.width(180.dp).height(270.dp).shadow(24.dp, RoundedCornerShape(8.dp)),
            )
            Column(Modifier.widthIn(max = 760.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TitleOrLogo(response)
                Facts(response, state)
                response.tags?.takeIf { it.isNotEmpty() }?.let { tags ->
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        tags.take(6).forEach { Tag(it) }
                    }
                }
                response.plot?.takeIf { it.isNotBlank() }?.let { Plot(it) }
                state.unsupported?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (state.unsupported == null && state.movieData == null && state.seasons.isEmpty()) {
                    Text("No episodes yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Actions(response, state, onPlay, onResume, onBookmark, onSearch, download)
                WatchedSoFar(state)
            }
        }
    }
}

/** The title's logo when the extension gives one, its name otherwise or when the logo does not load */
@Composable
private fun TitleOrLogo(response: LoadResponse) {
    var logoFailed by remember(response.logoUrl) { mutableStateOf(false) }
    val logo = response.logoUrl?.takeIf { it.isNotBlank() && !logoFailed }
    if (logo != null) AsyncImage(
        model = logo,
        contentDescription = response.name,
        contentScale = ContentScale.Fit,
        alignment = Alignment.BottomStart,
        onError = { logoFailed = true },
        modifier = Modifier.fillMaxWidth(0.7f).height(110.dp),
    ) else Text(
        response.name,
        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

/** "★ 7.8 · 2024 · Movie · 112 min · PG-13 · Ongoing · Provider", the score in the accent */
@Composable
private fun Facts(response: LoadResponse, state: DetailState) {
    val grey = MaterialTheme.colorScheme.onSurfaceVariant
    val facts = listOfNotNull(
        response.year?.toString(),
        typeName(response.type),
        response.duration?.let { "$it min" },
        state.seasons.size.takeIf { it > 1 }?.let { "$it seasons" },
        response.contentRating?.takeIf { it.isNotBlank() },
        (response as? EpisodeResponse)?.showStatus?.name,
        response.apiName,
    )
    Text(
        buildAnnotatedString {
            response.score?.let {
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)) {
                    append("★ ${"%.1f".format(it.toFloat(10))}")
                }
                if (facts.isNotEmpty()) append("  ·  ")
            }
            append(facts.joinToString("  ·  "))
        },
        style = MaterialTheme.typography.bodyMedium,
        color = grey,
    )
}

/** A genre, as a small pill */
@Composable
private fun Tag(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.10f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** The story, three lines, all of it on a click */
@Composable
private fun Plot(plot: String) {
    var expanded by remember(plot) { mutableStateOf(false) }
    var overflows by remember(plot) { mutableStateOf(false) }
    Column {
        Text(
            plot,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow },
        )
        if (overflows || expanded) Text(
            if (expanded) "Less" else "More",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(4.dp)).clickable { expanded = !expanded }.padding(2.dp),
        )
    }
}

/**
 * Play or resume, large in the accent; the library and download; then small round tools: other
 * sources, the trailer, the title on the provider's site and copying its link
 */
@Composable
private fun Actions(
    response: LoadResponse,
    state: DetailState,
    onPlay: (fromStart: Boolean) -> Unit,
    onResume: (Episode) -> Unit,
    onBookmark: (WatchType) -> Unit,
    onSearch: () -> Unit,
    download: (@Composable () -> Unit)?,
) {
    val big = PaddingValues(horizontal = 28.dp, vertical = 12.dp)
    Row(
        Modifier.padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val resume = state.resume
        if (state.unsupported == null) when {
            // Continue watching left off in an episode
            resume?.episode != null -> WhiteButton(onClick = { onResume(resume.episode!!) }, contentPadding = big) {
                Icon(painterResource(Res.drawable.play_arrow_24px), contentDescription = null)
                val verb = if (resume.positionMs > 0) "Resume" else "Play"
                Text("$verb ${resume.label}", modifier = Modifier.padding(start = 8.dp))
            }

            state.movieData != null -> {
                WhiteButton(onClick = { onPlay(false) }, contentPadding = big) {
                    Icon(painterResource(Res.drawable.play_arrow_24px), contentDescription = null)
                    Text(if (resume != null) "Resume" else "Play", modifier = Modifier.padding(start = 8.dp))
                }
                if (resume != null) BlackButton(onClick = { onPlay(true) }) {
                    Icon(painterResource(Res.drawable.ic_baseline_replay_24), contentDescription = null, modifier = Modifier.size(20.dp))
                    Text("Start over", modifier = Modifier.padding(start = 8.dp))
                }
            }

            // A series not started yet begins at its first episode
            else -> state.seasons.firstOrNull()?.episodes?.firstOrNull()?.let { first ->
                WhiteButton(onClick = { onResume(first) }, contentPadding = big) {
                    Icon(painterResource(Res.drawable.play_arrow_24px), contentDescription = null)
                    val label = listOfNotNull(first.season?.let { "S$it" }, first.episode?.let { "E$it" }).joinToString(" ")
                    Text(if (label.isEmpty()) "Play" else "Play $label", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
        LibraryButton(state.bookmark, onBookmark)
        download?.let { RoundTool("Download") { it() } }
        RoundTool("Find in other sources", onClick = onSearch) {
            Icon(painterResource(Res.drawable.search_icon), contentDescription = null, modifier = Modifier.size(20.dp))
        }
        response.trailers.firstOrNull()?.let { trailer ->
            RoundTool("Watch the trailer", onClick = { openInBrowser(trailer.extractorUrl) }) {
                Icon(painterResource(Res.drawable.baseline_theaters_24), contentDescription = null, modifier = Modifier.size(20.dp))
            }
        }
        RoundTool("Open on ${response.apiName}'s site", onClick = { openInBrowser(response.url) }) {
            Icon(painterResource(Res.drawable.open_in_new_24px), contentDescription = null, modifier = Modifier.size(20.dp))
        }
        var copied by remember { mutableStateOf(false) }
        LaunchedEffect(copied) {
            if (copied) {
                delay(2000)
                copied = false
            }
        }
        RoundTool(if (copied) "Link copied" else "Copy the link", onClick = {
            copyText(response.url)
            copied = true
        }) {
            Icon(
                painterResource(if (copied) Res.drawable.check else Res.drawable.content_copy_24px),
                contentDescription = null,
                tint = if (copied) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** How far the movie, or the episode to resume, was watched: a thin bar and the time left */
@Composable
private fun WatchedSoFar(state: DetailState) {
    val resume = state.resume ?: return
    val progress = resume.episode?.let(state::progressOf) ?: state.titleId?.let(state.progress::get) ?: return
    if (progress.watched || progress.positionMs <= 0 || progress.durationMs <= 0) return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        LinearProgressIndicator(
            progress = { progress.fraction },
            modifier = Modifier.width(220.dp).height(4.dp),
            trackColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f),
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
        val left = (progress.durationMs - progress.positionMs) / 60_000
        Text(
            if (left > 0) "$left min left" else "Almost done",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A round button of the theme's tonal colour, its name on hover */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RoundTool(name: String, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    TooltipArea(
        tooltip = { GlassTooltip(name) },
        delayMillis = 300,
        tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.BottomCenter, alignment = Alignment.BottomCenter, offset = DpOffset(0.dp, 8.dp)),
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) { content() }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Choices(state: DetailState, onAction: (DetailAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (state.dubs.size > 1) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.dubs.forEach { dub ->
                AppFilterChip(
                    selected = dub == state.selectedDub,
                    onClick = { onAction(DetailAction.SelectDub(dub)) },
                    label = { Text(if (dub == DubStatus.None) "Default" else dub.name) },
                )
            }
        }
        if (state.seasons.size > 1) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.seasons.forEachIndexed { index, season ->
                AppFilterChip(
                    selected = index == state.selectedSeason,
                    onClick = { onAction(DetailAction.SelectSeason(index)) },
                    label = { Text(season.label) },
                )
            }
        }
    }
}

/** Which library list the title is in, with a menu to change it */
@Composable
private fun LibraryButton(current: WatchType, onSelect: (WatchType) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Box {
        BlackButton(onClick = { open = true }) {
            Icon(
                painterResource(if (current == WatchType.NONE) Res.drawable.ic_baseline_add_24 else Res.drawable.check),
                contentDescription = null,
                tint = if (current == WatchType.NONE) LocalContentColor.current else colors.primary,
                modifier = Modifier.size(20.dp),
            )
            Text(if (current == WatchType.NONE) "My library" else current.label, modifier = Modifier.padding(start = 8.dp))
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            offset = DpOffset(0.dp, 8.dp),
            shape = RoundedCornerShape(16.dp),
            containerColor = colors.surfaceContainer,
            tonalElevation = 0.dp,
            shadowElevation = 16.dp,
            border = BorderStroke(1.dp, colors.onBackground.copy(alpha = 0.08f)),
        ) {
            WatchType.lists.forEach { type ->
                val selected = type == current
                DropdownMenuItem(
                    text = { Text(type.label, color = if (selected) colors.primary else colors.onBackground, fontWeight = if (selected) FontWeight.SemiBold else null) },
                    trailingIcon = if (selected) {
                        { Icon(painterResource(Res.drawable.check), contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp)) }
                    } else null,
                    onClick = {
                        open = false
                        onSelect(type)
                    },
                    modifier = Modifier.padding(horizontal = 6.dp).clip(RoundedCornerShape(10.dp)),
                )
            }
            if (current != WatchType.NONE) DropdownMenuItem(
                text = { Text("Remove from library", color = colors.error) },
                onClick = {
                    open = false
                    onSelect(WatchType.NONE)
                },
                modifier = Modifier.padding(horizontal = 6.dp).clip(RoundedCornerShape(10.dp)),
            )
        }
    }
}

/**
 * An episode as a card in the grid: its picture with how far it was watched along the bottom and a
 * play sign on hover, its name, story and length, and its download button
 */
@Composable
private fun EpisodeCard(
    episode: Episode,
    progress: PlaybackPosition?,
    download: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val colors = MaterialTheme.colorScheme
    Row(
        modifier
            .height(104.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (hovered) colors.surfaceVariant else colors.surfaceContainer)
            .hoverable(interaction)
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(156.dp).fillMaxHeight().clip(RoundedCornerShape(8.dp)).background(colors.surface)) {
            if (episode.posterUrl != null) RemoteImage(episode.posterUrl, null, contentDescription = null, modifier = Modifier.fillMaxSize(), corner = 0.dp)
            if (hovered || episode.posterUrl == null) Box(
                Modifier.align(Alignment.Center).size(36.dp).background(Color.Black.copy(alpha = 0.55f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(painterResource(Res.drawable.play_arrow_24px), contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp)) }
            if (progress != null && progress.positionMs > 0) {
                val fraction = if (progress.watched) 1f else progress.fraction
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color.Black.copy(alpha = 0.4f)))
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(fraction).height(3.dp).background(colors.primary))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                DetailViewModel.episodeLabel(episode),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            episode.description?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            val meta = when {
                progress?.watched == true -> "Watched ✓"
                progress != null && progress.positionMs > 0 -> "${formatTime(progress.positionMs)} / ${formatTime(progress.durationMs)}"
                else -> episode.runTime?.let { "$it min" }
            }
            meta?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = if (progress?.watched == true) colors.primary else colors.onSurfaceVariant)
            }
        }
        download()
    }
}

/** The people in it, as round pictures with their names and parts */
@Composable
private fun CastRow(actors: List<ActorData>) {
    Column(Modifier.padding(top = 24.dp)) {
        RowTitle("Cast", Modifier.padding(horizontal = PageGutter, vertical = 4.dp))
        ScrollingRow {
            items(actors) { data ->
                Column(Modifier.width(96.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(80.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
                        Icon(painterResource(Res.drawable.person_24px), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(36.dp))
                        data.actor.image?.takeIf { it.isNotBlank() }?.let {
                            RemoteImage(it, null, contentDescription = data.actor.name, modifier = Modifier.fillMaxSize(), corner = 40.dp)
                        }
                    }
                    Text(data.actor.name, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold), maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    data.roleString?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

/** Everything known about the title, in a card at the bottom of the page */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun About(response: LoadResponse) {
    val facts = listOfNotNull(
        "Provider" to response.apiName,
        "Type" to typeName(response.type),
        response.year?.let { "Year" to it.toString() },
        response.duration?.let { "Runtime" to "$it min" },
        response.contentRating?.takeIf { it.isNotBlank() }?.let { "Age rating" to it },
        (response as? EpisodeResponse)?.showStatus?.let { "Status" to it.name },
        response.score?.let { "Score" to "%.1f / 10".format(it.toFloat(10)) },
        response.tags?.takeIf { it.isNotEmpty() }?.let { "Genres" to it.joinToString(", ") },
    )
    Column(Modifier.padding(start = PageGutter, end = PageGutter, top = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        RowTitle("About")
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                facts.forEach { (label, value) ->
                    Column(Modifier.widthIn(min = 120.dp, max = 360.dp)) {
                        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(value, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Column {
                Text("Link", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    response.url,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable { openInBrowser(response.url) },
                )
            }
        }
    }
}

/** "Movie", "Series", "Anime"... */
private fun typeName(type: TvType): String = when (type) {
    TvType.TvSeries -> "Series"
    TvType.AsianDrama -> "Asian drama"
    TvType.AnimeMovie -> "Anime movie"
    TvType.OVA -> "OVA"
    else -> type.name.replace(Regex("(?<=[a-z])(?=[A-Z])"), " ").lowercase().replaceFirstChar { it.uppercase() }
}

private fun openInBrowser(url: String) {
    runCatching { Desktop.getDesktop().browse(URI(url)) }
        .onFailure { println("WARN DetailScreen: could not open $url: $it") }
}

private fun copyText(text: String) {
    runCatching { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null) }
}

/** The id a download is saved under: the episode's, or the title's for a movie */
private fun downloadId(state: DetailState, episode: Episode?): Int? =
    if (episode == null) state.titleId else state.episodeIds[episode]

/** What downloads an episode, or the movie when [episode] is null */
private fun downloadRequest(state: DetailState, episode: Episode?): DownloadRequest? {
    val response = state.response ?: return null
    val titleId = state.titleId ?: return null
    val id = downloadId(state, episode) ?: return null
    val data = episode?.data ?: state.movieData ?: return null
    // An episode without a number is numbered by its place, so two never get the same file name
    val number = episode?.let { it.episode ?: (state.seasons.flatMap { season -> season.episodes }.indexOf(it) + 1) }
    return DownloadRequest(
        DownloadedTitle(titleId, response.apiName, response.url, response.name, response.type, response.posterUrl),
        DownloadedEpisode(id, titleId, episode?.name, number, episode?.season, episode?.posterUrl ?: response.posterUrl, episode?.description),
        DownloadSource(response.apiName, data),
    )
}
