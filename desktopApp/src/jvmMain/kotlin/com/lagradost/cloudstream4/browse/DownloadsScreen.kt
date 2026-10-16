package com.lagradost.cloudstream4.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.DrawableResource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream4.detail.PlayRequest
import com.lagradost.cloudstream4.download.DownloadState
import com.lagradost.cloudstream4.download.DownloadStatus
import com.lagradost.cloudstream4.download.DownloadedEpisode
import com.lagradost.cloudstream4.download.DownloadedTitle
import com.lagradost.cloudstream4.download.Downloads
import com.lagradost.cloudstream4.generated.resources.Res
import com.lagradost.cloudstream4.generated.resources.delete_24px
import com.lagradost.cloudstream4.generated.resources.download_24px
import com.lagradost.cloudstream4.generated.resources.downloads_empty
import com.lagradost.cloudstream4.generated.resources.downloading_24px
import com.lagradost.cloudstream4.generated.resources.error
import com.lagradost.cloudstream4.generated.resources.folder_open_24px
import com.lagradost.cloudstream4.generated.resources.pause_24px
import com.lagradost.cloudstream4.generated.resources.play_arrow_24px
import com.lagradost.cloudstream4.library.DataStoreWatchStore
import com.lagradost.cloudstream4.library.PlaybackTracking
import com.lagradost.cloudstream4.library.TitleHeader
import com.lagradost.cloudstream4.theme.BlackButton
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import java.awt.Desktop
import java.io.File

/** Everything downloaded or downloading, by title, with each file's progress and what can be done with it */
@Composable
fun DownloadsScreen(
    downloads: Downloads,
    onPlay: (PlayRequest) -> Unit,
    openDetails: (apiName: String, url: String) -> Unit,
) {
    val states by downloads.states.collectAsState()
    // The list changes when a download starts or is deleted, not with every progress update
    val ids = states.keys
    val titles = remember(ids) { downloads.titles().map { it to downloads.episodes(it.id) } }
    val folder = downloads.folder

    // A readable width, centred, as on the settings pages
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.widthIn(max = DownloadsMaxWidth).fillMaxWidth().padding(start = 24.dp, top = 24.dp, end = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Downloads", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
                val used = states.values.sumOf { it.bytes }
                Text(
                    listOfNotNull(folder.absolutePath, used.takeIf { it > 0 }?.let { "${formatBytes(it)} used" }).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            BlackButton(onClick = { openFolder(folder) }) {
                Icon(painterResource(Res.drawable.folder_open_24px), contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Open folder", modifier = Modifier.padding(start = 8.dp))
            }
        }
        if (titles.isEmpty()) {
            Message(stringResource(Res.string.downloads_empty), "Use the download button on a movie or an episode to watch it offline.")
            return@Column
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 16.dp, bottom = DockClearance),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize(),
        ) {
            items(titles, key = { (title, _) -> "title-${title.id}" }) { (title, episodes) ->
                fun actions(episode: DownloadedEpisode, state: DownloadState) = DownloadActions(
                    state = state,
                    onPlay = { downloadedPlayRequest(downloads, title, episode)?.let(onPlay) },
                    onPause = { downloads.pause(episode.id) },
                    onResume = when {
                        // Another source is picked on the title's page
                        state.pickAgain -> ({ openDetails(title.apiName, title.url) })
                        downloads.canResume(title.id, episode.id) -> ({ downloads.resume(title.id, episode.id) })
                        else -> null
                    },
                    onDelete = { downloads.delete(title.id, episode.id) },
                )
                DownloadCard(
                    title = title,
                    episodes = episodes.mapNotNull { episode -> states[episode.id]?.let { episode to it } },
                    label = { downloadLabel(title, it) },
                    actions = ::actions,
                    onOpen = { openDetails(title.apiName, title.url) },
                )
            }
        }
    }
}

/** How wide the list of downloads grows */
private val DownloadsMaxWidth = 960.dp

/** What can be done with one file, its buttons and the click on its line */
private class DownloadActions(
    val state: DownloadState,
    val onPlay: () -> Unit,
    val onPause: () -> Unit,
    val onResume: (() -> Unit)?,
    val onDelete: () -> Unit,
)

/**
 * One title as a compact card. A movie is a single line: its poster, name, status and buttons. A
 * series has a short line for each episode under its name.
 */
