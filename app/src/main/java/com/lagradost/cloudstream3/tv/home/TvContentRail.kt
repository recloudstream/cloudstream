package com.lagradost.cloudstream3.tv.home

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.lagradost.cloudstream3.tv.components.TvMediaCard
import com.lagradost.cloudstream3.tv.model.TvContentRail
import com.lagradost.cloudstream3.tv.model.TvMediaItem

/**
 * Horizontal content rail with per-rail focus memory.
 * Skipped when [rail.items] is empty so empty rails never trap D-pad focus.
 * [lastFocusedIndex] is owned by [TvHomeFocusState]; coerced to dynamic item count.
 */
@Composable
fun TvContentRail(
    rail: TvContentRail,
    lastFocusedIndex: Int,
    onFocusedIndexChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onItemClick: (TvMediaItem) -> Unit = {},
    onItemLongClick: ((TvMediaItem) -> Unit)? = null,
    restoreFocus: Boolean = false,
    onRestoreConsumed: () -> Unit = {},
) {
    if (rail.items.isEmpty()) return

    val safeIndex = lastFocusedIndex.coerceIn(0, rail.items.lastIndex)
    val focusRequesters = remember(rail.id, rail.items.size) {
        List(rail.items.size) { FocusRequester() }
    }
    val listState = rememberLazyListState()

    LaunchedEffect(restoreFocus, rail.id, safeIndex, rail.items.size) {
        if (restoreFocus && focusRequesters.isNotEmpty()) {
            listState.scrollToItem(safeIndex)
            runCatching { focusRequesters[safeIndex].requestFocus() }
            onRestoreConsumed()
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = if (rail.isMock) "${rail.title}  ·  Demo" else rail.title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .focusRestorer(focusRequesters.getOrNull(safeIndex) ?: FocusRequester.Default)
                .focusGroup(),
        ) {
            itemsIndexed(rail.items, key = { _, item -> item.id }) { index, item ->
                TvMediaCard(
                    item = item,
                    onClick = { onItemClick(item) },
                    onLongClick = onItemLongClick?.let { handler -> { handler(item) } },
                    onFocused = { onFocusedIndexChanged(index) },
                    modifier = Modifier.focusRequester(focusRequesters[index]),
                )
            }
        }
    }
}
