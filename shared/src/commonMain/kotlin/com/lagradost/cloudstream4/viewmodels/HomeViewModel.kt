package com.lagradost.cloudstream4.viewmodels

import androidx.annotation.WorkerThread
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.APIHolder.unixTimeMS
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageData
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.cmap
import com.lagradost.cloudstream4.Database
import com.lagradost.cloudstream4.compose.ImmutableHomePageList
import com.lagradost.cloudstream4.compose.ImmutableSearchResponse
import com.lagradost.cloudstream4.compose.SearchResponseAction
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
import com.lagradost.cloudstream4.state.sortByInt
import com.lagradost.cloudstream4.state.sortByLong
import com.lagradost.cloudstream4.state.sortByUuid
import com.lagradost.cloudstream4.viewmodels.HomeAction.*
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.WeakHashMap
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid


@Immutable
data class ImmutableHero(
    val isLoading: Boolean = false,
    val hasLoaded: Boolean = false,
    val error: Throwable? = null,
    val response: ImmutableSearchResponse,
    override val uuid: Uuid = Uuid.random()
) : UniqueItem

@Immutable
data class HomeState(
    val activeApi: String? = null,
    val isLoading: Boolean = true,
    val error: Throwable? = null,
    /** Watch internal id -> Map(Item id -> response) */
    val bookmarks: PersistentMap<Int, SearchableData<Int, ImmutableSearchResponse>> = persistentMapOf(),
    /** Item id -> item */
    val continueWatching: SearchableData<Int, ImmutableSearchResponse> = SearchableData.from(
        data = persistentMapOf(),
        sortedBy = sortByLong { -(it.updateTime ?: 0L) },
        filteredBy = null
    ),
    /**
     * Hero uuid -> hero, this uuid is transient and not related to the response
     * so we use it for randomization
     * */
    val hero: SearchableData<Uuid, ImmutableHero> = SearchableData.from(
        data = persistentMapOf(),
        /** Sorted by here automatically randomize the items **/
        sortedBy = sortByUuid { it.uuid },
        filteredBy = null
    ),
    val rows: SearchableData<String, ImmutableHomePageList> = SearchableData.from(
        data = persistentMapOf(),
        sortedBy = sortByInt { it.order },
        filteredBy = { it.isEnabled }
    ),
    /**
     * What rows we have requested, that way we can enable/disable or re-request
     * if we change settings
     * */
    val rowsRequested : PersistentSet<String> = persistentSetOf(),
    val rowDialog: String? = null,
    val selectAPIDialog: SelectAPIDialog? = null,
)

object SortByPin : Comparator<ImmutableAPI> {
    private val sortByName: SortByName<ImmutableAPI> = SortByName { it.name }
    override fun compare(
        p0: ImmutableAPI?,
        p1: ImmutableAPI?
    ): Int {
        if (p0 == p1) return 0
        if (p0 == null) return -1
        if (p1 == null) return 1

        return if (p0.isPinned == p1.isPinned) {
            sortByName.compare(p0, p1)
        } else if (p0.isPinned) {
            -1
        } else {
            1
        }
    }
}

@Immutable
data class DerivedMainAPIState(
    private val countOfTypes: IntArray = IntArray(TvType.entries.size),
) : DerivedState<DerivedMainAPIState, ImmutableAPI> {
    fun containsType(type: TvType): Boolean = countOfTypes[type.ordinal] > 0

    companion object {
        val zero = DerivedMainAPIState()
    }

    override fun adding(value: ImmutableAPI): DerivedMainAPIState {
        val newCountOfTypes = countOfTypes.copyOf()
        for (type in value.supportedTypes) {
            newCountOfTypes[type.ordinal] += 1
        }
        return copy(countOfTypes = newCountOfTypes)
    }

    override fun clearing(): DerivedMainAPIState = zero

    override fun removing(value: ImmutableAPI): DerivedMainAPIState {
        val newCountOfTypes = countOfTypes.copyOf()
        for (type in value.supportedTypes) {
            newCountOfTypes[type.ordinal] -= 1
        }
        return copy(countOfTypes = newCountOfTypes)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as DerivedMainAPIState
        return countOfTypes.contentEquals(other.countOfTypes)
    }

    override fun hashCode(): Int {
        return countOfTypes.contentHashCode()
    }
}