@Composable
private fun DownloadCard(
    title: DownloadedTitle,
    episodes: List<Pair<DownloadedEpisode, DownloadState>>,
    label: (DownloadedEpisode) -> String,
    actions: (DownloadedEpisode, DownloadState) -> DownloadActions,
    onOpen: () -> Unit,
) {
    val movie = episodes.singleOrNull()?.takeIf { it.first.episode == null }
    Column(
        Modifier
            .widthIn(max = DownloadsMaxWidth - 48.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RemoteImage(title.poster, null, contentDescription = title.name, modifier = Modifier.width(44.dp).height(66.dp), corner = 6.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    title.name,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val bytes = episodes.sumOf { it.second.bytes }
                val files = if (episodes.size == 1) null else "${episodes.size} files"
                Text(
                    listOfNotNull(title.type.name, title.apiName, files, formatBytes(bytes)).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (movie != null) DownloadStatusLine(movie.second)
            }
            if (movie != null) DownloadButtons(actions(movie.first, movie.second))
        }
        if (movie == null) episodes.forEach { (episode, state) ->
            val action = actions(episode, state)
            val done = state.status == DownloadStatus.Done
            // Lined up with the title's name, under the poster's column
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = done, onClick = action.onPlay)
                    .padding(start = 68.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(label(episode), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    DownloadStatusLine(state)
                }
                DownloadButtons(action)
            }
        }
        if (movie == null) Spacer(Modifier.height(6.dp))
    }
}

