package com.lagradost.cloudstream3.ui.home

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.APIHolder.getApiFromNameNull
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getKey
import com.lagradost.cloudstream3.CloudStreamApp.Companion.setKey
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.metaproviders.TmdbProvider
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.plugins.PluginManager
import com.lagradost.cloudstream3.ui.WatchType
import com.lagradost.cloudstream3.ui.quicksearch.QuickSearchFragment
import com.lagradost.cloudstream3.ui.settings.SettingsFragmentScreen.AccountPin
import com.lagradost.cloudstream3.ui.settings.SettingsProvidersScreen.toStringRes
import com.lagradost.cloudstream3.ui.settings.extensions.ExtensionsScreen.contentTypes
import com.lagradost.cloudstream3.utils.AppContextUtils.getShortSeasonText
import com.lagradost.cloudstream3.utils.AppContextUtils.loadResult
import com.lagradost.cloudstream3.utils.DOWNLOAD_HEADER_CACHE
import com.lagradost.cloudstream3.utils.DOWNLOAD_HEADER_CACHE_BACKUP
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.DataStoreHelper.getAllResumeStateIds
import com.lagradost.cloudstream3.utils.DataStoreHelper.getLastWatched
import com.lagradost.cloudstream3.utils.DataStoreHelper.getViewPos
import com.lagradost.cloudstream3.utils.UIHelper.navigate
import com.lagradost.cloudstream3.utils.downloader.DownloadObjects
import com.lagradost.cloudstream4.compose.FocusButton
import com.lagradost.cloudstream4.compose.FocusIconButton
import com.lagradost.cloudstream4.compose.HORIZONTAL_ASPECT_RATIO
import com.lagradost.cloudstream4.compose.ImmutableHomePageList
import com.lagradost.cloudstream4.compose.ImmutableSearchResponse
import com.lagradost.cloudstream4.compose.ImmutableWatchEpisode
import com.lagradost.cloudstream4.compose.ImmutableWatchPosition
import com.lagradost.cloudstream4.compose.LeftAlignedFocusFollower
import com.lagradost.cloudstream4.compose.LoadingLine
import com.lagradost.cloudstream4.compose.RoundedShape
import com.lagradost.cloudstream4.compose.Screen
import com.lagradost.cloudstream4.compose.SearchResponseAction
import com.lagradost.cloudstream4.compose.SearchResponseItem
import com.lagradost.cloudstream4.compose.VERTICAL_ASPECT_RATIO
import com.lagradost.cloudstream4.compose.WhiteFilterChip
import com.lagradost.cloudstream4.compose.fadedOutline
import com.lagradost.cloudstream4.compose.loading
import com.lagradost.cloudstream4.compose.rounded
import com.lagradost.cloudstream4.compose.whiteOutline
import com.lagradost.cloudstream4.generated.resources.Res
import com.lagradost.cloudstream4.generated.resources.no_results_found
import com.lagradost.cloudstream4.rememberAccountData
import com.lagradost.cloudstream4.rememberDatabase
import com.lagradost.cloudstream4.state.SearchableData
import com.lagradost.cloudstream4.state.SearchableDataState
import com.lagradost.cloudstream4.state.sortByInt
import com.lagradost.cloudstream4.state.sortByUuid
import com.lagradost.cloudstream4.theme.CloudStreamPreviewTheme
import com.lagradost.cloudstream4.viewmodels.DerivedMainAPIState
import com.lagradost.cloudstream4.viewmodels.FilterAPI
import com.lagradost.cloudstream4.viewmodels.HomeAction
import com.lagradost.cloudstream4.viewmodels.HomeRepository
import com.lagradost.cloudstream4.viewmodels.HomeState
import com.lagradost.cloudstream4.viewmodels.HomeViewModel
import com.lagradost.cloudstream4.viewmodels.ImmutableAPI
import com.lagradost.cloudstream4.viewmodels.ImmutableHero
import com.lagradost.cloudstream4.viewmodels.SelectAPIDialog
import com.lagradost.cloudstream4.viewmodels.SortByPin
import com.mihon.material.padding
import com.mihon.presentation.secondaryItemAlpha
import com.mihon.presentation.settings.collectAsState
import com.mihon.presentation.settings.widget.SwitchPreferenceWidget
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid

