package com.lagradost.cloudstream4.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream4.generated.resources.Res
import com.lagradost.cloudstream4.generated.resources.close
import com.lagradost.cloudstream4.generated.resources.history_toggle_off_24px
import com.lagradost.cloudstream4.generated.resources.search_icon
import com.lagradost.cloudstream4.generated.resources.title_search
import com.lagradost.cloudstream4.search.ProviderStatus
import com.lagradost.cloudstream4.search.SearchAction
import com.lagradost.cloudstream4.search.SearchHistoryEntry
import com.lagradost.cloudstream4.search.SearchViewModel
import com.lagradost.cloudstream4.theme.WhiteButton
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Searches every enabled provider. Results are grouped by provider, the same as the Android app's
 * advanced search, and each provider's row appears as soon as it answers.
 */
@Composable
fun SearchScreen(viewModel: SearchViewModel, openExtensions: () -> Unit, openDetails: (apiName: String, url: String) -> Unit) {
    val state by viewModel.state.collectAsState()
    val focus = remember { FocusRequester() }
    // Opening Search puts the cursor in the box, ready to type
    LaunchedEffect(Unit) { focus.requestFocus() }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.query,
            onValueChange = { viewModel.onAction(SearchAction.QueryChanged(it)) },
            placeholder = { Text(stringResource(Res.string.title_search)) },
            leadingIcon = { Icon(painterResource(Res.drawable.search_icon), contentDescription = null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.onAction(SearchAction.Clear) }) {
                        Icon(painterResource(Res.drawable.close), contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PageGutter, vertical = 16.dp)
                .focusRequester(focus)
                .onPreviewKeyEvent {
                    // Enter searches; handled here because desktop keyboards have no IME search action
                    if (it.type == KeyEventType.KeyDown && (it.key == Key.Enter || it.key == Key.NumPadEnter)) {
                        viewModel.onAction(SearchAction.Submit)
                        true
                    } else false
                },
        )
        if (state.isSearching) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = PageGutter))

        val found = state.results.filter { it.items.isNotEmpty() }
        val failed = state.results.filter { it.status is ProviderStatus.Failed && it.items.isEmpty() }
        when {
            state.noProviders -> Message(
                "No extensions to search with",
                "Install an extension, or check the extension languages and media types under Settings > Providers.",
            ) { WhiteButton(onClick = openExtensions) { Text("Open Extensions") } }

            state.searchedQuery.isEmpty() && state.history.isNotEmpty() -> SearchHistory(
                entries = state.history,
                onSearch = { viewModel.onAction(SearchAction.SearchFor(it.query)) },
                onRemove = { viewModel.onAction(SearchAction.RemoveHistory(it.key)) },
                onClear = { viewModel.onAction(SearchAction.ClearHistory) },
            )

            state.searchedQuery.isEmpty() -> Message("Search all your extensions", "Type a title and press Enter.")

            found.isEmpty() && !state.isSearching -> Message(
                "Nothing found for \"${state.searchedQuery}\"",
                if (failed.isEmpty()) null else "${failed.size} of ${state.results.size} extensions could not be reached.",
            )

            else -> LazyColumn(
                contentPadding = PaddingValues(bottom = DockClearance),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(found) { row ->
                    val loading = row.status == ProviderStatus.Loading
                    PosterRow(
                        title = row.apiName,
                        subtitle = "${row.items.size} results",
                        items = row.items,
                        horizontal = false,
                        modifier = Modifier.padding(top = 8.dp),
                        onItemClick = { openDetails(it.apiName, it.url) },
                        trailing = if (row.hasNext) {
                            { MoreButton(loading, horizontal = false) { viewModel.onAction(SearchAction.LoadMore(row.apiName)) } }
                        } else null,
                    )
                }
                if (failed.isNotEmpty()) item {
                    Text(
                        "Could not reach: " + failed.joinToString { it.apiName },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                    )
                }
            }
        }
    }
}

/** Past searches, newest first, like the Android app's search history */
@Composable
private fun SearchHistory(
    entries: List<SearchHistoryEntry>,
    onSearch: (SearchHistoryEntry) -> Unit,
    onRemove: (SearchHistoryEntry) -> Unit,
    onClear: () -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(bottom = DockClearance), modifier = Modifier.fillMaxSize()) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Recent searches", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onClear) { Text("Clear all") }
            }
        }
        items(entries, key = { it.key }) { entry ->
            Row(
                Modifier.fillMaxWidth().clickable { onSearch(entry) }.padding(start = 24.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painterResource(Res.drawable.history_toggle_off_24px),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    entry.query,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                )
                IconButton(onClick = { onRemove(entry) }) {
                    Icon(painterResource(Res.drawable.close), contentDescription = "Remove from history")
                }
            }
        }
    }
}
