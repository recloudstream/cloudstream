package com.lagradost.cloudstream4.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream4.desktop.generated.resources.Res
import com.lagradost.cloudstream4.desktop.generated.resources.search
import com.lagradost.cloudstream4.desktop.plugins.PluginManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.jetbrains.compose.resources.painterResource

data class ProviderResults(val provider: String, val items: List<SearchResponse>, val error: String? = null)

enum class SearchFilter(val title: String, val types: Set<TvType>?) {
    All("All", null),
    Movies("Movies", setOf(TvType.Movie, TvType.AnimeMovie, TvType.Documentary, TvType.Torrent)),
    Series("TV Series", setOf(TvType.TvSeries, TvType.Cartoon, TvType.AsianDrama, TvType.Documentary)),
    Anime("Anime", setOf(TvType.Anime, TvType.AnimeMovie, TvType.OVA)),
    Live("Live", setOf(TvType.Live)),
}

data class SearchState(
    val query: String = "",
    val filter: SearchFilter = SearchFilter.All,
    val results: List<ProviderResults> = emptyList(),
    val pending: Int = 0,
)

class SearchViewModel(private val scope: CoroutineScope) {
    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()
    private var job: Job? = null

    fun onQueryChange(query: String) = _state.update { it.copy(query = query) }

    fun onFilterChange(filter: SearchFilter) {
        _state.update { it.copy(filter = filter) }
        if (_state.value.results.isNotEmpty()) search()
    }

    fun search() {
        val query = _state.value.query.trim()
        if (query.isEmpty()) return
        val filter = _state.value.filter
        val providers = PluginManager.providers().filter { api ->
            filter.types == null || api.supportedTypes.any { it in filter.types }
        }
        job?.cancel()
        _state.update { it.copy(results = emptyList(), pending = providers.size) }
        job = scope.launch {
            providers.forEach { api ->
                launch(Dispatchers.IO) {
                    val result = try {
                        val items = withTimeout(api.searchTimeoutMs ?: 30_000L) { api.search(query, 1) }?.items.orEmpty()
                        ProviderResults(api.name, items.filter { item ->
                            filter.types == null || item.type == null || item.type in filter.types
                        })
                    } catch (t: Throwable) {
                        if (t is CancellationException && t !is TimeoutCancellationException) throw t
                        logError(t)
                        ProviderResults(api.name, emptyList(), t.message ?: t.javaClass.simpleName)
                    }
                    _state.update {
                        it.copy(
                            // Providers with results first, keep the order they arrive in
                            results = (it.results + result).sortedBy { r -> r.items.isEmpty() },
                            pending = it.pending - 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SearchScreen(viewModel: SearchViewModel) {
    val state by viewModel.state.collectAsState()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            placeholder = { Text("Search all extensions") },
            leadingIcon = { Icon(painterResource(Res.drawable.search), null) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { viewModel.search() }),
            modifier = Modifier.fillMaxWidth().padding(16.dp).focusRequester(focus)
                .onEnter { viewModel.search() },
        )
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SearchFilter.entries.forEach { filter ->
                FilterChip(
                    selected = state.filter == filter,
                    onClick = { viewModel.onFilterChange(filter) },
                    label = { Text(filter.title) },
                )
            }
        }
        if (state.pending > 0) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))

        if (PluginManager.providers().isEmpty()) {
            MessageBox("No extensions installed", "Install extensions from the Extensions tab to search.")
            return
        }

        LazyColumn(Modifier.fillMaxSize()) {
            items(state.results, key = { it.provider }) { result ->
                when {
                    result.items.isNotEmpty() -> SearchResponseRow(result.provider, result.items)
                    else -> Text(
                        "${result.provider}: ${result.error ?: "No results"}",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
