package com.lagradost.cloudstream4.viewmodels

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream4.state.ActionHandler
import com.lagradost.cloudstream4.state.DefaultEffectContainer
import com.lagradost.cloudstream4.state.DefaultStateContainer
import com.lagradost.cloudstream4.state.DerivedState
import com.lagradost.cloudstream4.state.EffectContainer
import com.lagradost.cloudstream4.state.FilterByQuery
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
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
data class FilterPlugin(
    val tvTypes: PersistentSet<TvType> = TvType.entries.toSet().toPersistentSet(),
    val repository: Uuid? = null,
    val query: FilterByQuery<Plugin> = FilterByQuery(query = "") { it.info.name },
) : Function1<Plugin, Boolean> {
    override fun invoke(plugin: Plugin): Boolean =
        /** Filter by repository */
        (repository == null || (plugin.repository == repository)) &&
                /** Filter by query */
                query(plugin) &&
                /** Filter by type */
                (if (plugin.info.tvTypes.isEmpty()) {
                    /**
                     * This is a strange edge-case, but if no tv-types are recognized they
                     * automatically should be treated as "Other" to avoid always filtering them
                     */
                    tvTypes.contains(TvType.Others)
                } else {
                    plugin.info.tvTypes.any(tvTypes::contains)
                })
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
) : DerivedState<DerivedRepositoryState, Plugin> {
    fun containsType(type : TvType) : Boolean = countOfTypes[type.ordinal] > 0

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
        return copy(
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
        return copy(
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
    val searchPlugins: Boolean = false
)

@Immutable
data class AddRepositoryDialog(
    val url: String = "",
    val loading: Boolean = false,
    val error: Throwable? = null,
)

sealed class ExtensionsEffect {
    data class RepositoryAdded(val repository: RepositoryInfo, val uuid: Uuid) : ExtensionsEffect()
}

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
    data class DeletePlugin(val uuid: Uuid) : ExtensionsAction()
    data class DownloadPlugin(val uuid: Uuid) : ExtensionsAction()

    //data class DownloadRepository(val repository: RepositoryInfo) : ExtensionsAction()
    data class DownloadRepositoryWithDialog(val url: String) : ExtensionsAction()
    data class SelectRepository(val uuid: Uuid?) : ExtensionsAction()
    data class Search(val query: String) : ExtensionsAction()
    data class FilterByTypes(val types: PersistentSet<TvType>) : ExtensionsAction()
    data class SetRepositoryDialog(val dialog: AddRepositoryDialog?) : ExtensionsAction()
    object ClearPluginSearch : ExtensionsAction()
}

class ExtensionsViewModel2(
    private val manager: RepositoryManager,
    private val localUuid: Uuid = Uuid.random(),
) : ViewModel(), StateContainer<ExtensionsState> by DefaultStateContainer(ExtensionsState()),
    EffectContainer<ExtensionsEffect> by DefaultEffectContainer(),
    ActionHandler<ExtensionsAction> {
    private val repositoryDispatcher = MultiActiveQuery<Uuid>(dispatcher = Dispatchers.IO)
    private val downloadDispatcher = MultiActiveQuery<Uuid>(dispatcher = Dispatchers.IO)
    private val addRepositoryDispatcher = SingleActiveQuery(dispatcher = Dispatchers.IO)

    override fun onAction(action: ExtensionsAction) {
        when (action) {
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

            is ExtensionsAction.SetRepositoryDialog -> {
                updateState {
                    copy(addDialog = action.dialog)
                }
            }

            is ExtensionsAction.DeleteRepository -> {
                val repository = state.value.repositories.data[action.uuid] ?: return
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

            is ExtensionsAction.Search -> {
                updateState {
                    val newPluginFilter =
                        pluginFilter.copy(query = pluginFilter.query.update(action.query))
                    //val newRepoFilter = repoFilter.update(action.query)
                    copy(
                        plugins = plugins.filteringBy(newPluginFilter),
                        //repositories = repositories.filteringBy(newRepoFilter),
                        pluginFilter = newPluginFilter,
                        searchPlugins = pluginFilter.repository != null || action.query.isNotEmpty(),
                        //repoFilter = newRepoFilter,
                    )
                }
            }

            is ExtensionsAction.DeletePlugin -> {
                val plugin = state.value.plugins.data[action.uuid] ?: return
                viewModelScope.launch {
                    deletePlugin(plugin)
                }
            }

            is ExtensionsAction.DownloadPlugin -> {
                val plugin = state.value.plugins.data[action.uuid] ?: return
                viewModelScope.launch {
                    downloadPlugin(plugin)
                }
            }

            is ExtensionsAction.DownloadRepositoryWithDialog -> {
                val uuid = Uuid.random()
                val url = action.url.trim()

                viewModelScope.launch {
                    val found =
                        state.value.repositories.data.values.firstOrNull { it.info.url == url }
                    if (found != null) {
                        postEffect { RepositoryAdded(found.info, found.uuid) }
                        updateState {
                            copy(addDialog = null)
                        }
                        return@launch
                    }
                    updateState {
                        copy(
                            addDialog = AddRepositoryDialog(
                                url = action.url,
                                loading = true,
                                error = null
                            )
                        )
                    }
                    val repository = RepositoryInfo(
                        iconUrl = null,
                        name = "Pending",
                        url = url,
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
            }

            /*is ExtensionsAction.DownloadRepository -> {
                viewModelScope.launch {
                    if (state.value.repositories.data.any { it.value.info.url == action.repository.url }) {
                        return@launch
                    }
                    val uuid = Uuid.random()
                    downloadRepository(uuid = uuid, repository = action.repository)
                }
            }*/
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

    /*private suspend fun downloadRepository(
        uuid: Uuid,
        repository: RepositoryInfo
    ) {
        updateState {
            copy(
                repositories = repositories.adding(
                    uuid,
                    Repository(
                        info = repository,
                        status = RepositoryStatus.Loading,
                        uuid = uuid
                    )
                )
            )
        }
        loadRepository(uuid, repository)
    }*/

    private suspend fun onRepositoryAdded(
        uuid: Uuid,
        newRepositoryInfo: RepositoryInfo,
        pluginsFetched: List<Plugin>
    ) {
        manager.updateRepositoryInfo(newRepositoryInfo)
        val newPlugins = pluginsFetched.associateBy { it.uuid }
        updateState {
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

    private suspend fun cancelPlugin(uuid: Uuid) {
        downloadDispatcher.cancelOne(uuid)
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

    private suspend fun downloadPlugin(plugin: Plugin) {
        val source = plugin.source ?: return
        downloadDispatcher.launch(plugin.uuid) {
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
            }.onSuccess { downloadLocation ->
                updateState {
                    copy(plugins = plugins.updating(plugin.uuid) {
                        copy(status = PluginStatus.Idle, downloadLocation = downloadLocation)
                    })
                }
            }
        }
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
        val reposMap = repos.associate { info ->
            val uuid = Uuid.random()
            uuid to Repository(info = info, status = RepositoryStatus.Loading, uuid = uuid)
        }.toPersistentMap()

        updateState {
            copy(repositories = repositories.setTo(newData = reposMap))
        }

        repositoryDispatcher.launch(localUuid) {
            val pluginsFetched = manager.getLocalPlugins(localUuid)
            val newPlugins = pluginsFetched.associateBy { it.uuid }
            updateState {
                copy(plugins = plugins.setTo(plugins.data.puttingAll(newPlugins)))
            }
        }

        for ((uuid, repository) in reposMap) {
            loadRepository(uuid, repository.info)
        }
    }
}