object WaitForPluginHomeRepository : HomeRepository {
    private val waitForLoad = Channel<Unit>(
        capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    init {
        MainActivity.afterPluginsLoadedEvent += {
            waitForLoad.trySend(Unit)
        }
    }

    override suspend fun getApi(name: String): MainAPI? {
        val api = getApiFromNameNull(name)
        if (api != null) {
            return api
        }

        /** Use the waitForLoad channel as a condition variable, to check when loadedOnlinePlugins */
        while (!PluginManager.loadedOnlinePlugins && !PluginManager.isSafeMode()) {
            waitForLoad.receive()
        }

        return getApiFromNameNull(name)
    }

    override suspend fun getAllResumeWatching(): List<ImmutableSearchResponse> =
        withContext(Dispatchers.IO) {
            val ids = getAllResumeStateIds() ?: return@withContext arrayListOf()
            return@withContext ids.mapNotNull { id ->
                val resume = getLastWatched(id) ?: return@mapNotNull null

                val headerCache = getKey<DownloadObjects.DownloadHeaderCached>(
                    DOWNLOAD_HEADER_CACHE, resume.parentId.toString()
                )

                val data = if (headerCache == null) {
                    // We store resume watching data in download header cache
                    // Because downloads automatically pruned outdated download headers we
                    // removed resume watching data. We should restore the data for affected users.
                    val oldData = getKey<DownloadObjects.DownloadHeaderCached>(
                        DOWNLOAD_HEADER_CACHE_BACKUP, resume.parentId.toString()
                    ) ?: return@mapNotNull null

                    // Restore data
                    setKey(DOWNLOAD_HEADER_CACHE, resume.parentId.toString(), oldData)
                    oldData
                } else {
                    headerCache
                }

                val watchPos = getViewPos(resume.episodeId)

                ImmutableSearchResponse(
                    name = data.name,
                    apiName = data.apiName,
                    type = data.type,
                    url = data.url,
                    id = id,
                    posterUrl = data.poster,
                    updateTime = data.cacheTime,
                    watchPosition = watchPos?.let { posDur ->
                        ImmutableWatchPosition(
                            position = posDur.position.milliseconds,
                            duration = posDur.duration.milliseconds,
                        )
                    },
                    watchEpisode = ImmutableWatchEpisode(
                        episode = resume.episode,
                        season = resume.season,
                        parentId = resume.parentId,
                        episodeId = resume.episodeId,
                    )
                )
            }
        }

    override suspend fun getAllBookmarks(): Map<Int, List<ImmutableSearchResponse>> =
        withContext(Dispatchers.IO) {
            val output = mutableMapOf<Int, ArrayList<ImmutableSearchResponse>>()
            val keys = DataStoreHelper.getAllWatchStateIds() ?: return@withContext output

            for (id in keys) {
                val watchState = DataStoreHelper.getResultWatchState(id)
                if (watchState == WatchType.NONE) {
                    continue
                }
                val response = DataStoreHelper.getBookmarkedData(id) ?: continue
                val list = output.getOrPut(watchState.internalId) { ArrayList() }
                list.add(
                    ImmutableSearchResponse(
                        name = response.name,
                        url = response.url,
                        apiName = response.apiName,
                        type = response.type,
                        posterUrl = response.posterUrl,
                        posterHeaders = response.posterHeaders?.toPersistentMap()
                            ?: persistentMapOf(),
                        id = id,
                        quality = response.quality,
                        score = response.score,
                        updateTime = response.latestUpdatedTime
                    )
                )
            }

            return@withContext output
        }
}

@Composable
@PreviewLightDark
fun Preview() {
    CloudStreamPreviewTheme {
        Surface {
            /*HomeScreen.TvHeroRow(
                row = SearchableData.from(
                    data = persistentMapOf(Uuid.random() to ImmutableHero(response = ImmutableSearchResponse.preview)),
                    sortedBy = sortByUuid { it.uuid }), onAction = {})*/
            HomeScreen.Content(
                state = HomeState(
                    selectAPIDialog = SelectAPIDialog(
                        data = SearchableDataState.from(
                            state = DerivedMainAPIState.zero,
                            data = listOf(
                                ImmutableAPI(
                                    name = "movie api",
                                    language = "en",
                                    supportedTypes = persistentSetOf(TvType.Movie),
                                    isPinned = true
                                ), ImmutableAPI(
                                    name = "series api",
                                    language = "en",
                                    supportedTypes = persistentSetOf(TvType.TvSeries),
                                    isPinned = false
                                )
                            ).associateBy { it.uuid }.toPersistentMap(),
                            sortedBy = SortByPin,
                            filteredBy = null,
                        ), filter = FilterAPI(TvType.entries.toPersistentSet())
                    ),
                    isLoading = false,
                    bookmarks = persistentMapOf(
                        1 to SearchableData.from(
                            data = persistentMapOf(1 to ImmutableSearchResponse.previewMovie),
                            sortedBy = sortByUuid { it.uuid })
                    ),
                    hero = SearchableData.from(
                        sortedBy = sortByUuid { it.uuid }, data = persistentMapOf(
                            ImmutableSearchResponse.previewMovie.uuid to ImmutableHero(
                                isLoading = false,
                                hasLoaded = true,
                                response = ImmutableSearchResponse.previewMovie
                            )
                        )
                    ),
                    rows = SearchableData.from(
                        sortedBy = sortByInt { it.order }, data = persistentMapOf(
                            "Row 1" to ImmutableHomePageList(
                                order = 0,
                                name = "Row 1",
                                page = 1,
                                hasNext = false,
                                isLoading = false,
                                isHorizontalImages = true,
                                items = persistentListOf(
                                    ImmutableSearchResponse.previewMovie,
                                    ImmutableSearchResponse.previewTvSeries
                                )
                            )
                        )
                    ),
                    continueWatching = SearchableData.from(
                        sortedBy = sortByInt { it.id!! }, data = persistentMapOf(
                            0 to ImmutableSearchResponse.previewMovie,
                            1 to ImmutableSearchResponse.previewTvSeries
                        )
                    ),
                ), onAction = {})
        }
    }
}

object HomeScreen : Screen {
    val VERTICAL_POSTER_WIDTH = 120.dp
    val HORIZONTAL_POSTER_WIDTH = 240.dp
    val FOCUS_EXTRA_WIDTH = 20.dp

