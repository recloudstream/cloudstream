package com.lagradost.cloudstream3.tv.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.WideButton
import androidx.tv.material3.WideButtonDefaults
import com.lagradost.cloudstream3.tv.components.TvFocusScale
import com.lagradost.cloudstream3.tv.components.TvMediaCard
import com.lagradost.cloudstream3.tv.model.TvContentRef
import com.lagradost.cloudstream3.tv.model.TvSearchAction
import com.lagradost.cloudstream3.tv.model.TvSearchCatalog
import com.lagradost.cloudstream3.tv.model.TvSearchResult
import com.lagradost.cloudstream3.tv.model.TvAvailabilityClassifier
import com.lagradost.cloudstream3.tv.model.TvAvailabilityKind
import com.lagradost.cloudstream3.tv.model.TvSearchUiState

/**
 * Hoisted Search focus memory — survives Details overlay in [com.lagradost.cloudstream3.tv.navigation.TvNavigationShell].
 * Query text lives in [TvSearchViewModel]; this only tracks field vs results focus + selected index.
 */
class TvSearchFocusState {
    var lastFocusedResultIndex: Int by mutableStateOf(0)
    var restoreToField: Boolean by mutableStateOf(true)
    var initialFocusDone: Boolean by mutableStateOf(false)

    fun pruneTo(resultCount: Int) {
        if (resultCount <= 0) {
            lastFocusedResultIndex = 0
        } else {
            lastFocusedResultIndex = lastFocusedResultIndex.coerceIn(0, resultCount - 1)
        }
    }
}

@Composable
fun rememberTvSearchFocusState(): TvSearchFocusState = remember { TvSearchFocusState() }

@Composable
fun TvSearchScreen(
    focusState: TvSearchFocusState,
    modifier: Modifier = Modifier,
    onOpenDetails: (TvContentRef) -> Unit = {},
    viewModel: TvSearchViewModel = viewModel(),
) {
    val uiState by viewModel.state.collectAsState()
    val query by viewModel.queryText.collectAsState()
    val fieldFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        if (!focusState.initialFocusDone) {
            runCatching { fieldFocus.requestFocus() }
            focusState.initialFocusDone = true
            focusState.restoreToField = true
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Search",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )

        TvSearchInputRow(
            query = query,
            fieldFocus = fieldFocus,
            onQueryChange = { viewModel.onAction(TvSearchAction.UpdateQuery(it)) },
            onSubmit = {
                keyboard?.hide()
                focusState.restoreToField = false
                viewModel.onAction(TvSearchAction.Submit)
            },
            onClear = {
                viewModel.onAction(TvSearchAction.Clear)
                focusState.lastFocusedResultIndex = 0
                focusState.restoreToField = true
                runCatching { fieldFocus.requestFocus() }
            },
            restoreFieldFocus = focusState.restoreToField && uiState !is TvSearchUiState.Content,
            onFieldFocused = { focusState.restoreToField = true },
        )

        when (val state = uiState) {
            TvSearchUiState.Idle -> TvSearchHintPane(
                body = "Type a title, then press Search (or IME Done). Results open the same Details as Home.",
            )
            is TvSearchUiState.Loading -> TvSearchHintPane(
                body = "Searching for \"${state.query}\"…",
            )
            is TvSearchUiState.Content -> {
                focusState.pruneTo(state.catalog.results.size)
                TvSearchResultsGrid(
                    catalog = state.catalog,
                    focusState = focusState,
                    onOpen = { result ->
                        focusState.restoreToField = false
                        onOpenDetails(result.contentRef)
                    },
                )
            }
            is TvSearchUiState.Empty -> TvSearchStatusPane(
                title = TvAvailabilityKind.Unavailable.label,
                body = buildString {
                    append(state.message)
                    if (state.providerCount > 0) {
                        append("\nSearched ${state.providerCount} provider(s).")
                    }
                    if (state.failedProviderCount > 0) {
                        append("\n${state.failedProviderCount} provider(s) failed (partial).")
                    }
                    append("\n\nTry another query, or retry. No silent swap to other content.")
                },
                primaryLabel = "Retry",
                onPrimary = { viewModel.onAction(TvSearchAction.Retry) },
                secondaryLabel = "Clear",
                onSecondary = {
                    viewModel.onAction(TvSearchAction.Clear)
                    focusState.restoreToField = true
                    runCatching { fieldFocus.requestFocus() }
                },
            )
            is TvSearchUiState.Error -> {
                val status = TvAvailabilityClassifier.fromSearchFailure(state.message)
                TvSearchStatusPane(
                    title = status.title,
                    body = state.message + "\n\nRetry or Clear. No silent swap to other results.",
                    primaryLabel = "Retry",
                    onPrimary = { viewModel.onAction(TvSearchAction.Retry) },
                    secondaryLabel = "Clear",
                    onSecondary = {
                        viewModel.onAction(TvSearchAction.Clear)
                        focusState.restoreToField = true
                        runCatching { fieldFocus.requestFocus() }
                    },
                )
            }
        }
    }
}

