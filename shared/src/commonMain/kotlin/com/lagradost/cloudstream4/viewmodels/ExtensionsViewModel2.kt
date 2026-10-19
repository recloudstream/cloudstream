package com.lagradost.cloudstream4.viewmodels

import androidx.annotation.CheckResult
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.InternalAPI
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.utils.Levenshtein
import com.lagradost.cloudstream4.state.ActionHandler
import com.lagradost.cloudstream4.state.DefaultEffectContainer
import com.lagradost.cloudstream4.state.DefaultStateContainer
import com.lagradost.cloudstream4.state.DerivedState
import com.lagradost.cloudstream4.state.EffectContainer
import com.lagradost.cloudstream4.state.MultiActiveQuery
import com.lagradost.cloudstream4.state.SearchableData
import com.lagradost.cloudstream4.state.SearchableDataState
import com.lagradost.cloudstream4.state.SingleActiveQuery
import com.lagradost.cloudstream4.state.SortByName
import com.lagradost.cloudstream4.state.StateContainer
import com.lagradost.cloudstream4.state.UniqueItem
import com.lagradost.cloudstream4.viewmodels.ExtensionsEffect.RepositoryAdded
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.uuid.Uuid

@Immutable
data class RepositoryInfo(
    val iconUrl: String?,
    val name: String,
    val url: String,
    val description: String? = null,
)

@Immutable
data class Repository(
    val info: RepositoryInfo,
    val status: RepositoryStatus,
    override val uuid: Uuid
) : UniqueItem

@Immutable
sealed class RepositoryStatus {
    object Loading : RepositoryStatus()
    object Success : RepositoryStatus()
    data class Failure(val cause: Throwable) : RepositoryStatus()
}

@Immutable
data class Plugin(
    val info: PluginInfo,
    val source: PluginDownloadSource?,
    val status: PluginStatus,
    val downloadLocation: String?,
    val repository: Uuid,
    override val uuid: Uuid = Uuid.random()
) : UniqueItem

@Immutable
data class PluginInfo(
    val name: String,
    val internalName: String,
    val version: Int,
    val tvTypes: PersistentList<TvType>,
    val authors: PersistentList<String>,
    val status: Int,
    val iconUrl: String?,
    val language: String?,
    val description: String?,
    /**
     * This is different from source.repositoryUrl,
     * as this may be a different url than the repo we got this from */
    val repositoryUrl: String?,
)

@Immutable
sealed class PluginStatus {
    object Idle : PluginStatus()
    data class Downloading(val progress: Long, val total: Long?) : PluginStatus()
    data class Failed(val cause: Throwable) : PluginStatus()
}

@Immutable
data class PluginDownloadSource(
    val size: Long?,
    val internalName: String,
    val pluginUrl: String,
    val repositoryUrl: String,
    val digest: DigestPair?,
)

@Immutable
data class PluginSortBy(
    val query: String,
    val ignoreCase: Boolean = true,
) : Comparator<Plugin> {
    override fun compare(p0: Plugin?, p1: Plugin?): Int {
        if (p0 == p1) return 0
        if (p0 == null) return -1
        if (p1 == null) return 1
        return score(p1).compareTo(score(p0))
    }

    fun score(plugin: Plugin): Int {
        val score1 = Levenshtein.partialRatio(
            plugin.info.name.lowercase(),
            query
        )
        if (score1 > 80) {
            return score1
        }
        val score2 = plugin.info.description?.take(64)?.lowercase()
            ?.let { description ->
                Levenshtein.partialRatio(
                    description,
                    query
                )
            } ?: 0
        return maxOf(score1, score2)
    }
}

