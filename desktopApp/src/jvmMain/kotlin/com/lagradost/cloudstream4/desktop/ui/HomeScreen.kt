package com.lagradost.cloudstream4.desktop.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageData
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream4.desktop.data.AppData
import com.lagradost.cloudstream4.desktop.generated.resources.Res
import com.lagradost.cloudstream4.desktop.generated.resources.arrow_down
import com.lagradost.cloudstream4.desktop.generated.resources.refresh
import com.lagradost.cloudstream4.desktop.plugins.PluginManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.jetbrains.compose.resources.painterResource

data class HomeState(
    val provider: String? = null,
    val rows: List<HomePageList> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

class HomeViewModel(private val scope: CoroutineScope) {
    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()
    private var job: Job? = null

    fun currentApi(): MainAPI? {
        val providers = PluginManager.providers().filter { it.hasMainPage }
        val wanted = _state.value.provider ?: AppData.settings.value.homeProvider
        return providers.firstOrNull { it.name == wanted } ?: providers.firstOrNull()
    }

    fun select(name: String) {
        AppData.updateSettings { it.copy(homeProvider = name) }
        _state.value = HomeState(provider = name)
        load()
    }

    /** Reloads if the provider disappeared or nothing was loaded yet */
    fun ensureLoaded() {
        val api = currentApi()
        val current = _state.value
        if (api == null) {
            job?.cancel()
            _state.value = HomeState()
        } else if (current.provider != api.name || (current.rows.isEmpty() && !current.isLoading && current.error == null)) {
            _state.value = HomeState(provider = api.name)
            load()
        }
    }

    fun load() {
        val api = currentApi() ?: return
        job?.cancel()
        _state.value = HomeState(provider = api.name, isLoading = true)
        job = scope.launch {
            val lock = Mutex()
            val errors = mutableListOf<Throwable>()
            suspend fun fetch(data: MainPageData) {
                try {
                    val response = withTimeout(api.getMainPageTimeoutMs ?: 60_000L) {
                        api.getMainPage(1, MainPageRequest(data.name, data.data, data.horizontalImages))
                    }
                    val rows = response?.items.orEmpty().filter { it.list.isNotEmpty() }
                    lock.withLock { _state.update { it.copy(rows = it.rows + rows) } }
                } catch (t: Throwable) {
                    if (t is CancellationException && t !is TimeoutCancellationException) throw t
                    logError(t)
                    lock.withLock { errors += t }
                }
            }

            if (api.sequentialMainPage) {
                api.mainPage.forEach {
                    fetch(it)
                    delay(api.sequentialMainPageDelay)
                }
            } else {
                api.mainPage.map { launch(Dispatchers.IO) { fetch(it) } }.forEach { it.join() }
            }

            _state.update {
                it.copy(
                    isLoading = false,
                    error = if (it.rows.isEmpty()) errors.firstOrNull()?.let { e -> e.message ?: e.javaClass.simpleName }
                        ?: "Nothing found" else null
                )
            }
        }
    }
}

@Composable
fun HomeScreen(viewModel: HomeViewModel) {
    val navigator = LocalNavigator.current
    val providersVersion by PluginManager.providersVersion.collectAsState()
    val state by viewModel.state.collectAsState()
    val history by AppData.history.collectAsState()
    val positions by AppData.positions.collectAsState()

    LaunchedEffect(providersVersion) { viewModel.ensureLoaded() }
    val providers = remember(providersVersion) { PluginManager.providers().filter { it.hasMainPage } }

    if (providers.isEmpty()) {
        val anyProviders = remember(providersVersion) { PluginManager.providers().isNotEmpty() }
        MessageBox(
            "No extensions with a home page installed",
            if (anyProviders) "Your installed extensions only support search, use the Search tab or install more extensions."
            else "Add a repository and install an extension (a site provider, not a repository helper) to start browsing.",
            action = "Open extensions" to { navigator.select(Tab.Extensions) },
        )
        return
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(onClick = { expanded = true }) {
                            Text(state.provider ?: "Select provider")
                            Spacer(Modifier.width(4.dp))
                            Icon(painterResource(Res.drawable.arrow_down), null)
                        }
                        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                            providers.sortedBy { it.name.lowercase() }.forEach { api ->
                                DropdownMenuItem(
                                    text = { Text("${api.name}  ·  ${api.lang}") },
                                    onClick = {
                                        expanded = false
                                        viewModel.select(api.name)
                                    },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = viewModel::load) { Icon(painterResource(Res.drawable.refresh), "Reload") }
                }
                if (state.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            if (history.isNotEmpty()) {
                item {
                    SectionTitle("Continue watching")
                    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp)) {
                        items(history.take(20), key = { it.media.url }) { progress ->
                            MediaEntryCard(
                                progress.media,
                                subtitle = progress.episodeName ?: progress.media.apiName,
                                progress = progress.episodeData?.let { positions[it]?.fraction },
                            )
                        }
                    }
                }
            }

            items(state.rows, key = { it.name + it.list.firstOrNull()?.url }) { row ->
                SearchResponseRow(row.name, row.list, row.isHorizontalImages)
            }

            if (state.error != null) {
                item {
                    MessageBox(
                        "Could not load ${state.provider}",
                        state.error,
                        Modifier.fillMaxWidth(),
                        action = "Retry" to viewModel::load,
                    )
                }
            }
        }
    }
}