@Immutable
data class FilterAPI(
    val tvTypes: PersistentSet<TvType> = TvType.entries.toSet().toPersistentSet(),
) : Function1<ImmutableAPI, Boolean> {
    override fun invoke(api: ImmutableAPI): Boolean =
        /** Filter by type */
        (if (api.supportedTypes.isEmpty()) {
            /**
             * This is a strange edge-case, but if no tv-types are recognized they
             * automatically should be treated as "Other" to avoid always filtering them
             */
            tvTypes.contains(TvType.Others)
        } else {
            api.supportedTypes.any(tvTypes::contains)
        })
}

@Immutable
data class SelectAPIDialog(
    val data: SearchableDataState<Uuid, ImmutableAPI, DerivedMainAPIState>,
    val filter: FilterAPI,
)

@Immutable
sealed class HomeAction {
    data class ResultAction(val data: SearchResponseAction) : HomeAction()
    data class SelectApi(val apiName: String?) : HomeAction()
    data class RemovePin(val name: String) : HomeAction()
    data class AddPin(val name: String) : HomeAction()
    data class Expand(val rowName: String) : HomeAction()
    data class SelectHero(val uuid: Uuid) : HomeAction()
    data class ShowRowDialog(val rowName: String) : HomeAction()
    object DismissDialog : HomeAction()
    object Reload : HomeAction()
    object LoadResumeWatching : HomeAction()
    object LoadBookmarks : HomeAction()
    object OpenApiDialog : HomeAction()
    data class FilterByTypes(val types: PersistentSet<TvType>) : HomeAction()
}

sealed class HomeEffect {
    data class Error(val error: Throwable) : HomeEffect()
}

data class APIRepository(val api: MainAPI) {
    companion object {
        // 2 minute timeout to prevent bad extensions/extractors from hogging the resources
        // No real provider should take longer, so we hard kill them.
        private const val DEFAULT_TIMEOUT_MS = 120_000L
        private const val MAX_TIMEOUT_MS = 4 * DEFAULT_TIMEOUT_MS
        private const val MIN_TIMEOUT_MS = 5_000L

        private fun getTimeout(desiredMs: Long?): Long {
            return (desiredMs ?: DEFAULT_TIMEOUT_MS).coerceIn(
                MIN_TIMEOUT_MS,
                MAX_TIMEOUT_MS
            )
        }

        suspend fun <T> request(
            requestedTimeoutMs: Long?,
            @WorkerThread work: suspend CoroutineScope.() -> T
        ): Result<T> =
            runCatching {
                withTimeout(getTimeout(requestedTimeoutMs).milliseconds, work)
            }
    }

    @WorkerThread
    @Throws
    private suspend fun getMainPage(page: Int, data: MainPageData): HomePageResponse? =
        api.getMainPage(
            page,
            MainPageRequest(data.name, data.data, data.horizontalImages)
        )


    @WorkerThread
    suspend fun load(url: String): Result<ImmutableSearchResponse> = request(api.loadTimeoutMs) {
        /** TODO Cache! */
        ImmutableSearchResponse.from(api.load(url) ?: throw ErrorLoadingException("No data"))
    }

    @WorkerThread
    suspend fun loadHomepage(): Result<List<HomePageResponse?>> =
        request(api.getMainPageTimeoutMs) {
            val delta = api.lastHomepageRequest - unixTimeMS
            api.lastHomepageRequest = unixTimeMS
            if (delta > 0) {
                delay(delta.milliseconds)
            }

            val pages = api.mainPage

            if (api.sequentialMainPage) {
                return@request pages.mapIndexed { index, data ->
                    if (index > 0) {
                        delay(api.sequentialMainPageDelay.milliseconds)
                    }
                    getMainPage(1, data)
                }
            }

            return@request pages.cmap { data ->
                getMainPage(1, data)
            }
        }

    @WorkerThread
    suspend fun loadHomepage(
        page: Int,
        name: String
    ): Result<List<HomePageResponse?>> = request(api.getMainPageTimeoutMs) {
        val delta = api.lastHomepageRequest - unixTimeMS
        api.lastHomepageRequest = unixTimeMS
        if (delta > 0) {
            delay(delta.milliseconds)
        }

        val pages = api.mainPage
        val requestedData = pages.firstOrNull { it.name == name }

        if (requestedData != null) {
            return@request listOf(getMainPage(page, requestedData))
        }

        if (api.sequentialMainPage) {
            return@request pages.mapIndexed { index, data ->
                if (index > 0) {
                    delay(api.sequentialMainPageDelay.milliseconds)
                }
                getMainPage(page, data)
            }
        }

        return@request pages.cmap { data ->
            getMainPage(page, data)
        }
    }
}

