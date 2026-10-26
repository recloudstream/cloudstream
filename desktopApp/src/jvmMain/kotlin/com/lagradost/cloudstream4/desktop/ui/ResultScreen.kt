package com.lagradost.cloudstream4.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.EpisodeResponse
import com.lagradost.cloudstream3.LiveStreamLoadResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.TorrentLoadResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream4.desktop.data.AppData
import com.lagradost.cloudstream4.desktop.data.MediaEntry
import com.lagradost.cloudstream4.desktop.generated.resources.Res
import com.lagradost.cloudstream4.desktop.generated.resources.arrow_back
import com.lagradost.cloudstream4.desktop.generated.resources.bookmark
import com.lagradost.cloudstream4.desktop.generated.resources.bookmark_border
import com.lagradost.cloudstream4.desktop.generated.resources.open_in_new
import com.lagradost.cloudstream4.desktop.generated.resources.play
import com.lagradost.cloudstream4.desktop.generated.resources.star
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.jetbrains.compose.resources.painterResource
import java.awt.Desktop
import java.net.URI

private sealed interface ResultState {
    data object Loading : ResultState
    data class Error(val message: String) : ResultState
    data class Loaded(val response: LoadResponse) : ResultState
}

/** Episodes grouped by dub status and season, as shown in the season/dub selectors */
private data class EpisodeGroups(
    val byDub: Map<DubStatus, Map<Int?, List<Episode>>>,
    val seasonNames: Map<Int, String>,
)

private fun LoadResponse.episodeGroups(): EpisodeGroups? {
    val episodes: Map<DubStatus, List<Episode>> = when (this) {
        is TvSeriesLoadResponse -> mapOf(DubStatus.None to episodes)
        is AnimeLoadResponse -> episodes.filterValues { it.isNotEmpty() }
        else -> return null
    }
    val names = (this as? EpisodeResponse)?.seasonNames.orEmpty().associate { data ->
        data.season to (data.name ?: "Season ${data.displaySeason ?: data.season}")
    }
    return EpisodeGroups(
        byDub = episodes.mapValues { (_, list) ->
            list.groupBy { it.season }.toSortedMap(compareBy(nullsFirst()) { it })
        },
        seasonNames = names,
    )
}

private fun Episode.label(index: Int): String {
    val number = episode ?: (index + 1)
    val prefix = if (season != null) "S$season E$number" else "Episode $number"
    return if (name.isNullOrBlank()) prefix else "$prefix · $name"
}

private suspend fun load(media: MediaEntry): ResultState {
    val api = APIHolder.getApiFromNameNull(media.apiName)
        ?: return ResultState.Error("The extension ${media.apiName} is not installed or failed to load")
    return try {
        val response = withContext(Dispatchers.IO) {
            withTimeout(api.loadTimeoutMs ?: 60_000L) { api.load(media.url) }
        }
        response?.let { ResultState.Loaded(it) } ?: ResultState.Error("Nothing returned by ${api.name}")
    } catch (t: Throwable) {
        logError(t)
        ResultState.Error(t.message ?: t.javaClass.simpleName)
    }
}

fun openInBrowser(url: String) {
    try {
        Desktop.getDesktop().browse(URI(url))
    } catch (t: Throwable) {
        logError(t)
    }
}