@Composable
private fun TvSearchInputRow(
    query: String,
    fieldFocus: FocusRequester,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
    restoreFieldFocus: Boolean,
    onFieldFocused: () -> Unit,
) {
    var fieldFocused by remember { mutableStateOf(false) }

    LaunchedEffect(restoreFieldFocus) {
        if (restoreFieldFocus) {
            runCatching { fieldFocus.requestFocus() }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(56.dp)
                .background(
                    color = if (fieldFocused) {
                        MaterialTheme.colorScheme.surfaceVariant
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    shape = RoundedCornerShape(12.dp),
                )
                .padding(horizontal = 20.dp, vertical = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (query.isEmpty()) {
                Text(
                    text = "Search movies, series, anime…",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.titleLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(fieldFocus)
                    .onFocusChanged { state ->
                        fieldFocused = state.isFocused
                        if (state.isFocused) onFieldFocused()
                    },
            )
        }

        WideButton(
            onClick = onSubmit,
            scale = WideButtonDefaults.scale(focusedScale = TvFocusScale.ButtonFocused),
            modifier = Modifier.width(160.dp),
        ) {
            Text("Search")
        }

        WideButton(
            onClick = onClear,
            scale = WideButtonDefaults.scale(focusedScale = TvFocusScale.ButtonFocused),
            modifier = Modifier.width(140.dp),
        ) {
            Text("Clear")
        }
    }
}

@Composable
private fun TvSearchResultsGrid(
    catalog: TvSearchCatalog,
    focusState: TvSearchFocusState,
    onOpen: (TvSearchResult) -> Unit,
) {
    val safeIndex = focusState.lastFocusedResultIndex.coerceIn(0, catalog.results.lastIndex.coerceAtLeast(0))
    val focusRequesters = remember(catalog.query, catalog.results.size) {
        List(catalog.results.size) { FocusRequester() }
    }
    val gridState = rememberLazyGridState()
    var restorePending by remember(catalog.query) { mutableStateOf(true) }

    LaunchedEffect(restorePending, catalog.query, safeIndex, catalog.results.size) {
        if (restorePending && focusRequesters.isNotEmpty() && !focusState.restoreToField) {
            gridState.scrollToItem(safeIndex)
            runCatching { focusRequesters[safeIndex].requestFocus() }
            restorePending = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        val subtitle = buildString {
            append("${catalog.results.size} result(s) for \"${catalog.query}\"")
            if (catalog.failedProviderCount > 0) {
                append(" · ${catalog.failedProviderCount}/${catalog.providerCount} provider(s) failed")
            }
        }
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 148.dp),
            state = gridState,
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(catalog.results, key = { _, item -> item.id }) { index, item ->
                TvMediaCard(
                    item = item.toMediaItem(),
                    onClick = { onOpen(item) },
                    onFocused = { focusState.lastFocusedResultIndex = index },
                    modifier = Modifier.focusRequester(focusRequesters[index]),
                )
            }
        }
    }
}

@Composable
private fun TvSearchHintPane(body: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.TopStart,
    ) {
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TvSearchStatusPane(
    title: String,
    body: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            WideButton(
                onClick = onPrimary,
                scale = WideButtonDefaults.scale(focusedScale = TvFocusScale.ButtonFocused),
            ) {
                Text(primaryLabel)
            }
            if (secondaryLabel != null && onSecondary != null) {
                WideButton(
                    onClick = onSecondary,
                    scale = WideButtonDefaults.scale(focusedScale = TvFocusScale.ButtonFocused),
                ) {
                    Text(secondaryLabel)
                }
            }
        }
    }
}