    /*// TODO actually fix
    private val reloadCount = MutableStateFlow(0)

    init {
        MainActivity.afterPluginsLoadedEvent += { reload ->
            if (reload) {
                reloadCount.update { value -> value + 1 }
            }
        }
    }

    @Composable
    fun HandleReload(onAction: (HomeAction) -> Unit) {
        val reloaded by reloadCount.collectAsState()
        LaunchedEffect(reloaded) {
            if (reloaded > 0) {
                onAction(HomeAction.Reload)
            }
        }
    }*/

    object TestPlugin : BasePlugin() {
        val api = object : TmdbProvider() {
            override var name = "TMDB"
            override val useMetaLoadResponse = true
        }

        override fun load() {
            registerMainAPI(api)
        }
    }

    @Composable
    override fun Content() {
        val db = rememberDatabase()
        val homeViewModel = viewModel {
            HomeViewModel(
                repository = WaitForPluginHomeRepository, db = db
            )
        }
        //HandleReload(homeViewModel::onAction)
        val state by homeViewModel.state.collectAsState()

        LaunchedEffect(Unit) {
            TestPlugin.load()
        }

        Content(state = state, onAction = homeViewModel::onAction)
    }

    @Composable
    fun Content(state: HomeState, onAction: (HomeAction) -> Unit) {
        Scaffold { contentPadding ->
            state.selectAPIDialog?.let { dialog ->
                SelectAPIDialog(
                    dialog = dialog, selectedAPIName = state.activeApi, onAction = onAction
                )
            }

            val settings = rememberAccountData()

            LazyColumn(contentPadding = contentPadding) {
                item("header") {
                    TvHeader(state.activeApi, onAction = onAction)
                }
                item("hero") {
                    val heroEnabled by settings.home.heroEnabled.collectAsState()
                    if (heroEnabled) {
                        if (state.isLoading) {
                            TvHeroLoading()
                        } else {
                            TvHeroRow(row = state.hero, onAction = onAction)
                        }
                    }
                }
                if (!state.isLoading) {
                    item("continue watching") {
                        val continueWatchingEnabled by settings.home.continueWatchingEnabled.collectAsState()
                        if (state.continueWatching.sorted.isNotEmpty() && continueWatchingEnabled) {
                            TvResumeRow(row = state.continueWatching, onAction = onAction)
                        }
                    }
                    item("bookmarks") {
                        val bookmarksEnabled by settings.home.bookmarksEnabled.collectAsState()
                        if (bookmarksEnabled) {
                            Text("TODO bookmarks")
                        }
                    }
                    items(state.rows.sorted, key = { name -> name }) { name ->
                        val row = state.rows[name]!!
                        TvSearchRow(row = row, onAction = onAction)
                    }
                } else {
                    items(10) {
                        TvLoadingRow()
                    }
                }
            }
        }
    }

