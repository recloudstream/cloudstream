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
import com.lagradost.cloudstream3.tv.model.TvMockRail

/**
 * Horizontal content rail with per-rail focus memory.
 * [lastFocusedIndex] is owned by [TvHomeFocusState] so leaving/returning Home restores focus.
 */
@Composable
fun TvContentRail(
    rail: TvMockRail,
    lastFocusedIndex: Int,
    onFocusedIndexChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
    restoreFocus: Boolean = false,
    onRestoreConsumed: () -> Unit = {},
) {
    val safeIndex = lastFocusedIndex.coerceIn(0, (rail.items.size - 1).coerceAtLeast(0))
    val focusRequesters = remember(rail.id, rail.items.size) {
        List(rail.items.size) { FocusRequester() }
    }
    val listState = rememberLazyListState()

    LaunchedEffect(restoreFocus, rail.id, safeIndex) {
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
            text = rail.title,
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
                    onClick = {},
                    onFocused = { onFocusedIndexChanged(index) },
                    modifier = Modifier.focusRequester(focusRequesters[index]),
                )
            }
        }
    }
}