@Immutable
data class PluginFilterByQuery(
    val query: String,
    private val ratio: Int = 80,
) : Function1<Plugin, Boolean> {
    override fun invoke(value: Plugin): Boolean {
        if (query.isEmpty()) return true
        val score1 = Levenshtein.partialRatio(
            value.info.name.lowercase(),
            query
        )

        if (score1 > ratio) return true
        val score2 = value.info.description?.take(64)?.lowercase()
            ?.let { description ->
                Levenshtein.partialRatio(
                    description,
                    query
                )
            }

        return score2 != null && score2 > ratio
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        val other = other as? PluginFilterByQuery ?: return false
        if (query != other.query) return false
        if (ratio != other.ratio) return false
        return true
    }

    override fun hashCode(): Int {
        var result = ratio
        result = 31 * result + query.hashCode()
        return result
    }

    @CheckResult
    fun update(newQuery: String): PluginFilterByQuery {
        val lowerCaseQuery = newQuery.lowercase()
        return if (lowerCaseQuery == query) {
            this
        } else {
            copy(query = lowerCaseQuery)
        }
    }
}

@Immutable
data class FilterPlugin(
    val tvTypes: PersistentSet<TvType> = TvType.entries.toSet().toPersistentSet(),
    val repository: Uuid? = null,
    val query: PluginFilterByQuery = PluginFilterByQuery(query = ""),
    /** We filter by the inverse, as that makes is easy to initalize it as all = {} */
    val removeLanguages: PersistentSet<String> = persistentSetOf(),
) : Function1<Plugin, Boolean> {
    override fun invoke(plugin: Plugin): Boolean =
        /** Filter by repository */
        (repository == null || (plugin.repository == repository)) &&
                /** Filter by type */
                (if (plugin.info.tvTypes.isEmpty()) {
                    /**
                     * This is a strange edge-case, but if no tv-types are recognized they
                     * automatically should be treated as "Other" to avoid always filtering them
                     */
                    tvTypes.contains(TvType.Others)
                } else {
                    plugin.info.tvTypes.any(tvTypes::contains)
                }) &&
                /** Filter by language */
                !(plugin.info.language?.let(removeLanguages::contains)
                    ?: removeLanguages.contains("")) &&
                /** Filter by query */
                query(plugin)
}

