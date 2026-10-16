package com.lagradost.cloudstream4.browse

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
import androidx.compose.material3.HorizontalDivider
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

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, top = 24.dp, end = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Downloads", style = MaterialTheme.typography.headlineSmall)
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
        LazyColumn(contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 16.dp, bottom = DockClearance), modifier = Modifier.fillMaxSize()) {
            titles.forEach { (title, episodes) ->
                item(key = "title-${title.id}") {
                    TitleRow(title, episodes.size, episodes.sumOf { states[it.id]?.bytes ?: 0 }) { openDetails(title.apiName, title.url) }
                }
                items(episodes, key = { "episode-${it.id}" }) { episode ->
                    val state = states[episode.id] ?: return@items
                    EpisodeDownloadRow(
                        label = downloadLabel(title, episode),
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
                }
                item(key = "divider-${title.id}") { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            }
        }
    }
}

@Composable
private fun TitleRow(title: DownloadedTitle, count: Int, bytes: Long, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RemoteImage(title.poster, null, contentDescription = title.name, modifier = Modifier.width(60.dp).height(90.dp))
        Column(Modifier.weight(1f)) {
            Text(title.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val files = if (count == 1) "1 file" else "$count files"
            Text(
                listOf(title.type.name, title.apiName, files, formatBytes(bytes)).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EpisodeDownloadRow(
    label: String,
    state: DownloadState,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onResume: (() -> Unit)?,
    onDelete: () -> Unit,
) {
    val done = state.status == DownloadStatus.Done
    Row(
        Modifier.fillMaxWidth().clickable(enabled = done, onClick = onPlay).padding(start = 76.dp, top = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                statusText(state),
                style = MaterialTheme.typography.bodySmall,
                color = if (state.status == DownloadStatus.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (state.active || state.status == DownloadStatus.Paused) {
                val fraction = state.fraction
                if (fraction != null) LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth(0.6f))
                else if (state.active) LinearProgressIndicator(modifier = Modifier.fillMaxWidth(0.6f))
            }
        }
        when {
            done -> IconButton(onClick = onPlay) { Icon(painterResource(Res.drawable.play_arrow_24px), contentDescription = "Play") }
            state.active -> IconButton(onClick = onPause) { Icon(painterResource(Res.drawable.pause_24px), contentDescription = "Pause") }
            onResume != null -> IconButton(onClick = onResume) { Icon(painterResource(Res.drawable.downloading_24px), contentDescription = "Resume") }
        }
        IconButton(onClick = onDelete) { Icon(painterResource(Res.drawable.delete_24px), contentDescription = "Delete") }
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
