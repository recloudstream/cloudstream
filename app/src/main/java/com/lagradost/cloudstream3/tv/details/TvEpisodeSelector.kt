package com.lagradost.cloudstream3.tv.details

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.lagradost.cloudstream3.tv.components.TvFocusScale
import com.lagradost.cloudstream3.tv.model.TvDetailsAction
import com.lagradost.cloudstream3.tv.model.TvDetailsUiState
import com.lagradost.cloudstream3.tv.model.TvDubGroup
import com.lagradost.cloudstream3.tv.model.TvEpisode
import com.lagradost.cloudstream3.tv.model.TvSeason

/**
 * 10ft season + episode selector for Series / Anime.
 * Selection events go to ViewModel — FocusRequester stays in composition only.
 * Uses LazyRow / LazyColumn + focusRestorer (Phase 2 pattern).
 */
@Composable
fun TvEpisodeSelector(
    content: TvDetailsUiState.Content,
    onAction: (TvDetailsAction) -> Unit,
    modifier: Modifier = Modifier,
    restoreEpisodeFocus: Boolean = false,
    onRestoreConsumed: () -> Unit = {},
) {
    val seasons = content.visibleSeasons
    val episodes = content.visibleEpisodes
    if (seasons.isEmpty()) {
        Text(
            text = "No episodes available",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(top = 8.dp),
        )
        return
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (content.showDubSelector) {
            TvDubRow(
                groups = content.details.dubGroups,
                selectedId = content.selectedDubStatusId,
                onSelect = { onAction(TvDetailsAction.SelectDubStatus(it)) },
            )
        }
        TvSeasonRow(
            seasons = seasons,
            selectedIndex = content.selectedSeasonIndex,
            onSelect = { onAction(TvDetailsAction.SelectSeason(it)) },
        )
        Text(
            text = content.selectedSeason?.label?.let { "$it · ${episodes.size} episodes" }
                ?: "${episodes.size} episodes",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        TvEpisodeList(
            episodes = episodes,
            selectedId = content.selectedEpisodeId,
            onSelect = { onAction(TvDetailsAction.SelectEpisode(it)) },
            onPlay = { id ->
                onAction(TvDetailsAction.SelectEpisode(id))
                onAction(TvDetailsAction.PlaySelectedEpisode)
            },
            restoreFocus = restoreEpisodeFocus,
            onRestoreConsumed = onRestoreConsumed,
        )
    }
}

@Composable
private fun TvDubRow(
    groups: List<TvDubGroup>,
    selectedId: Int?,
    onSelect: (Int) -> Unit,
) {
    val focusRequesters = remember(groups.map { it.dubStatusId }) {
        List(groups.size) { FocusRequester() }
    }
    val selectedPos = groups.indexOfFirst { it.dubStatusId == selectedId }.coerceAtLeast(0)
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .focusRestorer(focusRequesters.getOrNull(selectedPos) ?: FocusRequester.Default)
            .focusGroup(),
    ) {
        itemsIndexed(groups, key = { _, g -> g.dubStatusId }) { index, group ->
            val selected = group.dubStatusId == selectedId
            SelectorChip(
                label = group.label.ifBlank { "Default" },
                selected = selected,
                onClick = { onSelect(group.dubStatusId) },
                modifier = Modifier.focusRequester(focusRequesters[index]),
            )
        }
    }
}

@Composable
private fun TvSeasonRow(
    seasons: List<TvSeason>,
    selectedIndex: Int?,
    onSelect: (Int) -> Unit,
) {
    val focusRequesters = remember(seasons.map { it.seasonIndex }) {
        List(seasons.size) { FocusRequester() }
    }
    val listState = rememberLazyListState()
    val selectedPos = seasons.indexOfFirst { it.seasonIndex == selectedIndex }.coerceAtLeast(0)

    LaunchedEffect(selectedIndex, seasons.size) {
        if (seasons.isNotEmpty()) {
            listState.animateScrollToItem(selectedPos.coerceIn(0, seasons.lastIndex))
        }
    }

    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .focusRestorer(focusRequesters.getOrNull(selectedPos) ?: FocusRequester.Default)
            .focusGroup(),
    ) {
        itemsIndexed(seasons, key = { _, s -> s.seasonIndex }) { index, season ->
            val selected = season.seasonIndex == selectedIndex
            SelectorChip(
                label = season.label,
                selected = selected,
                onClick = { onSelect(season.seasonIndex) },
                modifier = Modifier.focusRequester(focusRequesters[index]),
            )
        }
    }
}

@Composable
private fun TvEpisodeList(
    episodes: List<TvEpisode>,
    selectedId: Int?,
    onSelect: (Int) -> Unit,
    onPlay: (Int) -> Unit,
    restoreFocus: Boolean,
    onRestoreConsumed: () -> Unit,
) {
    if (episodes.isEmpty()) {
        Text(
            text = "No episodes in this season",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val focusRequesters = remember(episodes.map { it.id }) {
        List(episodes.size) { FocusRequester() }
    }
    val listState = rememberLazyListState()
    val selectedPos = episodes.indexOfFirst { it.id == selectedId }.let {
        if (it >= 0) it else 0
    }

    LaunchedEffect(restoreFocus, selectedId, episodes.size) {
        if (restoreFocus && focusRequesters.isNotEmpty()) {
            val idx = selectedPos.coerceIn(0, focusRequesters.lastIndex)
            listState.scrollToItem(idx)
            runCatching { focusRequesters[idx].requestFocus() }
            onRestoreConsumed()
        }
    }

    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 280.dp)
            .focusRestorer(focusRequesters.getOrNull(selectedPos) ?: FocusRequester.Default)
            .focusGroup(),
    ) {
        itemsIndexed(episodes, key = { _, ep -> ep.id }) { index, episode ->
            EpisodeRow(
                episode = episode,
                selected = episode.id == selectedId,
                onFocus = { onSelect(episode.id) },
                onClick = {
                    onSelect(episode.id)
                    if (episode.isPlayable) onPlay(episode.id)
                },
                modifier = Modifier.focusRequester(focusRequesters[index]),
            )
        }
    }
}

@Composable
private fun SelectorChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.widthIn(min = 96.dp),
        scale = ClickableSurfaceDefaults.scale(focusedScale = TvFocusScale.ButtonFocused),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            },
            focusedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            focusedContentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun EpisodeRow(
    episode: TvEpisode,
    selected: Boolean,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        enabled = episode.isPlayable,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { if (it.isFocused) onFocus() },
        scale = ClickableSurfaceDefaults.scale(focusedScale = TvFocusScale.ButtonFocused),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            },
            focusedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            focusedContentColor = MaterialTheme.colorScheme.onSurface,
            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = episode.titleLine,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = buildList {
                    episode.scoreLabel?.let { add("★ $it") }
                    episode.runTime?.takeIf { it > 0 }?.let { add("${it}m") }
                    if (!episode.isPlayable) add("Unavailable")
                    episode.description?.takeIf { it.isNotBlank() }?.let { add(it) }
                }.joinToString("  •  ")
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