@Immutable
data class DerivedRepositoryState(
    /**
     * This is ***readonly***!
     *
     * TvType.ordinal -> Int, we use an IntArray to prevent boxing and such.
     *
     * We do not win very much having a persistantList/persitantMap here even if it is a bit more
     * "correct".
     * */
    private val countOfTypes: IntArray = IntArray(TvType.entries.size),
    val downloadedAndEnabled: Int = 0,
    val downloadedAndDisabled: Int = 0,
    val notDownloaded: Int = 0,
    /**
     * Map of strictly positive values corresponding with the count of each language
     * where null/blank -> ""
     *
     * This is for the language filter, to only showcase relevant languages
     * */
    val languageCount: PersistentMap<String, Int> = persistentMapOf()
) : DerivedState<DerivedRepositoryState, Plugin> {
    val total get() = downloadedAndDisabled + downloadedAndEnabled + notDownloaded

    fun containsType(type: TvType): Boolean = countOfTypes[type.ordinal] > 0

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DerivedRepositoryState) return false
        if (downloadedAndEnabled != other.downloadedAndEnabled) return false
        if (downloadedAndDisabled != other.downloadedAndDisabled) return false
        if (notDownloaded != other.notDownloaded) return false
        if (!countOfTypes.contentEquals(other.countOfTypes)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = downloadedAndEnabled
        result = 31 * result + downloadedAndDisabled
        result = 31 * result + notDownloaded
        result = 31 * result + countOfTypes.contentHashCode()
        return result
    }

    override fun clearing(): DerivedRepositoryState = DerivedRepositoryState()

    override fun adding(value: Plugin): DerivedRepositoryState {
        val isDownloaded = value.downloadLocation != null
        val isDisabled = value.info.status == 0
        val isEnabled = !isDisabled
        val isNotDownloaded = !isDownloaded

        val newCountOfTypes = countOfTypes.copyOf()
        for (type in value.info.tvTypes) {
            newCountOfTypes[type.ordinal] = newCountOfTypes[type.ordinal] + 1
        }
        if (value.info.tvTypes.isEmpty()) {
            newCountOfTypes[TvType.Others.ordinal] += 1
        }
        val language = if (value.info.language.isNullOrBlank()) "" else value.info.language
        val count = languageCount[language]?.plus(1) ?: 1
        val newLanguageCount = languageCount.putting(language, count)
        return copy(
            languageCount = newLanguageCount,
            downloadedAndEnabled = downloadedAndEnabled + if (isDownloaded && isEnabled) 1 else 0,
            downloadedAndDisabled = downloadedAndDisabled + if (isDownloaded && isDisabled) 1 else 0,
            notDownloaded = notDownloaded + if (isNotDownloaded) 1 else 0,
            countOfTypes = newCountOfTypes
        )
    }

    override fun removing(value: Plugin): DerivedRepositoryState {
        val isDownloaded = value.downloadLocation != null
        val isDisabled = value.info.status == 0
        val isEnabled = !isDisabled
        val isNotDownloaded = !isDownloaded

        val newCountOfTypes = countOfTypes.copyOf()
        for (type in value.info.tvTypes) {
            newCountOfTypes[type.ordinal] = newCountOfTypes[type.ordinal] + 1
        }
        val language = if (value.info.language.isNullOrBlank()) "" else value.info.language
        val count = languageCount[language]?.minus(1) ?: 0
        val newLanguageCount = if (count <= 0) {
            languageCount.removing(language)
        } else {
            languageCount.putting(language, count)
        }
        return copy(
            languageCount = newLanguageCount,
            downloadedAndEnabled = downloadedAndEnabled - if (isDownloaded && isEnabled) 1 else 0,
            downloadedAndDisabled = downloadedAndDisabled - if (isDownloaded && isDisabled) 1 else 0,
            notDownloaded = notDownloaded - if (isNotDownloaded) 1 else 0,
            countOfTypes = newCountOfTypes
        )
    }
}

@Immutable
data class DerivedPluginsState(
    val sumOfRepositories: DerivedRepositoryState = DerivedRepositoryState(),
    val repositories: PersistentMap<Uuid, DerivedRepositoryState> = persistentMapOf(),
) : DerivedState<DerivedPluginsState, Plugin> {
    /** This is ***readonly***! Do not mutate */
    val downloadedAndEnabled: Int get() = sumOfRepositories.downloadedAndEnabled
    val downloadedAndDisabled: Int get() = sumOfRepositories.downloadedAndDisabled
    val notDownloaded: Int get() = sumOfRepositories.notDownloaded

    companion object {
        val zero = DerivedRepositoryState()
    }

    override fun clearing(): DerivedPluginsState = DerivedPluginsState()

    override fun adding(value: Plugin): DerivedPluginsState {
        val current = repositories[value.repository] ?: zero
        val newRepository = current.adding(value)

        return copy(
            sumOfRepositories = sumOfRepositories.adding(value),
            repositories = repositories.putting(value.repository, newRepository)
        )
    }

    override fun removing(value: Plugin): DerivedPluginsState {
        val current = repositories[value.repository] ?: zero
        val newRepository = current.removing(value)

        return copy(
            sumOfRepositories = sumOfRepositories.removing(value),
            repositories = repositories.putting(value.repository, newRepository)
        )
    }
}

@Immutable
data class ExtensionsState(
    val repositories: SearchableData<Uuid, Repository> = SearchableData.from(
        data = persistentMapOf(),
        sortedBy = SortByName { it.info.name }
    ),
    val plugins: SearchableDataState<Uuid, Plugin, DerivedPluginsState> = SearchableDataState.from(
        data = persistentMapOf(),
        sortedBy = SortByName { it.info.name },
        state = DerivedPluginsState()
    ),
    val pluginFilter: FilterPlugin = FilterPlugin(),
    //val repoFilter: FilterByQuery<Repository> = FilterByQuery(query = "") { it.info.name },
    val addDialog: AddRepositoryDialog? = null,
    val searchPlugins: Boolean = false,
    val updateAllPluginsDialog: PluginsUpdatingDialog? = null,
) {
    val derivedRepositoryState: DerivedRepositoryState =
        pluginFilter.repository?.let(plugins.state.repositories::get)
            ?: plugins.state.sumOfRepositories
}