fun <K, T> PersistentMap<K, T>.edit(key: K, builder: T.() -> T): PersistentMap<K, T> {
    val value = this[key] ?: return this
    return putting(key, builder(value))
}

data class ImmutableAPI(
    val name: String,
    val sourcePlugin: String? = null,
    val language: String,
    val supportedTypes: PersistentSet<TvType>,
    val isPinned: Boolean,
    override val uuid: Uuid = Uuid.random()
) : UniqueItem

interface HomeRepository {
    /**
     * The reason why we have this function in the repository is because
     * the api might not have been loaded yet, so this function acts as a delay
     * */
    suspend fun getApi(name: String): MainAPI?
    suspend fun getAllResumeWatching(): List<ImmutableSearchResponse>
    suspend fun getAllBookmarks(): Map<Int, List<ImmutableSearchResponse>>

    companion object {
        private val uuidMap = WeakHashMap<MainAPI, Uuid>()

        /**
         * Just makes the uuid a bit more stable for e.g. ui stuff.
         *
         * Synchronized as we want weak references (no concurrent weak hashmap exists),
         * but also no crashes as there is no guarantee on calling thread,
         * and we are quite sure that there is no hard contention here.
         * */
        @Synchronized
        private fun getSameUuid(api: MainAPI): Uuid {
            return uuidMap.getOrPut(api) { Uuid.random() }
        }
    }

    fun getAllApis(database: Database): List<ImmutableAPI> {
        val pinnedSet = database.general.pinnedProviders.get()
        return APIHolder.apis.withLock {
            APIHolder.apis.map { api ->
                ImmutableAPI(
                    uuid = getSameUuid(api),
                    name = api.name,
                    language = api.lang,
                    sourcePlugin = api.sourcePlugin,
                    supportedTypes = api.supportedTypes.toPersistentSet(),
                    isPinned = pinnedSet.contains(api.name)
                )
            }
        }
    }
}