    @Composable
    fun TvHeroLoading() {
        Column(
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            modifier = Modifier.padding(MaterialTheme.padding.medium)
        ) {
            Box(
                modifier = Modifier
                    .height(200.dp)
                    .fillMaxWidth()
                    .loading()
                    .fadedOutline()
            )
            LoadingLine(0.7f)
        }
    }

    @Composable
    fun TvHeroRow(row: SearchableData<Uuid, ImmutableHero>, onAction: (HomeAction) -> Unit) {
        if (row.sorted.isEmpty()) {
            return
        }

        val searchAction = remember<(SearchResponseAction) -> Unit>(onAction) {
            { item ->
                onAction(HomeAction.ResultAction(item))
            }
        }

        val state = rememberPagerState(initialPage = 0) { row.sorted.size }

        /** Preload prev and next */
        LaunchedEffect(state.currentPage) {
            for (index in state.currentPage - 1..state.currentPage + 1) {
                val uuid = row.sorted.getOrNull(index) ?: continue
                onAction(HomeAction.SelectHero(uuid))
            }
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
            modifier = Modifier.padding(MaterialTheme.padding.medium)
        ) {
            var hasFocus by remember { mutableStateOf(false) }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .rounded()
                    .onFocusChanged { newFocus ->
                        hasFocus = newFocus.hasFocus
                    }
                    .whiteOutline(
                        hasFocus = hasFocus, shape = RoundedShape()
                    )
                    .fadedOutline()) {
                HorizontalPager(
                    state = state,
                    modifier = Modifier.semantics { role = Role.Carousel },
                    // itemSpacing = MaterialTheme.padding.medium
                ) { page ->
                    val uuid = row.sorted[page]
                    val hero = row[uuid]!!
                    val interactionSource = remember { MutableInteractionSource() }

                    AsyncImage(
                        model = if (hero.isLoading) {
                            Color.Transparent
                        } else {
                            hero.response.backgroundImageRequest()
                        },
                        /**
                         * Error is not desirable as it acts as transparent
                         * error = painterResource(R.drawable.poster),
                         *  */
                        contentDescription = hero.response.name,
                        contentScale = ContentScale.Crop,
                        clipToBounds = true,
                        alignment = BiasAlignment(0f, -0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize()
                            .height(
                                if (hasFocus) {
                                    300.dp
                                } else {
                                    200.dp
                                }
                            )
                            .onFocusChanged { newFocus ->
                                if (newFocus.isFocused) {
                                    onAction(HomeAction.SelectHero(uuid))
                                }
                            }
                            .let { modifier ->
                                if (hero.isLoading) {
                                    modifier.loading()
                                } else {
                                    modifier
                                }
                            }
                            .clickable(
                                indication = null,
                                enabled = true,
                                onClickLabel = stringResource(R.string.home_more_info),
                                role = Role.Image,
                                interactionSource = interactionSource
                            ) {
                                loadResult(
                                    hero.response.url, hero.response.apiName, hero.response.name
                                )
                            })
                }
            }

            Spacer(Modifier.height(MaterialTheme.padding.extraSmall))

            val uuid = row.sorted[state.currentPage]
            val hero = row[uuid]!!

            Text(text = hero.response.name, style = MaterialTheme.typography.titleLarge)
            val isPreview = LocalInspectionMode.current
            if (!hasFocus && !isPreview) {
                return@Column
            }
            if (hero.isLoading) {
                Spacer(modifier = Modifier.height(MaterialTheme.padding.extraSmall))
                LoadingLine(0.4f)
                LoadingLine()
                LoadingLine()
                LoadingLine(0.7f)
            } else {
                FlowRow(
                    itemVerticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                    maxLines = 1
                ) {
                    var addDot = false
                    val centerDotColor = MaterialTheme.colorScheme.onSurfaceVariant
                    val tvType = hero.response.type
                    if (tvType != null) {
                        Text(text = stringResource(tvType.toStringRes()))
                        addDot = true
                    }
                    if (hero.response.year != null) {
                        if (addDot) {
                            Text(text = "·", color = centerDotColor)
                        }
                        Text(text = hero.response.year.toString())
                        addDot = true
                    }
                    if (tvType != null) {
                        val episodes = hero.response.episodes.maxOfOrNull { it.value }

                        when (tvType) {
                            TvType.AnimeMovie, TvType.Live, TvType.Movie, TvType.Torrent, TvType.Video -> {
                                if (hero.response.duration != null) {
                                    Text(text = "·", color = centerDotColor)
                                    Text(text = hero.response.duration.toString())
                                    addDot = true
                                }
                            }

                            TvType.Anime, TvType.AsianDrama, TvType.Cartoon, TvType.TvSeries -> {
                                if (episodes != null && episodes > 0) {
                                    Text(text = "·", color = centerDotColor)
                                    Text(text = "$episodes ${stringResource(R.string.episodes)}")
                                    addDot = true
                                }
                            }

                            else -> {}
                        }
                    }
                    hero.response.score?.toStringNull(minScore = 0.1, maxScore = 10)?.let { score ->
                        if (addDot) {
                            Text(text = "·", color = centerDotColor)
                        }
                        Text(text = "★ $score")
                        addDot = true
                    }

                    if (addDot) {
                        Spacer(Modifier.width(MaterialTheme.padding.extraSmall))
                    }
                    hero.response.tags.take(10).forEach { tag ->
                        Text(
                            text = tag, modifier = Modifier
                                .border(
                                    width = 1.5.dp,
                                    color = Color.White.copy(alpha = 0.1f),
                                    shape = RoundedShape()
                                )
                                .padding(
                                    vertical = MaterialTheme.padding.extraSmall,
                                    horizontal = MaterialTheme.padding.small
                                )
                        )
                    }
                }

                val plot = hero.response.plot
                if (!plot.isNullOrBlank()) {
                    Text(
                        text = plot,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }

    @Composable
    fun TvLoadingRow() {
        Column {
            LoadingLine(0.4f, modifier = Modifier.padding(MaterialTheme.padding.medium))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
                contentPadding = PaddingValues(horizontal = MaterialTheme.padding.medium),
            ) {
                items(30) {
                    LoadingPoster(isHorizontal = false)
                }
            }
        }
    }

    @Composable
    fun LoadingPoster(isHorizontal: Boolean) {
        Column {
            Box(
                modifier = Modifier
                    .width(
                        if (isHorizontal) {
                            HORIZONTAL_POSTER_WIDTH
                        } else {
                            VERTICAL_POSTER_WIDTH
                        }
                    )
                    .aspectRatio(
                        if (isHorizontal) {
                            HORIZONTAL_ASPECT_RATIO
                        } else {
                            VERTICAL_ASPECT_RATIO
                        }
                    )
                    .loading()
                    .fadedOutline()
            )
            Spacer(modifier = Modifier.height(50.dp))
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun TvResumeRow(
        row: SearchableData<Int, ImmutableSearchResponse>, onAction: (HomeAction) -> Unit
    ) {
        TvRow(
            rowName = stringResource(R.string.continue_watching),
            isExpandable = true,
            isHorizontal = false,
            isLoading = false,
            items = { itemContent ->
                itemsIndexed(items = row.sorted, key = { _, id -> row[id]!!.uuid }) { index, id ->
                    val response = row[id]!!
                    itemContent(index, response)
                }
            },
            onAction = onAction
        )
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun TvSearchRow(row: ImmutableHomePageList, onAction: (HomeAction) -> Unit) {
        TvRow(
            rowName = row.name,
            isExpandable = true,
            isHorizontal = row.isHorizontalImages,
            isLoading = row.isLoading,
            items = { itemContent ->
                itemsIndexed(
                    items = row.items, key = { _, item -> item.uuid },
                    itemContent = itemContent,
                )
            },
            onAction = onAction
        )
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun TvRow(
        rowName: String,
        isExpandable: Boolean,
        isHorizontal: Boolean,
        isLoading: Boolean,
        items: LazyListScope.(@Composable LazyItemScope.(index: Int, response: ImmutableSearchResponse) -> Unit) -> Unit,
        onAction: (HomeAction) -> Unit
    ) {
        val searchAction = remember<(SearchResponseAction) -> Unit>(onAction) {
            { item ->
                onAction(HomeAction.ResultAction(item))
            }
        }

        val pxValue = with(LocalDensity.current) { MaterialTheme.padding.medium.toPx() }
        val newBringIntoViewSpec = remember(pxValue) { LeftAlignedFocusFollower(pxValue) }
        CompositionLocalProvider(LocalBringIntoViewSpec provides newBringIntoViewSpec) {
            Column {
                Text(
                    text = rowName,
                    modifier = Modifier.padding(MaterialTheme.padding.medium),
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.titleMedium,
                )

                Box(
                    contentAlignment = Alignment.CenterStart,
                ) {
                    val isPreview = LocalInspectionMode.current
                    var lastFocusResponse by remember {
                        mutableStateOf(
                            if (isPreview) {
                                ImmutableSearchResponse.previewMovie
                            } else {
                                null
                            }
                        )
                    }
                    var hasFocus by remember { mutableStateOf(false) }
                    val listState: LazyListState = rememberLazyListState()
                    val shouldLoadMore = remember {
                        derivedStateOf {
                            val lastVisibleIndex =
                                listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                            val totalItems = listState.layoutInfo.totalItemsCount
                            // Do not expand if zero, as it is probably loading
                            totalItems > 0 && lastVisibleIndex >= totalItems - 5
                        }
                    }

                    LaunchedEffect(shouldLoadMore.value) {
                        if (shouldLoadMore.value && !isLoading && isExpandable) {
                            onAction(HomeAction.Expand(rowName))
                        }
                    }
                    val focusRequester = remember { FocusRequester() }
                    LazyRow(
                        state = listState,
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
                        contentPadding = PaddingValues(horizontal = MaterialTheme.padding.medium),
                        modifier = Modifier
                            .onFocusChanged { newFocus ->
                                hasFocus = newFocus.hasFocus
                            }
                            .focusGroup()
                            .focusRestorer(fallback = focusRequester),
                        verticalAlignment = Alignment.CenterVertically) {
                        items { index, response ->
                            val isFocused =
                                (hasFocus || isPreview) && lastFocusResponse?.uuid == response.uuid
                            val isFirstItem = index == 0
                            SearchResponseItem(
                                response = response,
                                action = searchAction,
                                showText = false,
                                isHorizontal = isHorizontal,
                                modifier = Modifier.width(
                                    if (isHorizontal) {
                                        HORIZONTAL_POSTER_WIDTH
                                    } else {
                                        VERTICAL_POSTER_WIDTH
                                    } + if (isFocused) {
                                        FOCUS_EXTRA_WIDTH
                                    } else {
                                        0.dp
                                    }
                                ).alpha(
                                    if (isFocused) {
                                        0.0f
                                    } else {
                                        1.0f
                                    }
                                ).onFocusChanged { newFocus ->
                                    if (newFocus.isFocused) {
                                        lastFocusResponse = response
                                    }
                                }.let { modifier ->
                                    if (isFirstItem) {
                                        modifier.focusRequester(focusRequester)
                                    } else {
                                        modifier
                                    }
                                })
                        }
                        if (isLoading) {
                            items(10) {
                                LoadingPoster(isHorizontal)
                            }
                        }
                        item("tail") {
                            Spacer(modifier = Modifier.width(LocalWindowInfo.current.containerSize.width.dp))
                        }
                    }

                    val overlayResponse = lastFocusResponse
                    if ((hasFocus || isPreview) && overlayResponse != null) {
                        Row {
                            Spacer(modifier = Modifier.width(MaterialTheme.padding.medium))
                            Column {
                                SearchResponseItem(
                                    response = overlayResponse,
                                    action = searchAction,
                                    innerModifier = Modifier.whiteOutline(hasFocus = true),
                                    showText = true,
                                    clickable = false,
                                    isHorizontal = isHorizontal,
                                    modifier = Modifier
                                        .width(
                                            if (isHorizontal) {
                                                HORIZONTAL_POSTER_WIDTH
                                            } else {
                                                VERTICAL_POSTER_WIDTH
                                            } + FOCUS_EXTRA_WIDTH
                                        )
                                        .background(MaterialTheme.colorScheme.background),
                                    customText = overlayResponse.watchEpisode?.let { watchEpisode ->
                                        {
                                            val text = LocalContext.current.getShortSeasonText(
                                                episode = watchEpisode.episode,
                                                season = watchEpisode.season
                                            )
                                                ?: overlayResponse.watchPosition?.let { watchPosition ->
                                                    (watchPosition.duration - watchPosition.position).toComponents { hours, minutes, seconds, _ ->
                                                        if (hours > 0) {
                                                            stringResource(
                                                                R.string.next_episode_time_hour_format,
                                                                hours,
                                                                minutes
                                                            )
                                                        } else if (minutes > 0) {
                                                            stringResource(
                                                                R.string.next_episode_time_min_format,
                                                                hours,
                                                                minutes
                                                            )
                                                        } else {
                                                            null
                                                        }
                                                    }
                                                }
                                            Column(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                if (text != null) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.Center,
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        Icon(
                                                            painter = painterResource(R.drawable.play_arrow_24px),
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(24.dp)
                                                        )
                                                        Text(
                                                            text = text,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            maxLines = 1,
                                                            textAlign = TextAlign.Center,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        Spacer(modifier = Modifier.width(24.dp))
                                                    }
                                                }
                                                Text(
                                                    text = overlayResponse.name,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onBackground,
                                                    maxLines = if (text != null) {
                                                        1
                                                    } else {
                                                        2
                                                    },
                                                    textAlign = TextAlign.Center,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                        }
                                    })

                                /*val watchEpisode = overlayResponse.watchEpisode
                                if (watchEpisode != null) {
                                    val text =
                                        "S1:E2"//LocalContext.current.getShortSeasonText(episode = watchEpisode.episode, season = watchEpisode.season)
                                    if (text != null) {
                                        Text(
                                            text = text,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onBackground,
                                            maxLines = 2,
                                            textAlign = TextAlign.Center,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }*/
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun EditMenu(onDismissRequest: () -> Unit) {
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.background,
            onDismissRequest = onDismissRequest,
            confirmButton = {
            },
            dismissButton = {},
            text = {
                val settings = rememberAccountData()
                Column {
                    SwitchPreferenceWidget(
                        preference = settings.home.heroEnabled,
                        title = stringResource(R.string.hero),
                    )
                    SwitchPreferenceWidget(
                        preference = settings.home.continueWatchingEnabled,
                        title = stringResource(R.string.continue_watching),
                    )
                    SwitchPreferenceWidget(
                        preference = settings.home.bookmarksEnabled,
                        title = stringResource(R.string.error_bookmarks_text),
                    )
                }
            },
            title = {
                Text(text = stringResource(R.string.title_home))
            })
    }

    @Composable
    fun TvHeader(api: String?, onAction: (HomeAction) -> Unit) {
        val focusRequester = remember { FocusRequester() }
        var editMenu by remember { mutableStateOf(false) }
        if (editMenu) {
            EditMenu {
                editMenu = false
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(
                space = MaterialTheme.padding.small, alignment = Alignment.CenterHorizontally
            ),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .focusRestorer(fallback = focusRequester)
        ) {
            val activity = LocalActivity.current
            if (api != null) {
                FocusIconButton(onClick = {
                    QuickSearchFragment.pushSearch(
                        activity = activity, autoSearch = null, providers = arrayOf(api)
                    )
                }, transparent = true) {
                    Icon(
                        painter = painterResource(R.drawable.search_icon),
                        contentDescription = stringResource(R.string.search),
                    )
                }
            }

            FocusIconButton(onClick = {
                editMenu = true
            }, transparent = true) {
                Icon(
                    painter = painterResource(R.drawable.edit_24px),
                    contentDescription = stringResource(R.string.edit),
                )
            }

            FocusButton(
                text = if (api.isNullOrBlank()) {
                    stringResource(R.string.title_home)
                } else {
                    api
                }, onClick = {
                    onAction(HomeAction.OpenApiDialog)
                }, transparent = false, modifier = Modifier.focusRequester(focusRequester)
            )
            // TODO
            /*DropdownMenu(expanded = dialog != null, onDismissRequest = {
                onAction(HomeAction.DismissDialog)
            }) {
                if (dialog == null) {
                    return@DropdownMenu
                }
                Column {
                    SelectAPIDialog(dialog = dialog, onAction = onAction)
                }
            }*/
            FocusButton(
                text = stringResource(R.string.title_search), onClick = {
                    activity?.navigate(
                        R.id.navigation_search,
                    )
                }, transparent = true
            )
            FocusButton(
                text = stringResource(R.string.library), onClick = {
                    activity?.navigate(
                        R.id.navigation_library,
                    )
                }, transparent = true
            )
            FocusButton(
                text = stringResource(R.string.title_downloads), onClick = {
                    activity?.navigate(
                        R.id.navigation_downloads,
                    )
                }, transparent = true
            )
            FocusButton(
                text = stringResource(R.string.title_settings), onClick = {
                    activity?.navigate(
                        R.id.navigation_settings,
                    )
                }, transparent = true
            )/*FocusIconButton(onClick = {
                // Navigate
            }, transparent = true) {
                Icon(
                    painter = painterResource(R.drawable.settings),
                    contentDescription = stringResource(R.string.title_settings),
                )
            }*/
            AccountPin(35.dp)
        }
    }

    @Composable
    fun SelectAPIDialog(
        dialog: SelectAPIDialog,
        selectedAPIName: String?,
        onAction: (HomeAction) -> Unit,
    ) {
        val defaultFocusRequester: FocusRequester = remember { FocusRequester() }

        AlertDialog(containerColor = MaterialTheme.colorScheme.background, onDismissRequest = {
            onAction(HomeAction.DismissDialog)
        }, confirmButton = {

        }, dismissButton = {}, text = {
            LazyColumn {
                item {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusGroup()
                            .focusRestorer(defaultFocusRequester),
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                        contentPadding = PaddingValues(horizontal = MaterialTheme.padding.small)
                    ) {
                        var first = true
                        for ((text, types) in contentTypes) {
                            if (types.none(dialog.data.state::containsType)) {
                                continue
                            }
                            val modifier = if (first) {
                                first = false
                                Modifier.focusRequester(defaultFocusRequester)
                            } else {
                                Modifier
                            }
                            item(key = text) {
                                FilterSelection(
                                    modifier = modifier,
                                    filter = dialog.filter.tvTypes,
                                    text = text,
                                    types = types,
                                    onAction = onAction
                                )
                            }
                        }
                    }
                }

                item("no results found") {
                    if (dialog.data.sorted.isEmpty()) {
                        Text(
                            text = org.jetbrains.compose.resources.stringResource(Res.string.no_results_found),
                            modifier = Modifier
                                .secondaryItemAlpha()
                                .fillMaxWidth()
                                .padding(20.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                items(dialog.data.sorted, key = { uuid -> uuid }) { uuid ->
                    val api = dialog.data[uuid]!!
                    val isSelected = selectedAPIName == api.name
                    val (backgroundFocusRequester, button1FocusRequester, button2FocusRequester) = remember { FocusRequester.createRefs() }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .rounded()
                            .combinedClickable(onClick = {
                                if (isSelected) {
                                    onAction(HomeAction.SelectApi("None"))
                                } else {
                                    onAction(HomeAction.SelectApi(api.name))
                                }
                                onAction(HomeAction.DismissDialog)
                            }, onLongClick = {
                                if (api.isPinned) {
                                    onAction(HomeAction.RemovePin(api.name))
                                } else {
                                    onAction(HomeAction.AddPin(api.name))
                                }
                            })
                            .focusRequester(backgroundFocusRequester)
                            .focusProperties {
                                end = button1FocusRequester
                            }, verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isSelected) {
                            Icon(
                                painter = painterResource(R.drawable.ic_baseline_check_24),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        } else {
                            Spacer(modifier = Modifier.size(24.dp))
                        }
                        Text(
                            text = api.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(MaterialTheme.padding.medium)
                        )
                        Spacer(modifier = Modifier.weight(1.0f))
                        val settings =
                            (api.sourcePlugin?.let { PluginManager.plugins[it] } as? Plugin)?.openSettings
                        if (settings != null) {
                            val context = LocalContext.current
                            FocusIconButton(
                                transparent = true,
                                onClick = {
                                    settings(context)
                                },
                                modifier = Modifier
                                    .focusRequester(button1FocusRequester)
                                    .focusProperties {
                                        start = backgroundFocusRequester
                                        end = button2FocusRequester
                                    }) {
                                Icon(
                                    painter = painterResource(R.drawable.tune),
                                    contentDescription = stringResource(R.string.title_settings),
                                )
                            }
                        }
                        if (api.isPinned) {
                            FocusIconButton(
                                transparent = true, onClick = {
                                    onAction(HomeAction.RemovePin(api.name))
                                }, modifier = if (settings != null) {
                                    Modifier
                                        .focusRequester(button1FocusRequester)
                                        .focusProperties {
                                            start = backgroundFocusRequester
                                            end = button2FocusRequester
                                        }
                                } else {
                                    Modifier
                                        .focusRequester(button2FocusRequester)
                                        .focusProperties {
                                            start = button1FocusRequester
                                        }
                                }) {
                                Icon(
                                    painter = painterResource(R.drawable.keep),
                                    contentDescription = stringResource(R.string.favorite),
                                )
                            }
                        }
                    }
                }
            }
        }, title = {
            Text(text = stringResource(R.string.title_home))
        })
    }

    @Composable
    fun FilterSelection(
        filter: PersistentSet<TvType>,
        text: Int,
        types: PersistentList<TvType>,
        onAction: (HomeAction) -> Unit,
        modifier: Modifier = Modifier,
    ) {
        val selected = filter.contains(types[0])

        WhiteFilterChip(
            modifier = modifier,
            selected = selected,
            label = stringResource(text),
        ) {
            val newSelection = if (selected) {
                filter.removingAll(types)
            } else {
                filter.addingAll(types)
            }

            onAction(HomeAction.FilterByTypes(newSelection))
        }
    }
}