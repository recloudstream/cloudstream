package com.lagradost.cloudstream4.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream4.generated.resources.Res
import com.lagradost.cloudstream4.generated.resources.close
import com.lagradost.cloudstream4.library.PlaybackPosition
import com.lagradost.cloudstream4.library.ResumeEntry
import com.lagradost.cloudstream4.library.TitleHeader
import com.lagradost.cloudstream4.library.WatchStore
import com.lagradost.cloudstream4.library.WatchType
import com.lagradost.cloudstream4.theme.AppFilterChip
import org.jetbrains.compose.resources.painterResource

/** The titles the user put in a library list, one list at a time, most recently changed first */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LibraryScreen(store: WatchStore, openDetails: (apiName: String, url: String) -> Unit) {
    val version by store.version.collectAsState()
    val bookmarks = remember(version) { store.bookmarks().sortedByDescending { it.latestUpdatedTime } }
    var selected by rememberSaveable { mutableStateOf(WatchType.WATCHING) }
    val shown = bookmarks.filter { it.status == selected }

    Column(Modifier.fillMaxSize()) {
        Text("Library", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 24.dp, top = 24.dp, end = 24.dp))
        // Product Sans draws "(5)" as a circled digit, so counts use a separator instead of parentheses
        FlowRow(Modifier.padding(horizontal = 24.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WatchType.lists.forEach { type ->
                val count = bookmarks.count { it.status == type }
                AppFilterChip(
                    selected = type == selected,
                    onClick = { selected = type },
                    label = { Text(if (count > 0) "${type.label} · $count" else type.label) },
                )
            }
        }
        if (shown.isEmpty()) {
            Message(
                "Nothing in ${selected.label} yet",
                "Open a title and use Add to library to keep it here.",
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(shown, key = { it.header.id }) { bookmark ->
                    TitleCard(bookmark.header, onClick = { openDetails(bookmark.header.apiName, bookmark.header.url) })
                }
            }
        }
    }
}

@Composable
private fun TitleCard(header: TitleHeader, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick)) {
        RemoteImage(header.posterUrl, header.posterHeaders, contentDescription = header.name, modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f))
        Text(header.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        Text(
            listOfNotNull(header.year?.toString(), header.apiName).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The titles the user started, newest first, like the Android app's Continue watching row. Each is
 * a small card: the poster, the title, how far its episode got, and a button to drop it from the
 * row. Opening one goes to its page, where Resume picks it up.
 */
@Composable
fun ContinueWatchingRow(store: WatchStore, openDetails: (apiName: String, url: String) -> Unit, modifier: Modifier = Modifier) {
    val version by store.version.collectAsState()
    val entries = remember(version) { store.resumeEntries().sortedByDescending { it.updateTime } }
    if (entries.isEmpty()) return
    Column(modifier.fillMaxWidth()) {
        Text("Continue watching", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(entries, key = { it.header.id }) { entry ->
                val position = remember(version, entry) { store.position(entry.episodeId ?: entry.header.id) }
                ContinueCard(
                    entry = entry,
                    position = position,
                    onClick = { openDetails(entry.header.apiName, entry.header.url) },
                    onRemove = { store.removeResume(entry.header.id) },
                )
            }
        }
    }
}

@Composable
private fun ContinueCard(entry: ResumeEntry, position: PlaybackPosition?, onClick: () -> Unit, onRemove: () -> Unit) {
    val header = entry.header
    Row(
        Modifier
            .width(280.dp)
            .height(84.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
    ) {
        RemoteImage(header.posterUrl, header.posterHeaders, contentDescription = header.name, modifier = Modifier.width(56.dp).fillMaxHeight())
        Column(Modifier.weight(1f).fillMaxHeight().padding(start = 10.dp, top = 8.dp, bottom = 8.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(header.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val episode = listOfNotNull(entry.season?.let { "S$it" }, entry.episode?.let { "E$it" }).joinToString(" ")
            val left = position?.takeIf { !it.watched && it.durationMs > 0 }?.let { "${(it.durationMs - it.positionMs) / 60_000} min left" }
            Text(
                listOfNotNull(episode.ifEmpty { null }, left).joinToString(" · ").ifEmpty { header.apiName },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            LinearProgressIndicator(
                progress = { if (position == null || position.watched) 0f else position.fraction },
                modifier = Modifier.fillMaxWidth().height(3.dp),
            )
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
            Icon(painterResource(Res.drawable.close), contentDescription = "Remove from Continue watching", modifier = Modifier.size(16.dp))
        }
    }
}