@Immutable
data class PluginsUpdatingDialog(
    val total: Int,
    val downloaded: Int,
    val failed: Int,
) {
    val progress get() = downloaded + failed
}

@Immutable
data class AddRepositoryDialog(
    val url: String = "",
    val loading: Boolean = false,
    val error: Throwable? = null,
)

sealed class ExtensionsEffect {
    data class RepositoryAdded(val repository: RepositoryInfo, val uuid: Uuid) : ExtensionsEffect()
}

@InternalAPI
interface RepositoryManager {
    fun getRepositories(): List<RepositoryInfo>

    @Throws
    suspend fun getPlugins(
        repository: RepositoryInfo,
        repositoryUuid: Uuid
    ): Pair<RepositoryInfo, List<Plugin>>

    fun getLocalPlugins(localUuid: Uuid): List<Plugin>

    @Throws
    suspend fun deletePlugin(location: String)

    @Throws
    suspend fun deleteRepository(repository: RepositoryInfo)

    @Throws
    suspend fun downloadPlugin(
        source: PluginDownloadSource,
        downloadProgress: (Long, Long?) -> Unit,
    ): String

    suspend fun updateRepositoryInfo(repository: RepositoryInfo)
}

@Immutable
sealed class ExtensionsAction {
    object Init : ExtensionsAction()
    data class DeleteRepository(val uuid: Uuid) : ExtensionsAction()
    data class DeletePluginsInRepository(val uuid: Uuid) : ExtensionsAction()
    data class DeletePlugin(val uuid: Uuid) : ExtensionsAction()
    data class DownloadPlugin(val uuid: Uuid) : ExtensionsAction()

    //data class DownloadRepository(val repository: RepositoryInfo) : ExtensionsAction()
    data class DownloadRepositoryWithDialog(val url: String) : ExtensionsAction()
    data class SelectRepository(val uuid: Uuid?) : ExtensionsAction()
    data class SelectPlugin(val uuid: Uuid) : ExtensionsAction()
    data class ReloadRepository(val uuid: Uuid) : ExtensionsAction()
    data class Search(val query: String) : ExtensionsAction()
    data class FilterByTypes(val types: PersistentSet<TvType>) : ExtensionsAction()
    data class FilterByInverseLanguages(val removeLanguages: PersistentSet<String>) :
        ExtensionsAction()

    data class SetRepositoryDialog(val dialog: AddRepositoryDialog?) : ExtensionsAction()
    object ClearPluginSearch : ExtensionsAction()
    object UpdateAllPlugins : ExtensionsAction()
    object CancelPluginsUpdate : ExtensionsAction()
    data class CancelPluginDownloader(val uuid: Uuid) : ExtensionsAction()
}

/**
 * Here viewmodel acts as a view state holder that keeps everything up to date,
 * and RepositoryManager is invoked to handle the platform specific features
 *
 * UI -(onAction)-> Viewmodel -(invokes)-> RepositoryManager
 *   <---(state)---/        <--(callback)--/
 * */
