package com.lagradost.cloudstream4.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream4.desktop.data.AppData
import com.lagradost.cloudstream4.desktop.data.RepositoryEntry
import com.lagradost.cloudstream4.desktop.generated.resources.Res
import com.lagradost.cloudstream4.desktop.generated.resources.arrow_back
import com.lagradost.cloudstream4.desktop.generated.resources.delete
import com.lagradost.cloudstream4.desktop.generated.resources.update
import com.lagradost.cloudstream4.desktop.plugins.PluginManager
import com.lagradost.cloudstream4.desktop.plugins.RemotePlugin
import com.lagradost.cloudstream4.desktop.plugins.Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

val LocalSnackbar = compositionLocalOf { SnackbarHostState() }

data class RepositoryPlugins(
    val repository: Repository? = null,
    val plugins: List<RemotePlugin> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

data class ExtensionsState(
    val openRepository: RepositoryEntry? = null,
    val repositoryPlugins: Map<String, RepositoryPlugins> = emptyMap(),
    /** Internal names of extensions being installed or removed */
    val busy: Set<String> = emptySet(),
    val isAddingRepository: Boolean = false,
    val isUpdating: Boolean = false,
    val showSettings: Boolean = false,
)

class ExtensionsViewModel(private val scope: CoroutineScope, private val notify: (String) -> Unit) {
    private val _state = MutableStateFlow(ExtensionsState())
    val state: StateFlow<ExtensionsState> = _state.asStateFlow()

    fun addRepository(input: String) {
        if (input.isBlank()) return
        _state.update { it.copy(isAddingRepository = true) }
        scope.launch {
            val repo = PluginManager.addRepository(input)
            _state.update { it.copy(isAddingRepository = false) }
            if (repo == null) notify("Not a valid repository: $input") else {
                notify("Added ${repo.name}")
                open(repo)
            }
        }
    }

    fun removeRepository(repo: RepositoryEntry) = scope.launch {
        PluginManager.removeRepository(repo.url)
        _state.update { it.copy(openRepository = null, repositoryPlugins = it.repositoryPlugins - repo.url) }
        notify("Removed ${repo.name}")
    }

    fun showSettings(show: Boolean) = _state.update { it.copy(showSettings = show) }

    fun open(repo: RepositoryEntry?) {
        _state.update { it.copy(openRepository = repo) }
        if (repo != null && _state.value.repositoryPlugins[repo.url]?.error == null &&
            _state.value.repositoryPlugins[repo.url]?.plugins.isNullOrEmpty()
        ) refresh(repo)
    }

    fun refresh(repo: RepositoryEntry) {
        _state.update { it.copy(repositoryPlugins = it.repositoryPlugins + (repo.url to RepositoryPlugins())) }
        scope.launch {
            val repository = PluginManager.fetchRepository(repo.url)
            val result = if (repository == null) {
                RepositoryPlugins(isLoading = false, error = "Could not load the repository")
            } else {
                RepositoryPlugins(repository, PluginManager.fetchRepositoryPlugins(repository).sortedBy { it.name.lowercase() }, false)
            }
            _state.update { it.copy(repositoryPlugins = it.repositoryPlugins + (repo.url to result)) }
        }
    }

    private fun busy(name: String, isBusy: Boolean) =
        _state.update { it.copy(busy = if (isBusy) it.busy + name else it.busy - name) }

    fun install(repo: RepositoryEntry, plugin: RemotePlugin) {
        busy(plugin.internalName, true)
        scope.launch {
            PluginManager.install(repo.url, plugin)
                .onSuccess { notify("Installed ${plugin.name}") }
                .onFailure { notify("Failed to install ${plugin.name}: ${it.message}") }
            busy(plugin.internalName, false)
        }
    }

    fun uninstall(repo: RepositoryEntry, plugin: RemotePlugin) {
        val installed = PluginManager.installedFor(repo.url, plugin.internalName) ?: return
        busy(plugin.internalName, true)
        scope.launch {
            PluginManager.uninstall(installed)
            busy(plugin.internalName, false)
            notify("Removed ${plugin.name}")
        }
    }

    fun installAll(repo: RepositoryEntry) {
        val plugins = _state.value.repositoryPlugins[repo.url]?.plugins.orEmpty()
            .filter { it.status != 0 && PluginManager.installedFor(repo.url, it.internalName) == null }
        plugins.forEach { install(repo, it) }
    }

    fun updateAll() {
        _state.update { it.copy(isUpdating = true) }
        scope.launch {
            val count = PluginManager.updateAll()
            _state.update { it.copy(isUpdating = false) }
            notify(if (count == 0) "All extensions are up to date" else "Updated $count extensions")
        }
    }
}

@Composable
fun ExtensionsScreen(viewModel: ExtensionsViewModel) {
    val state by viewModel.state.collectAsState()
    val open = state.openRepository
    when {
        state.showSettings -> ExtensionSettingsScreen(onBack = { viewModel.showSettings(false) })
        open == null -> RepositoryList(viewModel, state)
        else -> RepositoryDetails(viewModel, state, open)
    }
}

@Composable
private fun RepositoryList(viewModel: ExtensionsViewModel, state: ExtensionsState) {
    val repositories by AppData.repositories.collectAsState()
    val plugins by AppData.plugins.collectAsState()
    val errors by PluginManager.errors.collectAsState()
    var input by remember { mutableStateOf("") }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Extensions", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { viewModel.showSettings(true) }) { Text("Extension settings") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = viewModel::updateAll, enabled = !state.isUpdating && plugins.isNotEmpty()) {
                    Icon(painterResource(Res.drawable.update), null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (state.isUpdating) "Updating..." else "Update all")
                }
            }
            Text(
                "${plugins.size} installed · ${PluginManager.providers().size} providers loaded",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("Repository URL or shortcode") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).onEnter { viewModel.addRepository(input).also { input = "" } },
                )
                Spacer(Modifier.width(12.dp))
                Button(
                    onClick = {
                        viewModel.addRepository(input)
                        input = ""
                    },
                    enabled = !state.isAddingRepository && input.isNotBlank(),
                ) { Text(if (state.isAddingRepository) "Adding..." else "Add repository") }
            }
            Spacer(Modifier.height(16.dp))
        }

        if (errors.isNotEmpty()) {
            item {
                Column(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(10.dp)).padding(12.dp)
                ) {
                    Text("Some extensions failed to load", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                    errors.forEach { Text("${it.name}: ${it.message}", color = MaterialTheme.colorScheme.onErrorContainer) }
                }
                Spacer(Modifier.height(16.dp))
            }
        }

        if (repositories.isEmpty()) {
            item {
                MessageBox(
                    "No repositories added",
                    "Paste a repository link above. Repositories are published by the community, CloudStream does not provide any.",
                    Modifier.fillMaxWidth(),
                )
            }
        }

        items(repositories, key = { it.url }) { repo ->
            val count = plugins.count { it.repositoryUrl == repo.url }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { viewModel.open(repo) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RemoteImage(repo.iconUrl, Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(repo.name, style = MaterialTheme.typography.titleMedium)
                    Text(repo.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text("$count installed", color = MaterialTheme.colorScheme.onSurfaceVariant)
                IconButton(onClick = { viewModel.removeRepository(repo) }) {
                    Icon(painterResource(Res.drawable.delete), "Remove repository")
                }
            }
        }
    }
}

