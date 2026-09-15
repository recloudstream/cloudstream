package com.lagradost.cloudstream3.tv.watchlist

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.lagradost.cloudstream3.tv.components.TvFocusScale
import com.lagradost.cloudstream3.tv.components.TvMediaCard
import com.lagradost.cloudstream3.tv.components.TvOnResume
import com.lagradost.cloudstream3.tv.model.TvContentRef
import com.lagradost.cloudstream3.tv.model.TvWatchlistAction
import com.lagradost.cloudstream3.tv.model.TvWatchlistCatalog
import com.lagradost.cloudstream3.tv.model.TvWatchlistItem
import com.lagradost.cloudstream3.tv.model.TvWatchlistSection
import com.lagradost.cloudstream3.tv.model.TvAvailabilityClassifier
import com.lagradost.cloudstream3.tv.model.TvAvailabilityKind
import com.lagradost.cloudstream3.tv.model.TvWatchlistUiState

/**
 * Hoisted Watchlist focus — survives Details overlay in the navigation shell.
 */
class TvWatchlistFocusState(
    val sectionFocusIndices: SnapshotStateMap<String, Int> = mutableStateMapOf(),
) {
    var lastFocusedSectionId: String? by mutableStateOf(null)
    var initialFocusDone: Boolean by mutableStateOf(false)

    fun indexFor(sectionId: String): Int = sectionFocusIndices[sectionId] ?: 0

    fun update(sectionId: String, index: Int) {
        sectionFocusIndices[sectionId] = index
        lastFocusedSectionId = sectionId
    }

    fun pruneTo(catalog: TvWatchlistCatalog) {
        val alive = catalog.sections.associate { it.id to it.items.size }
        sectionFocusIndices.keys.filter { it !in alive }.forEach { sectionFocusIndices.remove(it) }
        alive.forEach { (id, size) ->
            if (size <= 0) sectionFocusIndices.remove(id)
            else {
                val idx = sectionFocusIndices[id] ?: return@forEach
                if (idx > size - 1) sectionFocusIndices[id] = size - 1
            }
        }
        if (lastFocusedSectionId != null && lastFocusedSectionId !in alive) {
            lastFocusedSectionId = null
        }
    }
}

@Composable
fun rememberTvWatchlistFocusState(): TvWatchlistFocusState = remember { TvWatchlistFocusState() }

@Composable
fun TvWatchlistScreen(
    focusState: TvWatchlistFocusState,
    modifier: Modifier = Modifier,
    onOpenDetails: (TvContentRef) -> Unit = {},
    viewModel: TvWatchlistViewModel = viewModel(),
) {
    val uiState by viewModel.state.collectAsState()

    // Enter (incl. return from Details) + Activity resume — no polling.
    LaunchedEffect(Unit) { viewModel.onAction(TvWatchlistAction.Refresh) }
    TvOnResume { viewModel.onAction(TvWatchlistAction.Refresh) }

    when (val state = uiState) {
        is TvWatchlistUiState.Loading -> TvWatchlistLoadingPane(modifier)
        is TvWatchlistUiState.Content -> TvWatchlistContentPane(
            catalog = state.catalog,
            focusState = focusState,
            onOpenItem = { item -> onOpenDetails(item.toContentRef()) },
            modifier = modifier,
        )
        is TvWatchlistUiState.Empty -> TvWatchlistStatusPane(
            title = TvAvailabilityKind.Unavailable.label,
            body = buildString {
                append(state.message)
                state.accountName?.let { append("\nProfile: $it") }
                append("\n\nViewing only — add or edit watch status from the phone/tablet UI.")
            },
            primaryLabel = "Retry",
            onPrimary = { viewModel.onAction(TvWatchlistAction.Retry) },
            modifier = modifier,
        )
        is TvWatchlistUiState.Error -> {
            val status = TvAvailabilityClassifier.fromWatchlistFailure(state.message)
            TvWatchlistStatusPane(
                title = status.title,
                body = state.message + "\n\nRetry. No silent swap to demo Library.",
                primaryLabel = "Retry",
                onPrimary = { viewModel.onAction(TvWatchlistAction.Retry) },
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun TvWatchlistContentPane(
    catalog: TvWatchlistCatalog,
    focusState: TvWatchlistFocusState,
    onOpenItem: (TvWatchlistItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingRestoreSectionId by remember { mutableStateOf<String?>(null) }
    val sections = remember(catalog.sections) { catalog.sections.filter { it.items.isNotEmpty() } }

    LaunchedEffect(catalog) {
        focusState.pruneTo(catalog)
    }

    LaunchedEffect(Unit) {
        if (!focusState.initialFocusDone) {
            focusState.initialFocusDone = true
            pendingRestoreSectionId = sections.firstOrNull()?.id
        } else {
            pendingRestoreSectionId = focusState.lastFocusedSectionId ?: sections.firstOrNull()?.id
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        item(key = "header") {
            Text(
                text = "Watchlist",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            val subtitle = buildList {
                add("Local Library")
                catalog.accountName?.let { add("Profile: $it") }
                add("Viewing only")
            }.joinToString(" · ")
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        itemsIndexed(sections, key = { _, section -> section.id }) { _, section ->
            val shouldRestore = pendingRestoreSectionId == section.id
            TvWatchlistSectionRail(
                section = section,
                lastFocusedIndex = focusState.indexFor(section.id),
                onFocusedIndexChanged = { index -> focusState.update(section.id, index) },
                onItemClick = onOpenItem,
                restoreFocus = shouldRestore,
                onRestoreConsumed = {
                    if (pendingRestoreSectionId == section.id) {
                        pendingRestoreSectionId = null
                    }
                },
            )
        }
    }
}

@Composable
private fun TvWatchlistSectionRail(
    section: TvWatchlistSection,
    lastFocusedIndex: Int,
    onFocusedIndexChanged: (Int) -> Unit,
    onItemClick: (TvWatchlistItem) -> Unit,
    restoreFocus: Boolean,
    onRestoreConsumed: () -> Unit,
) {
    if (section.items.isEmpty()) return
    val safeIndex = lastFocusedIndex.coerceIn(0, section.items.lastIndex)
    val focusRequesters = remember(section.id, section.items.size) {
        List(section.items.size) { FocusRequester() }
    }
    val listState = rememberLazyListState()

    LaunchedEffect(restoreFocus, section.id, safeIndex, section.items.size) {
        if (restoreFocus && focusRequesters.isNotEmpty()) {
            listState.scrollToItem(safeIndex)
            runCatching { focusRequesters[safeIndex].requestFocus() }
            onRestoreConsumed()
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = section.title,
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
            itemsIndexed(section.items, key = { _, item -> item.id }) { index, item ->
                TvMediaCard(
                    item = item.toMediaItem(),
                    onClick = { onItemClick(item) },
                    onFocused = { onFocusedIndexChanged(index) },
                    modifier = Modifier.focusRequester(focusRequesters[index]),
                )
            }
        }
    }
}

@Composable
private fun TvWatchlistLoadingPane(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Loading Library…",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TvWatchlistStatusPane(
    title: String,
    body: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val retryFocus = remember { FocusRequester() }
    LaunchedEffect(title) {
        runCatching { retryFocus.requestFocus() }
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(48.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = onPrimary,
            modifier = Modifier.focusRequester(retryFocus),
            scale = ButtonDefaults.scale(focusedScale = TvFocusScale.ButtonFocused),
        ) {
            Text(primaryLabel)
        }
    }
}
