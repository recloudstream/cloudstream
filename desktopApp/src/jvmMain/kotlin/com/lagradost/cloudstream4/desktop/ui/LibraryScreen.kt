package com.lagradost.cloudstream4.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream4.desktop.data.AppData

private enum class LibraryTab(val title: String) { Bookmarks("Bookmarks"), History("History") }

@Composable
fun LibraryScreen() {
    val bookmarks by AppData.bookmarks.collectAsState()
    val history by AppData.history.collectAsState()
    val positions by AppData.positions.collectAsState()
    var tab by remember { mutableStateOf(LibraryTab.Bookmarks) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LibraryTab.entries.forEach {
                FilterChip(tab == it, onClick = { tab = it }, label = { Text(it.title) })
            }
            if (tab == LibraryTab.History && history.isNotEmpty()) {
                TextButton(onClick = AppData::clearHistory) { Text("Clear history") }
            }
        }

        val empty = if (tab == LibraryTab.Bookmarks) bookmarks.isEmpty() else history.isEmpty()
        if (empty) {
            MessageBox(
                if (tab == LibraryTab.Bookmarks) "No bookmarks yet" else "Nothing watched yet",
                if (tab == LibraryTab.Bookmarks) "Bookmark something from its page to find it here." else null,
            )
            return
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            contentPadding = PaddingValues(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            when (tab) {
                LibraryTab.Bookmarks -> items(bookmarks, key = { it.url }) { MediaEntryCard(it) }
                LibraryTab.History -> items(history, key = { it.media.url }) { progress ->
                    MediaEntryCard(
                        progress.media,
                        subtitle = progress.episodeName ?: progress.media.apiName,
                        progress = progress.episodeData?.let { positions[it]?.fraction },
                    )
                }
            }
        }
    }
}