@Composable
private fun RepositoryDetails(viewModel: ExtensionsViewModel, state: ExtensionsState, repo: RepositoryEntry) {
    val plugins by AppData.plugins.collectAsState()
    val data = state.repositoryPlugins[repo.url] ?: RepositoryPlugins()
    var query by remember { mutableStateOf("") }
    var language by remember { mutableStateOf<String?>(null) }
    val languages = remember(data.plugins) { data.plugins.mapNotNull { it.language }.distinct().sorted() }
    val shown = data.plugins.filter {
        (language == null || it.language == language) &&
            (query.isBlank() || it.name.contains(query, ignoreCase = true) || it.description?.contains(query, true) == true)
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { viewModel.open(null) }) { Icon(painterResource(Res.drawable.arrow_back), "Back") }
            Column(Modifier.weight(1f)) {
                Text(repo.name, style = MaterialTheme.typography.headlineSmall)
                data.repository?.description?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            OutlinedButton(onClick = { viewModel.installAll(repo) }, enabled = !data.isLoading) { Text("Install all") }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(query, { query = it }, label = { Text("Filter") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (languages.size > 1) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(vertical = 8.dp),
            ) {
                item { FilterChip(language == null, { language = null }, label = { Text("All") }) }
                items(languages) { lang -> FilterChip(language == lang, { language = lang }, label = { Text(lang) }) }
            }
        }

        when {
            data.isLoading -> LoadingBox("Loading extensions")
            data.error != null -> MessageBox(data.error, action = "Retry" to { viewModel.refresh(repo) })
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(shown, key = { it.internalName }) { plugin ->
                    val installed = plugins.firstOrNull {
                        it.filePath == PluginManager.pluginFile(repo.url, plugin.internalName).absolutePath
                    }
                    PluginRow(
                        plugin = plugin,
                        installedVersion = installed?.version,
                        isBusy = plugin.internalName in state.busy,
                        onInstall = { viewModel.install(repo, plugin) },
                        onUninstall = { viewModel.uninstall(repo, plugin) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PluginRow(
    plugin: RemotePlugin,
    installedVersion: Int?,
    isBusy: Boolean,
    onInstall: () -> Unit,
    onUninstall: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RemoteImage(plugin.icon(), Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(plugin.name, style = MaterialTheme.typography.titleMedium)
                Text("v${plugin.version}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                plugin.language?.let { Pill(it) }
                when (plugin.status) {
                    0 -> Pill("Down")
                    2 -> Pill("Slow")
                    3 -> Pill("Beta")
                }
                if (plugin.jarUrl == null) Pill("Android build")
            }
            plugin.description?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            val details = listOfNotNull(
                plugin.authors.takeIf { it.isNotEmpty() }?.joinToString(prefix = "By "),
                plugin.tvTypes?.takeIf { it.isNotEmpty() }?.joinToString(),
            )
            if (details.isNotEmpty()) {
                Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Box(Modifier.width(120.dp), contentAlignment = Alignment.CenterEnd) {
            when {
                isBusy -> CircularProgressIndicator(Modifier.size(24.dp))
                installedVersion == null -> Button(onClick = onInstall, enabled = plugin.status != 0) { Text("Install") }
                installedVersion < plugin.version -> Button(onClick = onInstall) { Text("Update") }
                else -> OutlinedButton(onClick = onUninstall) { Text("Remove") }
            }
        }
    }
}