class HomeViewModel(val repository: HomeRepository, val db: Database) :
    ViewModel(),
    StateContainer<HomeState> by DefaultStateContainer(HomeState()),
    EffectContainer<HomeEffect> by DefaultEffectContainer(), ActionHandler<HomeAction> {
    private val homepageQuery = SingleActiveQuery(Dispatchers.IO)
    private val heroDispatcher = MultiActiveQuery<Uuid>(Dispatchers.IO)
    private val bookmarkDispatcher = SingleActiveQuery(Dispatchers.IO)
    private val resumeDispatcher = SingleActiveQuery(Dispatchers.IO)

    override fun onAction(action: HomeAction) {
        when (action) {
            LoadBookmarks -> {
                viewModelScope.launch(Dispatchers.IO) {
                    loadBookmarks()
                }
            }

            LoadResumeWatching -> {
                viewModelScope.launch(Dispatchers.IO) {
                    loadResumeWatching()
                }
            }

            is Expand -> {
                // It must both exist and not be loading
                val currentState = state.value.rows[action.rowName]
                if (currentState == null || currentState.isLoading) {
                    return
                }
                viewModelScope.launch(Dispatchers.IO) {
                    expandRow(action.rowName)
                }
            }

            is SelectApi -> {
                /** Invalid api name -> None */
                if (action.apiName.isNullOrBlank() || action.apiName.uppercase() == "NONE") {
                    db.account.home.selectedApi.set(action.apiName ?: "None")
                    viewModelScope.launch(Dispatchers.IO) {
                        loadNonePage()
                    }
                    return
                }
                db.account.home.selectedApi.set(action.apiName)
                viewModelScope.launch(Dispatchers.IO) {
                    loadHomepage(action.apiName)
                }
            }

            is ShowRowDialog -> {
                updateState {
                    copy(rowDialog = action.rowName)
                }
            }

            is ResultAction -> {

            }

            is SelectHero -> {
                /** Sanity check */
                if (!state.value.hero.data.containsKey(action.uuid)) {
                    return
                }
                viewModelScope.launch(Dispatchers.IO) {
                    loadHero(action.uuid)
                }
            }

            DismissDialog -> {
                updateState { copy(rowDialog = null, selectAPIDialog = null) }
            }

            Reload -> {
                onAction(SelectApi(state.value.activeApi))
            }

            OpenApiDialog -> {
                val apis = repository.getAllApis(db)
                val filter = FilterAPI(tvTypes = db.account.home.selectedTvTypes.get())
                val data = SearchableDataState.from(
                    state = DerivedMainAPIState.zero,
                    data = apis.associateBy { it.uuid }.toPersistentMap(),
                    sortedBy = SortByPin,
                    filteredBy = filter,
                )
                updateState {
                    copy(selectAPIDialog = SelectAPIDialog(data = data, filter = filter))
                }
            }

            is FilterByTypes -> {
                db.account.home.selectedTvTypes.set(action.types)
                updateState {
                    if (selectAPIDialog == null) {
                        this
                    } else {
                        val newFilter = selectAPIDialog.filter.copy(tvTypes = action.types)
                        copy(
                            selectAPIDialog = selectAPIDialog.copy(
                                data = selectAPIDialog.data.filteringBy(newFilter),
                                filter = newFilter
                            )
                        )
                    }
                }
            }

            is AddPin -> {
                updatePin {
                    adding(action.name)
                }
            }

            is RemovePin -> {
                updatePin {
                    removing(action.name)
                }
            }
        }
    }

    private fun updatePin(updater: PersistentSet<String>.() -> PersistentSet<String>) {
        val pinnedSet = updater(db.general.pinnedProviders.get())
        db.general.pinnedProviders.set(pinnedSet)
        updateState {
            copy(
                selectAPIDialog = selectAPIDialog?.copy(
                    data = selectAPIDialog.data.setTo(
                        newData = selectAPIDialog.data.data.mapValues { (_, value) ->
                            value.copy(isPinned = pinnedSet.contains(value.name))
                        }.toPersistentMap()
                    )
                )
            )
        }
    }

    init {
        /** When we select an account, update everything */
        viewModelScope.launch(Dispatchers.IO) {
            db.general.accountIndex.changes().distinctUntilChanged()
                .collect {
                    onAction(SelectApi(db.account.home.selectedApi.get()))
                    onAction(LoadBookmarks)
                    onAction(LoadResumeWatching)
                }
        }
    }

    private suspend fun loadHero(uuid: Uuid) = heroDispatcher.launchWithoutCancel(uuid) {
        val currentHero = state.value.hero[uuid] ?: return@launchWithoutCancel
        if (currentHero.hasLoaded) {
            return@launchWithoutCancel
        }
        val api = repository.getApi(currentHero.response.apiName)?.let(::APIRepository)
            ?: return@launchWithoutCancel
        updateState {
            copy(hero = hero.updating(uuid) {
                copy(isLoading = true)
            })
        }
        api.load(currentHero.response.url).onFailure { error ->
            updateState {
                copy(hero = hero.updating(uuid) {
                    copy(error = error, isLoading = false)
                })
            }
        }.onSuccess { response ->
            updateState {
                copy(hero = hero.updating(uuid) {
                    copy(error = null, hasLoaded = true, isLoading = false, response = response)
                })
            }
        }
    }

    private suspend fun loadNonePage() = homepageQuery.launch {
        heroDispatcher.cancelAll()
        updateState {
            copy(
                activeApi = null,
                isLoading = false,
                error = null,
                rows = rows.clearing(),
                hero = hero.clearing(),
            )
        }
    }

    private suspend fun loadHomepage(apiName: String) = homepageQuery.launch {
        updateState {
            copy(
                activeApi = apiName,
                isLoading = true,
                error = null,
                rows = rows.setTo(persistentMapOf())
            )
        }

        /**
         * We can cancel all hero dispatches,
         * as those are irrelevant to us now
         * */
        heroDispatcher.cancelAll()

        val api =
            repository.getApi(apiName)?.let(::APIRepository) ?: return@launch showNoApi(apiName)

        api.loadHomepage().onFailure { error ->
            // If this was canceled then do nothing
            if (!currentCoroutineContext().isActive) {
                return@launch
            }
            updateState {
                copy(error = error, isLoading = false)
            }
        }.onSuccess { items ->
            // For the first page we actually define how they should be rendered
            // As expansion should *never* add rows after the first load
            val orderedResponses =
                items.asSequence().filterNotNull().flatMapIndexed { index, response ->
                    response.items.map { item ->
                        item.name to ImmutableHomePageList(
                            name = item.name,
                            page = 1,
                            hasNext = response.hasNext,
                            isHorizontalImages = item.isHorizontalImages,
                            isLoading = false,
                            items = item.list.map(ImmutableSearchResponse::from).toPersistentList(),
                            order = index
                        )
                    }
                }.toMap().toPersistentMap() // This keeps the order but de-duplicates the keys

            val items = orderedResponses.values
                .flatMap { responseList -> responseList.items }
                /**
                 * Some rows may include the item twice,
                 * so we dedup by url as url should be unique to a single item
                 * */
                .distinctBy { response -> response.url }
                .map { response -> ImmutableHero(response = response) }
                .associateBy { hero -> hero.uuid }
                .toPersistentMap()

            updateState {
                copy(
                    isLoading = false,
                    error = null,
                    rows = rows.setTo(orderedResponses),
                    hero = hero.setTo(items)
                )
            }
        }
    }

    private suspend fun loadBookmarks() = bookmarkDispatcher.launch {
        val results = repository.getAllBookmarks()
        val newBookmarks = results.entries.associate { entry ->
            entry.key to SearchableData.from(
                data = entry.value.associateBy { item -> item.id!! }.toPersistentMap(),
                sortedBy = sortByLong { it.updateTime!! },
                filteredBy = null
            )
        }.toPersistentMap()
        if (!isActive) return@launch
        updateState {
            copy(bookmarks = newBookmarks)
        }
    }

    private suspend fun loadResumeWatching() = resumeDispatcher.launch {
        val results = repository.getAllResumeWatching()
        if (!isActive) return@launch
        val newResumeWatching = results.associateBy { it.id!! }.toPersistentMap()
        updateState { copy(continueWatching = continueWatching.setTo(newResumeWatching)) }
    }

    private suspend fun expandRow(name: String) = homepageQuery.launch {
        val state = this@HomeViewModel.state.value
        val apiName = state.activeApi ?: return@launch
        val activeList = state.rows[name] ?: return@launch
        if (!activeList.hasNext) return@launch
        updateState {
            copy(rows = rows.updating(name) {
                copy(isLoading = true)
            })
        }
        val api =
            repository.getApi(apiName)?.let(::APIRepository) ?: return@launch showNoApi(apiName)

        val nextPage = activeList.page + 1

        api.loadHomepage(nextPage, name).onFailure { error ->
            // If this was canceled then do nothing
            if (!currentCoroutineContext().isActive) {
                return@launch
            }
            postEffect {
                HomeEffect.Error(error)
            }
            updateState {
                if (activeApi != apiName) return@updateState this // Sanity check

                copy(rows = rows.updating(name) {
                    copy(isLoading = false, hasNext = false)
                })
            }
        }.onSuccess { response ->
            // If page > 1 then we only append to the end
            updateState {
                if (activeApi != apiName) return@updateState this // Sanity check
                copy(isLoading = false, error = null, rows = rows.setTo(rows.data.mutate { edit ->
                    var containsTheRequest = false
                    response.forEach { list ->
                        list?.items?.forEach { item ->
                            containsTheRequest = containsTheRequest || item.name == name

                            // Give a warning here? This is inside a updateState so it is a bad idea to push an effect :/
                            val current = edit[item.name] ?: return@forEach

                            // Give a warning if (page != current.page + 1) ? This is caused by bad extension code
                            edit[item.name] = current.copy(
                                page = nextPage,
                                hasNext = list.hasNext,
                                isLoading = false,
                                // The old behavior did distinct here, the new behavior just adds it here
                                // as it makes it more consistent for extension devs, they should not rely on this behavior
                                //
                                // This ImmutableSearchResponse::from can be moved out of the updateState, but it should not be an issue
                                items = current.items.addingAll(
                                    item.list.map(
                                        ImmutableSearchResponse::from
                                    )
                                )
                            )
                        }
                    }

                    // Just in case the item does not contain it, mark it as a failure to prevent more requests
                    if (!containsTheRequest) {
                        edit[name]?.let { current ->
                            edit[name] = current.copy(hasNext = false, isLoading = false)
                        }
                    }
                }))
            }
        }
    }

    fun showNoApi(apiName: String) = updateState {
        copy(
            activeApi = apiName,
            isLoading = false,
            error = ErrorLoadingException("API $apiName not loaded")
        )
    }
}