class ExtensionsViewModel2(
    private val manager: RepositoryManager,
    private val localUuid: Uuid = Uuid.random(),
) : ViewModel(), StateContainer<ExtensionsState> by DefaultStateContainer(ExtensionsState()),
    EffectContainer<ExtensionsEffect> by DefaultEffectContainer(),
    ActionHandler<ExtensionsAction> {
    private val localRepository = Repository(
        info = RepositoryInfo(
            iconUrl = null,
            name = "Local plugins",
            url = "https://github.com/recloudstream/cloudstream",
            description = "Locally installed plugins"
        ),
        status = RepositoryStatus.Success,
        uuid = localUuid
    )
    private val repositoryDispatcher = MultiActiveQuery<Uuid>(dispatcher = Dispatchers.IO)
    private val downloadDispatcher = MultiActiveQuery<Uuid>(dispatcher = Dispatchers.IO)
    private val addRepositoryDispatcher = SingleActiveQuery(dispatcher = Dispatchers.IO)
    private val updatePluginsDispatcher = SingleActiveQuery(dispatcher = Dispatchers.IO)

    @OptIn(ExperimentalAtomicApi::class)
    override fun onAction(action: ExtensionsAction) {
        when (action) {
            ExtensionsAction.UpdateAllPlugins -> {
                viewModelScope.launch {
                    updateAllPlugins()
                }
            }

            ExtensionsAction.ClearPluginSearch -> {
                updateState {
                    val newPluginFilter = pluginFilter.copy(repository = null)
                    copy(
                        searchPlugins = false,
                        pluginFilter = newPluginFilter,
                        plugins = plugins.filteringBy(newPluginFilter),
                    )
                }
            }

            ExtensionsAction.Init -> {
                viewModelScope.launch {
                    init()
                }
            }

            ExtensionsAction.CancelPluginsUpdate -> {
                viewModelScope.launch {
                    updatePluginsDispatcher.launch {
                        updateState {
                            copy(updateAllPluginsDialog = null)
                        }
                    }
                }
            }

            is ExtensionsAction.CancelPluginDownloader -> {
                viewModelScope.launch {
                    downloadDispatcher.cancelOne(action.uuid)
                }
            }

            is ExtensionsAction.ReloadRepository -> {
                val uuid = action.uuid
                val current = state.value.repositories[uuid] ?: return
                viewModelScope.launch {
                    updateState {
                        copy(repositories = repositories.updating(uuid) { copy(status = RepositoryStatus.Loading) })
                    }
                    loadRepository(uuid = uuid, repository = current.info)
                }
            }

            is ExtensionsAction.SetRepositoryDialog -> {
                updateState {
                    copy(addDialog = action.dialog)
                }
            }

            is ExtensionsAction.DeletePluginsInRepository -> {
                viewModelScope.launch {
                    val allPlugins = state.value.plugins
                    for (plugin in allPlugins.data.values) {
                        if (plugin.repository == action.uuid) {
                            deletePlugin(plugin)
                        }
                    }
                }
            }

            is ExtensionsAction.DeleteRepository -> {
                val repository = state.value.repositories[action.uuid] ?: return
                viewModelScope.launch {
                    deleteRepository(repository)
                }
            }

            is ExtensionsAction.SelectRepository -> {
                updateState {
                    val newPluginFilter = pluginFilter.copy(repository = action.uuid)
                    copy(
                        plugins = plugins.filteringBy(newPluginFilter),
                        pluginFilter = newPluginFilter,
                        searchPlugins = true,
                    )
                }
            }

            is ExtensionsAction.SelectPlugin -> {
                updateState {
                    val plugin = plugins[action.uuid] ?: return@updateState this
                    val newPluginFilter = pluginFilter.copy(repository = plugin.repository)
                    copy(
                        plugins = plugins.filteringBy(newPluginFilter),
                        pluginFilter = newPluginFilter,
                        searchPlugins = true,
                    )
                }
            }

            is ExtensionsAction.FilterByInverseLanguages -> {
                updateState {
                    val newPluginFilter =
                        pluginFilter.copy(removeLanguages = action.removeLanguages)
                    copy(
                        plugins = plugins.filteringBy(newPluginFilter),
                        pluginFilter = newPluginFilter
                    )
                }
            }

            is ExtensionsAction.Search -> {
                updateState {
                    val newPluginFilter =
                        pluginFilter.copy(query = pluginFilter.query.update(action.query))
                    val activeQuery = action.query.isNotEmpty()
                    copy(
                        plugins = plugins.searchBy(
                            newSorting = if (activeQuery) {
                                PluginSortBy(query = action.query.lowercase())
                            } else {
                                SortByName { it.info.name }
                            },
                            newFilter = newPluginFilter
                        ),
                        pluginFilter = newPluginFilter,
                        searchPlugins = pluginFilter.repository != null || activeQuery,
                    )
                }
            }

            is ExtensionsAction.DeletePlugin -> {
                val plugin = state.value.plugins[action.uuid] ?: return
                viewModelScope.launch {
                    deletePlugin(plugin)
                }
            }

            is ExtensionsAction.DownloadPlugin -> {
                val plugin = state.value.plugins[action.uuid] ?: return
                viewModelScope.launch {
                    downloadDispatcher.launch(plugin.uuid) {
                        downloadPlugin(plugin)
                    }
                }
            }

            is ExtensionsAction.DownloadRepositoryWithDialog -> {
                viewModelScope.launch {
                    downloadRepositoryWithDialog(action)
                }
            }

            is ExtensionsAction.FilterByTypes -> {
                updateState {
                    val newPluginFilter = pluginFilter.copy(tvTypes = action.types)
                    copy(
                        plugins = plugins.filteringBy(newPluginFilter),
                        pluginFilter = newPluginFilter
                    )
                }
            }
        }
    }

    private suspend fun onRepositoryAdded(
        uuid: Uuid,
        newRepositoryInfo: RepositoryInfo,
        pluginsFetched: List<Plugin>
    ) {
        manager.updateRepositoryInfo(newRepositoryInfo)
        updateState {
            /**
             * The unique id of a plugin is the repository + source,
             * if source does not exist because it is an internal plugin,
             * then use the download location
             * */
            fun uniqueId(plugin: Plugin) =
                plugin.repository to (plugin.source?.pluginUrl ?: plugin.downloadLocation)

            val oldPlugins = plugins.data.values.associate { plugin ->
                uniqueId(plugin) to plugin.uuid
            }

            /** If we refresh plugins that already exist by assigning them the old uuid */
            val newPlugins = pluginsFetched.associate { plugin ->
                val newUuid = oldPlugins[uniqueId(plugin)]
                    ?: plugin.uuid
                newUuid to if (newUuid != plugin.uuid) {
                    plugin.copy(uuid = newUuid)
                } else {
                    plugin
                }
            }

            copy(
                plugins = plugins.setTo(plugins.data.puttingAll(newPlugins)),
                repositories = repositories.adding(
                    uuid, Repository(
                        status = RepositoryStatus.Success,
                        info = newRepositoryInfo,
                        uuid = uuid
                    )
                )
            )
        }
    }

    private suspend fun deleteRepository(repository: Repository) {
        /** Cancel repositoryDispatcher loading to prevent race */
        repositoryDispatcher.launch(repository.uuid) {
            updateState {
                copy(repositories = repositories.updating(repository.uuid) {
                    copy(status = RepositoryStatus.Loading)
                })
            }
            runCatching { manager.deleteRepository(repository.info) }.onSuccess {
                /** -> Remove all visual state of this plugin */
                updateState {
                    copy(
                        repositories = repositories.removing(repository.uuid),
                        plugins = plugins.setTo(plugins.data.filter { it.value.repository != repository.uuid }
                            .toPersistentMap())
                    )
                }
            }.onFailure { e ->
                /** -> Unable to remove, so we just show the error */
                updateState {
                    copy(repositories = repositories.updating(repository.uuid) {
                        copy(status = RepositoryStatus.Failure(e))
                    })
                }
            }
        }
    }

    private suspend fun deletePlugin(plugin: Plugin) {
        val downloadLocation = plugin.downloadLocation ?: return
        downloadDispatcher.launch(plugin.uuid) {
            runCatching { manager.deletePlugin(downloadLocation) }.onFailure { t ->
                updateState {
                    copy(plugins = plugins.updating(plugin.uuid) {
                        copy(status = PluginStatus.Failed(t))
                    })
                }
            }.onSuccess {
                updateState {
                    copy(plugins = plugins.updating(plugin.uuid) {
                        copy(status = PluginStatus.Idle, downloadLocation = null)
                    })
                }
            }
        }
    }

    private suspend fun downloadRepositoryWithDialog(action: ExtensionsAction.DownloadRepositoryWithDialog) {
        val trimmedUrl = action.url.trim()
        val uuid = Uuid.random()

        val found =
            state.value.repositories.data.values.firstOrNull { it.info.url == trimmedUrl }
        if (found != null) {
            postEffect { RepositoryAdded(found.info, found.uuid) }
            updateState {
                copy(addDialog = null)
            }
            return
        }
        updateState {
            copy(
                addDialog = AddRepositoryDialog(
                    /** Not a bug, the dialog should not get trimmed */
                    url = action.url,
                    loading = true,
                    error = null
                )
            )
        }
        val repository = RepositoryInfo(
            iconUrl = null,
            name = "Pending",
            url = trimmedUrl,
            description = null
        )
        addRepositoryDispatcher.launch {
            runCatching {
                manager.getPlugins(
                    repository,
                    uuid
                )
            }.onFailure { t ->
                /** Cancellation means that we restarted this, so the caller updates the status */
                if (!isActive) {
                    return@launch
                }
                updateState {
                    copy(
                        addDialog = AddRepositoryDialog(
                            /** Not a bug, the dialog should not get trimmed */
                            url = action.url,
                            loading = false,
                            error = t
                        )
                    )
                }
            }.onSuccess { (newRepositoryInfo, pluginsFetched) ->
                val found =
                    state.value.repositories.data.values.firstOrNull { it.info.url == newRepositoryInfo.url }
                if (found != null) {
                    // Shortcodes can have a different url from the input
                    updateState {
                        copy(addDialog = null)
                    }
                    postEffect {
                        RepositoryAdded(
                            found.info,
                            found.uuid
                        )
                    }
                    return@launch
                }
                onRepositoryAdded(
                    uuid = uuid,
                    newRepositoryInfo = newRepositoryInfo,
                    pluginsFetched = pluginsFetched
                )
                updateState {
                    copy(addDialog = null)
                }
                postEffect { RepositoryAdded(newRepositoryInfo, uuid) }
            }
        }
    }

    private suspend fun updateAllPlugins() = updatePluginsDispatcher.launch {
        val plugins = state.value.plugins.data.values.filter { it.downloadLocation != null }
        val updateMutex = Mutex()
        var failed = 0
        var downloaded = 0
        val total = plugins.size

        updateState {
            copy(
                updateAllPluginsDialog = PluginsUpdatingDialog(
                    total = total,
                    failed = 0,
                    downloaded = 0
                )
            )
        }

        for (plugin in plugins) {
            downloadDispatcher.launch(plugin.uuid) {
                val isSuccess = downloadPlugin(plugin)
                if (!isActive) {
                    return@launch
                }

                updateMutex.withLock {
                    if (isSuccess) {
                        downloaded += 1
                    } else {
                        failed += 1
                    }
                    updateState {
                        copy(
                            updateAllPluginsDialog = PluginsUpdatingDialog(
                                total = total,
                                failed = failed,
                                downloaded = downloaded
                            )
                        )
                    }
                }
            }
        }

        if (!isActive) {
            return@launch
        }

        updateMutex.withLock {
            updateState {
                copy(
                    updateAllPluginsDialog = PluginsUpdatingDialog(
                        total = total,
                        failed = failed,
                        downloaded = downloaded
                    )
                )
            }
        }
    }

    private suspend fun downloadPlugin(plugin: Plugin): Boolean {
        val source = plugin.source ?: return false
        runCatching {
            manager.downloadPlugin(
                source = source,
                downloadProgress = { downloadedBytes, totalBytes ->
                    updateState {
                        copy(plugins = plugins.updating(plugin.uuid) {
                            copy(
                                status = PluginStatus.Downloading(
                                    total = totalBytes,
                                    progress = downloadedBytes
                                )
                            )
                        })
                    }
                }
            )
        }.onFailure { t ->
            updateState {
                copy(plugins = plugins.updating(plugin.uuid) {
                    copy(status = PluginStatus.Failed(t))
                })
            }
            return false
        }.onSuccess { downloadLocation ->
            updateState {
                copy(plugins = plugins.updating(plugin.uuid) {
                    copy(status = PluginStatus.Idle, downloadLocation = downloadLocation)
                })
            }
            return true
        }
        return false
    }

    private suspend fun loadRepository(uuid: Uuid, repository: RepositoryInfo) {
        repositoryDispatcher.launch(uuid) {
            runCatching {
                manager.getPlugins(
                    repository,
                    uuid
                )
            }.onFailure { t ->
                /** Cancellation means that we restarted this, so the caller updates the status */
                if (!isActive) {
                    return@launch
                }
                updateState {
                    copy(repositories = repositories.updating(uuid) {
                        copy(status = RepositoryStatus.Failure(t))
                    })
                }
            }.onSuccess { (newRepositoryInfo, pluginsFetched) ->
                onRepositoryAdded(
                    uuid = uuid,
                    newRepositoryInfo = newRepositoryInfo,
                    pluginsFetched = pluginsFetched
                )
            }
        }
    }

    private suspend fun init() {
        val repos = manager.getRepositories()

        updateState {
            val oldRepositories = repositories.data.values.associate { repository ->
                repository.info.url to repository.uuid
            }

            val newRepositories = repos.associate { info ->
                val uuid = oldRepositories[info.url] ?: Uuid.random()
                uuid to Repository(info = info, status = RepositoryStatus.Loading, uuid = uuid)
            }.toPersistentMap()

            copy(
                repositories = repositories.setTo(
                    newData = repositories.data.puttingAll(
                        newRepositories
                    )
                )
            )
        }

        repositoryDispatcher.launch(localUuid) {
            val pluginsFetched = manager.getLocalPlugins(localUuid)
            updateState {
                val oldPlugins = plugins.data.values.associate { it.downloadLocation to it.uuid }

                /** Reuse the uuid if possible, to avoid duplicates and avoid clearing UI */
                val newPluginsFetched = pluginsFetched.associate { plugin ->
                    require(plugin.downloadLocation != null) {
                        "All local plugins must have a download path"
                    }

                    val newUuid = oldPlugins[plugin.downloadLocation] ?: Uuid.random()
                    newUuid to if (newUuid != plugin.uuid) {
                        plugin.copy(uuid = newUuid)
                    } else {
                        plugin
                    }
                }

                /**
                 * Remove ALL the old ones instead of putting as something might have been removed
                 * (this is *very* *very* unlikely, but more correct
                 * */
                val newPlugins = plugins.data.mutate { edit ->
                    for ((uuid, plugin) in plugins.data) {
                        if (plugin.repository == localUuid) {
                            edit.remove(uuid)
                        }
                    }
                    edit.putAll(newPluginsFetched)
                }

                copy(
                    plugins =
                        if (newPlugins != plugins) {
                            plugins.setTo(newPlugins)
                        } else {
                            plugins
                        },
                    repositories =
                        /** Only show local plugins if those are actually used */
                        if (newPluginsFetched.isEmpty()) {
                            repositories.removing(localUuid)
                        } else {
                            repositories.adding(localUuid, localRepository)
                        }
                )
            }
        }

        val repositories = state.value.repositories.data
        for ((uuid, repository) in repositories) {
            loadRepository(uuid, repository.info)
        }
    }
}