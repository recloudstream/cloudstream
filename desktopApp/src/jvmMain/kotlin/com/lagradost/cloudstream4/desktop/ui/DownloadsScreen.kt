package com.lagradost.cloudstream4.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream4.desktop.downloads.DownloadItem
import com.lagradost.cloudstream4.desktop.downloads.DownloadManager
import com.lagradost.cloudstream4.desktop.downloads.DownloadStatus
import com.lagradost.cloudstream4.desktop.generated.resources.Res
import com.lagradost.cloudstream4.desktop.generated.resources.delete
import com.lagradost.cloudstream4.desktop.generated.resources.download
import com.lagradost.cloudstream4.desktop.generated.resources.pause
import com.lagradost.cloudstream4.desktop.generated.resources.play
import org.jetbrains.compose.resources.painterResource
import java.awt.Desktop

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.2f GB".format(bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> "%.1f MB".format(bytes / (1L shl 20).toDouble())
    bytes >= 1L shl 10 -> "%d KB".format(bytes / (1L shl 10))
    else -> "$bytes B"
}

@Composable
fun DownloadsScreen() {
    val downloads by DownloadManager.downloads.collectAsState()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Downloads", style = MaterialTheme.typography.headlineMedium)
                Text(DownloadManager.directory.absolutePath, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(onClick = {
                DownloadManager.directory.mkdirs()
                runCatching { Desktop.getDesktop().open(DownloadManager.directory) }
            }) { Text("Open folder") }
        }
        if (downloads.isEmpty()) {
            MessageBox(
                "Nothing downloaded yet",
                "Use the download button on a movie or episode to watch it offline.",
            )
            return
        }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            // Grouped by show, newest first
            val groups = downloads.groupBy { it.media.url }
            groups.forEach { (_, items) ->
                item(key = "header" + items.first().media.url) {
                    Text(items.first().media.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                }
                items(items.sortedWith(compareBy({ it.season }, { it.episode })), key = { it.id }) { DownloadRow(it) }
            }
        }
    }
}

@Composable
private fun DownloadRow(item: DownloadItem) {
    val navigator = LocalNavigator.current
    val playable = Playable(item.id, item.title, item.season, item.episode)
    fun play() = navigator.push(Route.Player(item.media, listOf(playable), 0))

    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = item.status == DownloadStatus.Completed) { play() }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(110.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp))) {
            RemoteImage(item.media.posterUrl, Modifier.fillMaxSize(), item.media.posterHeaders)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val detail = when (item.status) {
                DownloadStatus.Queued -> "Waiting"
                DownloadStatus.Downloading -> if (item.totalBytes == 0L && item.segmentsTotal == 0) "Finding links..."
                else "${(item.progress * 100).toInt()}% · ${formatBytes(item.downloadedBytes)}" +
                    (if (item.totalBytes > 0) " of ${formatBytes(item.totalBytes)}" else "")

                DownloadStatus.Paused -> "Paused · ${(item.progress * 100).toInt()}%"
                DownloadStatus.Completed -> "Downloaded · ${formatBytes(item.file.length())}"
                DownloadStatus.Failed -> "Failed: ${item.error}"
            }
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = if (item.status == DownloadStatus.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.status == DownloadStatus.Downloading || item.status == DownloadStatus.Paused) {
                LinearProgressIndicator(progress = { item.progress }, modifier = Modifier.fillMaxWidth(), drawStopIndicator = {})
            }
        }
        Spacer(Modifier.width(8.dp))
        when (item.status) {
            DownloadStatus.Completed -> IconButton(onClick = ::play) { Icon(painterResource(Res.drawable.play), "Play") }
            DownloadStatus.Downloading, DownloadStatus.Queued ->
                IconButton(onClick = { DownloadManager.pause(item.id) }) { Icon(painterResource(Res.drawable.pause), "Pause") }

            DownloadStatus.Paused, DownloadStatus.Failed ->
                TextButton(onClick = { DownloadManager.resume(item.id) }) { Text(if (item.status == DownloadStatus.Failed) "Retry" else "Resume") }
        }
        IconButton(onClick = { DownloadManager.delete(item.id) }) { Icon(painterResource(Res.drawable.delete), "Delete") }
    }
}

/** Small download button with live state, used on movies and episodes */
@Composable
fun DownloadButton(media: com.lagradost.cloudstream4.desktop.data.MediaEntry, playable: Playable) {
    val downloads by DownloadManager.downloads.collectAsState()
    val item = downloads.firstOrNull { it.id == playable.data }
    when (item?.status) {
        null, DownloadStatus.Failed -> IconButton(onClick = { DownloadManager.enqueue(media, playable) }) {
            Icon(painterResource(Res.drawable.download), "Download")
        }

        DownloadStatus.Completed -> Text("Downloaded", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        DownloadStatus.Paused -> TextButton(onClick = { DownloadManager.resume(item.id) }) { Text("Resume") }
        DownloadStatus.Queued, DownloadStatus.Downloading -> Box(contentAlignment = Alignment.Center, modifier = Modifier.size(40.dp)) {
            CircularProgressIndicator(progress = { item.progress }, modifier = Modifier.size(28.dp))
        }
    }
}