@Composable
fun ResultScreen(media: MediaEntry) {
    val navigator = LocalNavigator.current
    var state by remember(media) { mutableStateOf<ResultState>(ResultState.Loading) }
    var reloadKey by remember { mutableStateOf(0) }
    LaunchedEffect(media, reloadKey) {
        state = ResultState.Loading
        state = load(media)
    }

    Box(Modifier.fillMaxSize()) {
        when (val current = state) {
            ResultState.Loading -> LoadingBox("Loading ${media.name}")
            is ResultState.Error -> MessageBox(
                "Could not load ${media.name}",
                current.message,
                action = "Retry" to { reloadKey++ },
            )

            is ResultState.Loaded -> ResultContent(media, current.response)
        }
        IconButton(
            onClick = { navigator.back() },
            modifier = Modifier.padding(8.dp).background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(50)),
        ) {
            Icon(painterResource(Res.drawable.arrow_back), "Back", tint = Color.White)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResultContent(media: MediaEntry, response: LoadResponse) {
    val navigator = LocalNavigator.current
    val bookmarks by AppData.bookmarks.collectAsState()
    val positions by AppData.positions.collectAsState()
    val entry = remember(response) {
        media.copy(
            name = response.name,
            posterUrl = response.posterUrl ?: media.posterUrl,
            type = response.type,
            posterHeaders = response.posterHeaders ?: media.posterHeaders,
        )
    }
    val groups = remember(response) { response.episodeGroups() }
    var dub by remember(groups) {
        mutableStateOf(groups?.byDub?.keys?.let { keys -> keys.firstOrNull { it == DubStatus.Subbed } ?: keys.firstOrNull() })
    }
    val seasons = groups?.byDub?.get(dub).orEmpty()
    var season by remember(groups, dub) { mutableStateOf(seasons.keys.firstOrNull()) }
    val episodes = seasons[season].orEmpty()

    fun playEpisodes(list: List<Episode>, index: Int) {
        val playlist = list.mapIndexed { i, episode ->
            Playable(episode.data, "${response.name} · ${episode.label(i)}", episode.season, episode.episode, episode.posterUrl)
        }
        navigator.push(Route.Player(entry, playlist, index))
    }

    val movieData: String? = when (response) {
        is MovieLoadResponse -> response.dataUrl
        is LiveStreamLoadResponse -> response.dataUrl
        else -> null
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Box(Modifier.fillMaxWidth().height(360.dp)) {
                RemoteImage(
                    response.backgroundPosterUrl ?: response.posterUrl,
                    Modifier.fillMaxSize(),
                    response.posterHeaders,
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.background))
                    )
                )
                Row(
                    Modifier.align(Alignment.BottomStart).padding(24.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    RemoteImage(
                        response.posterUrl,
                        Modifier.width(150.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp)),
                        response.posterHeaders,
                    )
                    Spacer(Modifier.width(20.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(response.name, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            listOfNotNull(
                                response.year?.toString(),
                                response.type.name,
                                response.duration?.let { "$it min" },
                                response.contentRating,
                                response.apiName,
                            ).forEach { Pill(it) }
                            response.score?.let { score ->
                                Icon(painterResource(Res.drawable.star), null, Modifier.size(18.dp))
                                Text(score.toString())
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            when {
                                movieData != null -> Button(onClick = {
                                    navigator.push(
                                        Route.Player(entry, listOf(Playable(movieData, response.name, posterUrl = response.posterUrl)), 0)
                                    )
                                }) {
                                    Icon(painterResource(Res.drawable.play), null)
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (positions[movieData]?.positionMs?.let { it > 0 } == true) "Resume" else "Play")
                                }

                                response is TorrentLoadResponse -> Button(onClick = {
                                    (response.magnet ?: response.torrent)?.let(::openInBrowser)
                                }) { Text("Open in torrent client") }

                                episodes.isNotEmpty() -> Button(onClick = {
                                    // Continue with the first unfinished episode of this season
                                    val index = episodes.indexOfFirst { (positions[it.data]?.fraction ?: 0f) < 0.9f }
                                    playEpisodes(episodes, index.coerceAtLeast(0))
                                }) {
                                    Icon(painterResource(Res.drawable.play), null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Play")
                                }
                            }
                            if (movieData != null && response !is LiveStreamLoadResponse) {
                                DownloadButton(entry, Playable(movieData, response.name, posterUrl = response.posterUrl))
                            }
                            val saved = bookmarks.any { it.url == media.url }
                            OutlinedButton(onClick = { AppData.toggleBookmark(entry) }) {
                                Icon(painterResource(if (saved) Res.drawable.bookmark else Res.drawable.bookmark_border), null)
                                Spacer(Modifier.width(6.dp))
                                Text(if (saved) "Bookmarked" else "Bookmark")
                            }
                            OutlinedButton(onClick = { openInBrowser(response.url) }) {
                                Icon(painterResource(Res.drawable.open_in_new), null)
                                Spacer(Modifier.width(6.dp))
                                Text("Website")
                            }
                        }
                    }
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (response.comingSoon) Pill("Coming soon")
                response.plot?.let {
                    Text(it, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.widthIn(max = 900.dp))
                }
                response.tags?.takeIf { it.isNotEmpty() }?.let { tags ->
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        tags.forEach { Pill(it) }
                    }
                }
                response.actors?.takeIf { it.isNotEmpty() }?.let { actors ->
                    Text(
                        "Cast: " + actors.take(12).joinToString(", ") { it.actor.name },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (groups != null) {
            item {
                Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (groups.byDub.size > 1) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            groups.byDub.keys.forEach { status ->
                                FilterChip(dub == status, onClick = { dub = status }, label = { Text(status.name) })
                            }
                        }
                    }
                    if (seasons.size > 1) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(seasons.keys.toList()) { key ->
                                FilterChip(
                                    season == key,
                                    onClick = { season = key },
                                    label = { Text(key?.let { groups.seasonNames[it] ?: "Season $it" } ?: "Episodes") },
                                )
                            }
                        }
                    }
                    if (episodes.isEmpty()) Text("No episodes found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(episodes.size, key = { episodes[it].data + it }) { index ->
                val episode = episodes[index]
                EpisodeRow(
                    download = {
                        DownloadButton(
                            entry,
                            Playable(episode.data, "${response.name} · ${episode.label(index)}", episode.season, episode.episode, episode.posterUrl),
                        )
                    },
                    title = episode.label(index),
                    description = episode.description,
                    posterUrl = episode.posterUrl ?: response.posterUrl,
                    headers = response.posterHeaders,
                    progress = positions[episode.data]?.fraction,
                    onClick = { playEpisodes(episodes, index) },
                )
            }
        }

        response.recommendations?.takeIf { it.isNotEmpty() }?.let { recommendations ->
            item {
                Spacer(Modifier.height(16.dp))
                SearchResponseRow("Recommended", recommendations)
                Spacer(Modifier.height(24.dp))
            }
        }
        item { Spacer(Modifier.height(32.dp)) }
    }
}

@Composable
private fun EpisodeRow(
    download: @Composable () -> Unit,
    title: String,
    description: String?,
    posterUrl: String?,
    headers: Map<String, String>?,
    progress: Float?,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(180.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp))) {
            RemoteImage(posterUrl, Modifier.fillMaxSize(), headers)
            Icon(
                painterResource(Res.drawable.play),
                null,
                Modifier.align(Alignment.Center).size(36.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50)).padding(6.dp),
                tint = Color.White,
            )
            if (progress != null && progress > 0f) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp),
                    drawStopIndicator = {},
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            description?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        download()
    }
}