/** The status in a line of small text, and a thin bar under it while the file downloads or is paused */
@Composable
private fun DownloadStatusLine(state: DownloadState) {
    Text(
        statusText(state),
        style = MaterialTheme.typography.bodySmall,
        color = if (state.status == DownloadStatus.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    if (state.active || state.status == DownloadStatus.Paused) {
        val color = MaterialTheme.colorScheme.primary
        val track = MaterialTheme.colorScheme.surface
        val bar = Modifier.padding(top = 3.dp).fillMaxWidth(0.6f).height(3.dp)
        val fraction = state.fraction
        if (fraction != null) {
            LinearProgressIndicator(
                progress = { fraction },
                modifier = bar,
                color = color,
                trackColor = track,
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
        } else if (state.active) {
            LinearProgressIndicator(modifier = bar, color = color, trackColor = track, strokeCap = StrokeCap.Round, gapSize = 0.dp)
        }
    }
}

/** Play, pause or resume, then delete, small enough to sit on a line of text */
@Composable
private fun DownloadButtons(actions: DownloadActions) {
    val state = actions.state
    val resume = actions.onResume
    Row {
        when {
            state.status == DownloadStatus.Done -> SmallIconButton(Res.drawable.play_arrow_24px, "Play", actions.onPlay)
            state.active -> SmallIconButton(Res.drawable.pause_24px, "Pause", actions.onPause)
            resume != null -> SmallIconButton(Res.drawable.downloading_24px, "Resume", resume)
        }
        SmallIconButton(Res.drawable.delete_24px, "Delete", actions.onDelete)
    }
}

@Composable
private fun SmallIconButton(icon: DrawableResource, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
        Icon(painterResource(icon), contentDescription = description, modifier = Modifier.size(20.dp))
    }
}

/**
 * The download button of a movie or an episode: it starts the download, then shows its progress.
 * A click while it runs pauses it; once paused, failed or done, a menu offers what can be done next.
 */
@Composable
fun DownloadButton(
    state: DownloadState?,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit,
    onPlay: () -> Unit,
    /** Picks another source, starting the file again */
    onPick: () -> Unit = onStart,
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = {
            when {
                state == null -> onStart()
                state.active -> onPause()
                else -> menu = true
            }
        }) {
            when {
                state == null -> Icon(painterResource(Res.drawable.download_24px), contentDescription = "Download")
                state.active -> Box(contentAlignment = Alignment.Center) {
                    val fraction = state.fraction
                    if (fraction != null && state.status == DownloadStatus.Downloading) {
                        CircularProgressIndicator(progress = { fraction }, modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                    } else {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                    }
                    Icon(painterResource(Res.drawable.pause_24px), contentDescription = "Pause download", modifier = Modifier.size(14.dp))
                }
                state.status == DownloadStatus.Done -> Icon(
                    painterResource(Res.drawable.download_24px),
                    contentDescription = "Downloaded",
                    tint = MaterialTheme.colorScheme.primary,
                )
                state.status == DownloadStatus.Failed -> Icon(painterResource(Res.drawable.error), contentDescription = "Download failed", tint = MaterialTheme.colorScheme.error)
                else -> Icon(painterResource(Res.drawable.downloading_24px), contentDescription = "Download paused")
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            state?.let { Text(statusText(it), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) }
            if (state?.status == DownloadStatus.Done) DropdownMenuItem(text = { Text("Play downloaded file") }, onClick = { menu = false; onPlay() })
            if (state?.status == DownloadStatus.Paused || (state?.status == DownloadStatus.Failed && !state.pickAgain)) {
                DropdownMenuItem(text = { Text(if (state.status == DownloadStatus.Failed) "Try again" else "Resume") }, onClick = { menu = false; onResume() })
            }
            if (state?.status == DownloadStatus.Paused || state?.status == DownloadStatus.Failed) {
                DropdownMenuItem(text = { Text("Pick another source") }, onClick = { menu = false; onPick() })
            }
            DropdownMenuItem(text = { Text("Delete download") }, onClick = { menu = false; onDelete() })
        }
    }
}

/** For example "Downloading · 120 MB of 800 MB · 15%" */
fun statusText(state: DownloadState): String {
    val sizes = when {
        state.total > 0 && state.status != DownloadStatus.Done -> "${formatBytes(state.bytes)} of ${formatBytes(state.total)}"
        state.bytes > 0 -> formatBytes(state.bytes)
        else -> null
    }
    val percent = state.fraction?.takeIf { state.status != DownloadStatus.Done }?.let { "${(it * 100).toInt()}%" }
    return when (state.status) {
        DownloadStatus.Queued -> "Waiting for another download to finish"
        DownloadStatus.FindingLinks -> "Finding links…"
        DownloadStatus.Downloading -> listOfNotNull("Downloading", sizes, percent).joinToString(" · ")
        DownloadStatus.Paused -> listOfNotNull("Paused", sizes, percent).joinToString(" · ")
        DownloadStatus.Failed -> "Failed: ${state.message ?: "unknown error"}"
        DownloadStatus.Done -> listOfNotNull("Downloaded", sizes).joinToString(" · ")
    }
}

fun formatBytes(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f GB".format(bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> "%d MB".format(bytes / (1L shl 20))
    else -> "%d KB".format(bytes / 1024)
}

/** "Movie", or "S1 E2 · Pilot" */
private fun downloadLabel(title: DownloadedTitle, episode: DownloadedEpisode): String {
    if (episode.episode == null) return title.name
    val number = listOfNotNull(episode.season?.let { "S$it" }, "E${episode.episode}").joinToString(" ")
    return listOf(number, episode.name.orEmpty().trim()).filter { it.isNotEmpty() }.joinToString(" · ")
}

/**
 * Plays a download from the Downloads page, where the title's page may not open (offline, or the
 * extension gone). Its progress is saved as if it played from the title's page.
 */
fun downloadedPlayRequest(downloads: Downloads, title: DownloadedTitle, episode: DownloadedEpisode): PlayRequest? {
    val file = downloads.file(episode.id) ?: return null
    val header = TitleHeader(title.id, title.apiName, title.url, title.name, title.type, title.poster)
    val done = downloads.episodes(title.id).filter { downloads.file(it.id) != null }
    val next = done.getOrNull(done.indexOfFirst { it.id == episode.id } + 1)?.takeIf { episode.episode != null }
    val position = DataStoreWatchStore.instance.position(episode.id)?.takeUnless { it.watched }?.positionMs ?: 0
    val tracking = PlaybackTracking(
        header = header,
        id = episode.id,
        episode = episode.episode,
        season = episode.season,
        next = next?.let { PlaybackTracking.NextEpisode(it.id, it.episode, it.season) },
        startPositionMs = position,
    )
    val label = if (episode.episode == null) null else downloadLabel(title, episode)
    return PlayRequest(title.apiName, title.name, label, "", tracking, file.absolutePath, localSubtitles(downloads, episode.id))
}

/** The subtitles saved beside a download, for the player */
fun localSubtitles(downloads: Downloads, id: Int): List<SubtitleFile> =
    downloads.subtitles(id).map { (language, file) -> SubtitleFile(language, file.absolutePath) }

private fun openFolder(folder: File) {
    runCatching {
        folder.mkdirs()
        Desktop.getDesktop().open(folder)
    }.onFailure { println("WARN DownloadsScreen: could not open $folder: $it") }
}
