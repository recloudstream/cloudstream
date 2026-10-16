package com.lagradost.cloudstream4.browse
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    LazyColumn(contentPadding = PaddingValues(bottom = DockClearance), modifier = Modifier.fillMaxSize()) {
        item {
            Header(
                response = response,
                state = state,
                onPlay = { fromStart -> play(viewModel.playRequest(fromStart = fromStart), null) },
                onResume = { episode -> play(viewModel.playRequest(episode), episode) },
                onBookmark = { viewModel.onAction(DetailAction.SetBookmark(it)) },
                onSearch = { onSearch(response.name) },
                download = if (state.movieData != null && response.type != TvType.Live) downloadButton(null) else null,
            )
        }

        if (state.dubs.size > 1 || state.seasons.size > 1) item {
            Choices(state, viewModel::onAction)
        }

        if (season != null) {
            item {
                Text(
                    "${season.label} · ${season.episodes.size} episodes",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = PageGutter, end = PageGutter, top = 16.dp, bottom = 8.dp),
                )
            }
            items(season.episodes) { episode ->
                EpisodeRow(episode, state.progressOf(episode), downloadButton(episode)) { play(viewModel.playRequest(episode), episode) }
            }
        }

        val recommendations = response.recommendations.orEmpty()
        if (recommendations.isNotEmpty()) item {
            PosterRow(
                title = "More like this",
                items = recommendations,
                horizontal = false,
                modifier = Modifier.padding(top = 24.dp),
                onItemClick = { onOpen(it.apiName, it.url) },
            )
        }
    }
}

/** "12:30", or "1:02:03" from an hour on */
fun formatTime(ms: Long): String {
    val total = ms / 1000
    val (h, m, s) = Triple(total / 3600, total / 60 % 60, total % 60)
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@Composable
private fun Header(
    response: LoadResponse,
    state: DetailState,
    onPlay: (fromStart: Boolean) -> Unit,
    onResume: (com.lagradost.cloudstream3.Episode) -> Unit,
    onBookmark: (WatchType) -> Unit,
    onSearch: () -> Unit,
    /** The movie's download button, null for a series or a live stream */
    download: (@Composable () -> Unit)?,
) {
    val background = MaterialTheme.colorScheme.background
    // The title's wide picture behind it, faded into the page below and on the side of the text, as on Home
    Box(Modifier.fillMaxWidth()) {
        RemoteImage(
            response.backgroundPosterUrl?.takeIf { it.isNotBlank() } ?: response.posterUrl,
            response.posterHeaders,
            contentDescription = null,
            modifier = Modifier.matchParentSize(),
            corner = 0.dp,
        )
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(0f to background.copy(alpha = 0.35f), 1f to background)))
        Box(Modifier.matchParentSize().background(Brush.horizontalGradient(0f to background.copy(alpha = 0.6f), 0.7f to background.copy(alpha = 0.2f))))
        Row(
            Modifier.padding(start = PageGutter, end = PageGutter, top = 72.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            RemoteImage(
                response.posterUrl,
                response.posterHeaders,
                contentDescription = response.name,
                modifier = Modifier.width(200.dp).height(300.dp).shadow(20.dp, RoundedCornerShape(8.dp)),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(response.name, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold))
                val facts = listOfNotNull(
                    response.score?.let { "★ ${"%.1f".format(it.toFloat(10))}" },
                    response.year?.toString(),
                    response.type.name,
                    response.duration?.let { "$it min" },
                    response.contentRating,
                    response.apiName,
                )
                Text(facts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                response.tags?.takeIf { it.isNotEmpty() }?.let {
                    Text(it.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                response.plot?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 8, overflow = TextOverflow.Ellipsis)
                }
                state.unsupported?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (state.unsupported == null && state.movieData == null && state.seasons.isEmpty()) {
                    Text("No episodes yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(
                    Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val resume = state.resume
                    if (state.unsupported == null) when {
                        // Continue watching left off in an episode
                        resume?.episode != null -> WhiteButton(onClick = { onResume(resume.episode!!) }) {
                            Icon(painterResource(Res.drawable.play_arrow_24px), contentDescription = null)
                            val at = if (resume.positionMs > 0) " at ${formatTime(resume.positionMs)}" else ""
                            Text("${if (resume.positionMs > 0) "Resume" else "Play"} ${resume.label}$at", modifier = Modifier.padding(start = 8.dp))
                        }

                        state.movieData != null -> {
                            WhiteButton(onClick = { onPlay(false) }) {
                                Icon(painterResource(Res.drawable.play_arrow_24px), contentDescription = null)
                                Text(resume?.let { "Resume at ${formatTime(it.positionMs)}" } ?: "Play", modifier = Modifier.padding(start = 8.dp))
                            }
                            if (resume != null) TextButton(onClick = { onPlay(true) }) { Text("Start over") }
                        }
                    }
                    LibraryButton(state.bookmark, onBookmark)
                    // The same title from other extensions, when this one's links do not play
                    BlackButton(onClick = onSearch) {
                        Icon(painterResource(Res.drawable.search_icon), contentDescription = null)
                        Text("Search", modifier = Modifier.padding(start = 8.dp))
                    }
                    download?.invoke()
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Choices(state: DetailState, onAction: (DetailAction) -> Unit) {
    Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
    Box {
        BlackButton(onClick = { open = true }) {
            Text(if (current == WatchType.NONE) "Add to library" else current.label)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            WatchType.lists.forEach { type ->
                DropdownMenuItem(
                    text = { Text(if (type == current) "${type.label} ✓" else type.label) },
                    onClick = {
                        open = false
                        onSelect(type)
                    },
                )
            }
            if (current != WatchType.NONE) DropdownMenuItem(
                text = { Text("Remove from library") },
                onClick = {
                    open = false
                    onSelect(WatchType.NONE)
                },
            )
        }
    }
}

@Composable
private fun EpisodeRow(episode: Episode, progress: PlaybackPosition?, download: @Composable () -> Unit, onClick: () -> Unit) {
    // A rounded line that lights under the mouse, the pages' gutter on both sides
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = PageGutter - 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (episode.posterUrl != null) {
            RemoteImage(episode.posterUrl, null, contentDescription = null, modifier = Modifier.width(160.dp).height(90.dp), corner = 6.dp)
        } else {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainer),
                contentAlignment = Alignment.Center,
            ) { Icon(painterResource(Res.drawable.play_arrow_24px), contentDescription = null) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(DetailViewModel.episodeLabel(episode), style = MaterialTheme.typography.titleSmall)
            episode.description?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (progress != null && !progress.watched && progress.positionMs > 0) {
                LinearProgressIndicator(
                    progress = { progress.fraction },
                    modifier = Modifier.fillMaxWidth(0.5f).padding(top = 4.dp).height(3.dp),
                    trackColor = MaterialTheme.colorScheme.surface,
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
            }
        }
        when {
            progress?.watched == true -> Text("Watched ✓", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            progress != null && progress.positionMs > 0 -> Text("${formatTime(progress.positionMs)} / ${formatTime(progress.durationMs)}", style = MaterialTheme.typography.bodySmall)
            else -> episode.runTime?.let { Text("$it min", style = MaterialTheme.typography.bodySmall) }
        }
        download()
    }
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
