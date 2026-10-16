package com.lagradost.cloudstream3.ui.settings.extensions

import android.annotation.SuppressLint
import android.text.format.Formatter.formatShortFileSize
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusRequester.Companion.FocusRequesterFactory.component1
import androidx.compose.ui.focus.FocusRequester.Companion.FocusRequesterFactory.component2
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.fasterxml.jackson.core.JacksonException
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.plugins.PluginManager
import com.lagradost.cloudstream3.ui.settings.SettingsFragmentScreen
import com.lagradost.cloudstream3.ui.settings.extensions.RepoAdapter.Companion.SHAREABLE_REPO_SEPARATOR
import com.lagradost.cloudstream3.utils.SubtitleHelper.fromTagToLanguageName
import com.lagradost.cloudstream3.utils.SubtitleHelper.getNameNextToFlagEmoji
import com.lagradost.cloudstream4.compose.ActionDialog
import com.lagradost.cloudstream4.compose.BlackButton
import com.lagradost.cloudstream4.compose.BlackTextField
import com.lagradost.cloudstream4.compose.LogBoxBlack
import com.lagradost.cloudstream4.compose.LogBoxWhite
import com.lagradost.cloudstream4.compose.LogText
import com.lagradost.cloudstream4.compose.PHONE
import com.lagradost.cloudstream4.compose.Screen
import com.lagradost.cloudstream4.compose.WhiteButton
import com.lagradost.cloudstream4.compose.isLayout
import com.lagradost.cloudstream4.getPlainText
import com.lagradost.cloudstream4.setPlainText
import com.lagradost.cloudstream4.state.LogLevel
import com.lagradost.cloudstream4.state.ObserveEffect
import com.lagradost.cloudstream4.state.SearchableData
import com.lagradost.cloudstream4.state.SortByName
import com.lagradost.cloudstream4.theme.CloudStreamPreviewTheme
import com.lagradost.cloudstream4.viewmodels.AddRepositoryDialog
import com.lagradost.cloudstream4.viewmodels.ExtensionsAction
import com.lagradost.cloudstream4.viewmodels.ExtensionsEffect
import com.lagradost.cloudstream4.viewmodels.ExtensionsState
import com.lagradost.cloudstream4.viewmodels.ExtensionsViewModel2
import com.lagradost.cloudstream4.viewmodels.Repository
import com.lagradost.cloudstream4.viewmodels.RepositoryInfo
import com.lagradost.cloudstream4.viewmodels.RepositoryStatus
import com.mihon.material.AppBar
import com.mihon.material.padding
import com.mihon.presentation.LocalBackPress
import com.mihon.presentation.secondaryItemAlpha
import com.mihon.presentation.settings.LocalPreferenceHighlighted
import com.mihon.presentation.settings.widget.BasePreferenceWidget
import com.mihon.presentation.settings.widget.PrefsHorizontalPadding
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid
import com.lagradost.cloudstream4.compose.IsScrolling
import com.lagradost.cloudstream4.compose.MultiSelectDialog
import com.lagradost.cloudstream4.compose.WhiteFilterChip
import com.lagradost.cloudstream4.compose.rounded
import com.lagradost.cloudstream4.generated.resources.Res
import com.lagradost.cloudstream4.generated.resources.cancel
import com.lagradost.cloudstream4.generated.resources.no_results_found
import com.lagradost.cloudstream4.generated.resources.ok
import com.lagradost.cloudstream4.viewmodels.DerivedRepositoryState
import com.lagradost.cloudstream4.viewmodels.FilterPlugin
import com.lagradost.cloudstream4.viewmodels.Plugin
import com.lagradost.cloudstream4.viewmodels.PluginStatus
import com.mihon.presentation.settings.widget.TextPreferenceWidget
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentSet
import kotlin.math.roundToInt
import kotlin.stackTraceToString
import kotlin.text.replace

@Composable
@PreviewLightDark
fun Preview() {
    CloudStreamPreviewTheme {
        val uuid1 = Uuid.random()
        val uuid2 = Uuid.random()
        val uuid3 = Uuid.random()
        ExtensionsScreen.Content(
            state = ExtensionsState(
                addDialog = AddRepositoryDialog(error = Throwable()),
                repositories = SearchableData.from(
                    data = persistentMapOf(
                        uuid1 to Repository(
                            uuid = uuid1,
                            info = RepositoryInfo(
                                name = "Cloudstream providers repository",
                                description = "hello",
                                url = "https://raw.githubusercontent.com/recloudstream/extensions/refs/heads/master/repo.json",
                                iconUrl = null
                            ),
                            status = RepositoryStatus.Loading
                        ),
                        uuid2 to Repository(
                            uuid = uuid2,
                            info = RepositoryInfo(
                                name = "codeberg test",
                                url = "https://codeberg.org/",
                                iconUrl = null
                            ),
                            status = RepositoryStatus.Loading
                        ),
                        uuid3 to Repository(
                            uuid = uuid3,
                            info = RepositoryInfo(
                                name = "parsebin test",
                                url = "https://pastebin.com/",
                                iconUrl = null
                            ),
                            status = RepositoryStatus.Loading
                        )
                    ),
                    sortedBy = SortByName { it.info.name }
                )
            ),
            onAction = {}
        )
    }
}


object ExtensionsScreen : Screen {
    @Composable
    override fun Content() {
        val viewModel =
            viewModel<ExtensionsViewModel2> { ExtensionsViewModel2(manager = AndroidRepositoryManager) }
        LaunchedEffect(Unit) { // TODO fix this to only do if necessary
            viewModel.onAction(ExtensionsAction.Init)
        }
        val state by viewModel.state.collectAsState()
        val highLight = remember { mutableStateOf<Uuid?>(null) }
        ObserveEffect(viewModel.effect) { effect ->
            when (effect) {
                is ExtensionsEffect.RepositoryAdded -> {
                    highLight.value = effect.uuid
                }
            }
        }
        Content(state = state, onAction = viewModel::onAction, highLightState = highLight)
    }

    @Composable
    fun Content(
        state: ExtensionsState,
        onAction: (ExtensionsAction) -> Unit,
        highLightState: MutableState<Uuid?> = remember { mutableStateOf(null) },
    ) {
        val (top, bottom) = remember { FocusRequester.createRefs() }
        val fabExpanded = remember { mutableStateOf(true) }



        com.mihon.material.Scaffold(
            // TODO @WindowInsets remove this when we have converted everything to compose
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                AppBar(
                    modifier = Modifier
                        .focusRequester(top)
                        .focusProperties {
                            down = bottom
                        },
                    title = if (state.pluginFilter.repository != null) {
                        state.repositories.data[state.pluginFilter.repository]?.info?.name
                        /** Should never happen */
                            ?: stringResource(R.string.extensions)
                    } else if (state.searchPlugins) {
                        stringResource(R.string.search)
                    } else {
                        stringResource(R.string.extensions)
                    },
                    navigateUp = LocalBackPress.current,
                    actions = {
                        var languageDialog by remember { mutableStateOf(false) }
                        val derivedRepositoryState = state.derivedRepositoryState

                        /** We only show the langauge filter button if we are searching plugins and there is languages to pick from*/
                        if (state.searchPlugins && derivedRepositoryState.languageCount.isNotEmpty()) {
                            IconButton(onClick = {
                                languageDialog = true
                            }) {
                                Icon(
                                    tint = MaterialTheme.colorScheme.onBackground,
                                    painter = painterResource(R.drawable.language_korean_latin_24px),
                                    contentDescription = stringResource(R.string.extension_language)
                                )
                            }
                        }
                        if (languageDialog) {
                            val entries =
                                derivedRepositoryState.languageCount.keys.sortedBy { lang ->
                                    fromTagToLanguageName(lang) ?: lang
                                }

                            MultiSelectDialog(
                                title = stringResource(R.string.extension_language),
                                entries = entries.associateWith { lang ->
                                    if (lang == "") {
                                        stringResource(R.string.no_data)
                                    } else {
                                        (getNameNextToFlagEmoji(
                                            lang
                                        ) ?: lang)
                                    }
                                },
                                properties = DialogProperties(
                                    usePlatformDefaultWidth = true,
                                ),
                                confirmText = stringResource(R.string.ok),
                                confirm = { selection ->
                                    val removed =
                                        derivedRepositoryState.languageCount.keys.subtract(selection)
                                    onAction(
                                        ExtensionsAction.FilterByInverseLanguages(
                                            removed.toPersistentSet()
                                        )
                                    )
                                    languageDialog = false
                                },
                                dismissText = stringResource(R.string.cancel),
                                dismiss = {
                                    languageDialog = false
                                },
                                selectedKeys = derivedRepositoryState.languageCount.keys.subtract(
                                    state.pluginFilter.removeLanguages
                                ),
                            )
                        }
                    },
                    scrollBehavior = it,
                )
            },
            bottomBar = {
                BottomBar(state = state, onAction = onAction)
            },
            floatingActionButton = {
                if (isLayout(PHONE) && !state.searchPlugins) {
                    FloatingActionButton(state = state, onAction = onAction, expanded = fabExpanded)
                }
            },
            content = { contentPadding ->
                Content(
                    contentPadding = contentPadding,
                    state = state,
                    onAction = onAction,
                    highLightState = highLightState,
                    fabExpanded = fabExpanded
                )
            },
        )
    }

    /** To make the UI even remotely usable, we batch the types */
    val contentTypes: PersistentList<Pair<Int, PersistentList<TvType>>> = persistentListOf(
        R.string.movies_singular to persistentListOf(TvType.Movie),
        R.string.tv_series_singular to persistentListOf(TvType.TvSeries),
        R.string.anime_singular to persistentListOf(TvType.Anime, TvType.AnimeMovie, TvType.OVA),
        R.string.cartoons_singular to persistentListOf(TvType.Cartoon),
        R.string.asian_drama_singular to persistentListOf(TvType.AsianDrama),
        R.string.live_singular to persistentListOf(TvType.Live),
        R.string.documentaries_singular to persistentListOf(TvType.Documentary),
        R.string.torrent_singular to persistentListOf(TvType.Torrent),
        R.string.audio_singular to persistentListOf(
            TvType.Audio,
            TvType.AudioBook,
            TvType.Podcast,
            TvType.Music
        ),
        R.string.nsfw_singular to persistentListOf(TvType.NSFW),
        R.string.others to persistentListOf(
            TvType.CustomMedia,
            TvType.Video,
            TvType.Others
        ),
    )

    @SuppressLint("UnrememberedMutableState")
    @Composable
    fun Content(
        contentPadding: PaddingValues,
        state: ExtensionsState,
        onAction: (ExtensionsAction) -> Unit,
        highLightState: MutableState<Uuid?>,
        fabExpanded: MutableState<Boolean>,
    ) {
        AddDialog(dialog = state.addDialog, onAction = onAction)

        val lazyRepoState = rememberLazyListState()
        val textState = rememberTextFieldState()
        val lazyState = if (state.searchPlugins) {
            rememberLazyListState()
        } else {
            lazyRepoState
        }

        BackHandler(enabled = state.searchPlugins) {
            textState.clearText()
            onAction(ExtensionsAction.ClearPluginSearch)
        }

        /** Fab scrolling, this works but is currently a bit too fast IMPO */
        lazyRepoState.IsScrolling(up = {
            fabExpanded.value = true
        }, down = {
            fabExpanded.value = false
        })

        /**
         * We are using mihons highlight here, meaning that we scroll for a few sec
         * and then remove the highlight to prevent backpresses from rehighlighting it.
         * The highlight is never in the viewmodel as it is a view issue.
         * */
        var highLight by highLightState
        LaunchedEffect(highLight) {
            if (highLight == null) return@LaunchedEffect
            val list = if (state.searchPlugins) {
                state.plugins.sorted
            } else {
                state.repositories.sorted
            }
            val index = list.indexOfFirst { uuid -> uuid == highLight }
            if (index != -1) {
                lazyRepoState.animateScrollToItem(
                    index +
                            /** We have some extra items here */
                            if (state.searchPlugins) 2 else 1
                )
            }
            delay(5.0.seconds)
            highLight = null
        }

        /**
         * This may look a bit weird, but it is very jarring to snap the UI like this
         * (because we want to have 2 different scrolls)
         *
         * The user expects a transition, so we fade it using animateTo + remember(repository),
         * NOT snapTo(0.0f) as snapTo is 1 recomposition too slow!
         * */
        val itemAlpha = remember(state.pluginFilter.repository) { Animatable(0f) }
        LaunchedEffect(state.pluginFilter.repository) {
            itemAlpha.snapTo(0.0f)
            itemAlpha.animateTo(1f)
        }

        LaunchedEffect(textState.text) {
            onAction(ExtensionsAction.Search(textState.text.toString()))
        }

        LazyColumn(
            state = lazyState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "header") {
                Spacer(modifier = Modifier.height(MaterialTheme.padding.medium))
                SettingsFragmentScreen.SettingsSearch(textState)
            }
            if (state.searchPlugins) {
                item(key = "filter") {
                    Spacer(modifier = Modifier.height(MaterialTheme.padding.small))

                    /**
                     * In the case of a filter applied to the repository,
                     * we want to use the associated derived state to calculate what types this
                     * repository has
                     * */
                    val derivedState =
                        state.pluginFilter.repository?.let(state.plugins.state.repositories::get)
                            ?: state.plugins.state.sumOfRepositories

                    FilterRow(
                        filter = state.pluginFilter,
                        derivedState = derivedState,
                        onAction = onAction
                    )
                }

                items(items = state.plugins.sorted, key = { uuid -> uuid }) { uuid ->
                    val plugin = state.plugins.data[uuid]!!
                    val isHighlighted = uuid == highLight
                    CompositionLocalProvider(
                        LocalPreferenceHighlighted provides isHighlighted,
                    ) {
                        Plugin(
                            plugin = plugin,
                            onAction = onAction,
                            modifier = Modifier
                                .animateItem()
                                .graphicsLayer { alpha = itemAlpha.value },
                            onClick = {
                                if (state.pluginFilter.repository == null) {
                                    onAction(ExtensionsAction.SelectPlugin(plugin.uuid))
                                    highLightState.value = plugin.uuid
                                }
                            }
                        )
                    }
                }

                val repository = state.pluginFilter.repository
                val status = repository?.let(state.repositories.data::get)?.status
                if (state.plugins.sorted.isEmpty()) {
                    when (status) {
                        is RepositoryStatus.Failure -> {
                            item {
                                ErrorButton(status.cause)
                            }
                        }

                        RepositoryStatus.Loading -> {
                            item {
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.onBackground,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                )
                            }
                        }

                        RepositoryStatus.Success, null -> {
                            val derivedState =
                                repository?.let(state.plugins.state.repositories::get)

                            item {
                                Text(
                                    text =
                                        if (repository != null && (derivedState == null || derivedState.total == 0)) {
                                            /**
                                             * If we have a repository filter on,
                                             * but it does not contain anything then show this
                                             * instead as we do not really filter it but
                                             * the repo is simply empty. This should mostly be used
                                             * for plugin authors
                                             * */
                                            stringResource(R.string.no_plugins_found_error)
                                        } else {
                                            org.jetbrains.compose.resources.stringResource(Res.string.no_results_found)
                                        },
                                    modifier = Modifier
                                        .secondaryItemAlpha()
                                        .fillMaxWidth()
                                        .padding(20.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            } else {
                items(items = state.repositories.sorted, key = { uuid -> uuid }) { uuid ->
                    val repository = state.repositories.data[uuid]!!
                    val isHighlighted = uuid == highLight
                    CompositionLocalProvider(
                        LocalPreferenceHighlighted provides isHighlighted,
                    ) {
                        Repository(
                            repository = repository,
                            onAction = onAction,
                            modifier = Modifier
                                .animateItem()
                                .graphicsLayer { alpha = itemAlpha.value }
                        )
                    }
                }
                if (state.repositories.sorted.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.blank_repo_message),
                            modifier = Modifier
                                .secondaryItemAlpha()
                                .fillMaxWidth()
                                .padding(20.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun FilterRow(
        filter: FilterPlugin,
        derivedState: DerivedRepositoryState,
        onAction: (ExtensionsAction) -> Unit,
    ) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            contentPadding = PaddingValues(horizontal = MaterialTheme.padding.small)
        ) {
            for ((text, types) in contentTypes) {
                if (types.none(derivedState::containsType)) {
                    continue
                }
                item(key = text) {
                    FilterSelection(
                        filter = filter,
                        text = text,
                        types = types,
                        onAction = onAction
                    )
                }
            }
        }
    }

    @Composable
    fun FilterSelection(
        filter: FilterPlugin,
        text: Int,
        types: PersistentList<TvType>,
        onAction: (ExtensionsAction) -> Unit,
    ) {
        val selected =
            filter.tvTypes.contains(types[0])

        WhiteFilterChip(
            selected = selected,
            label = stringResource(text),
        ) {
            onAction(
                ExtensionsAction.FilterByTypes(
                    if (selected) {
                        filter.tvTypes.removingAll(types)
                    } else {
                        filter.tvTypes.addingAll(types)
                    }
                )
            )
        }
    }

    @Composable
    fun ErrorDialog(
        error: Throwable,
        tag: String,
        confirmButton: @Composable (() -> Unit),
        onDismissRequest: () -> Unit
    ) {
        val message = error.toString() + "\n\n" + error.stackTraceToString()
            .lines()
            .mapNotNull { line ->
                val trimmed = line.trim()
                if (!trimmed.startsWith("at ")) return@mapNotNull null
                // pull "File.kt:42" out of "at com.pkg.Class.method(File.kt:42)"
                Regex("""\(([^)]+)\)$""").find(trimmed)?.groupValues?.get(1)
                    ?.replace(":", " ")
            }
            .joinToString("\n")
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.background,
            onDismissRequest = onDismissRequest,
            confirmButton = confirmButton,
            dismissButton = {
                BlackButton(text = stringResource(R.string.dismiss), onClick = onDismissRequest)
            },
            text = {
                val clipboard = LocalClipboard.current
                val scope = rememberCoroutineScope()
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        LogBoxWhite(LogLevel.Error.identifier)
                        LogBoxBlack(tag)
                    }
                    LogText(
                        level = LogLevel.Error,
                        message = message,
                        onClick = {
                            scope.launch {
                                clipboard.setPlainText("Logcat", message)
                            }
                        })
                }
            },
            title = {
                Text(text = stringResource(R.string.error))
            })
    }

    @Composable
    fun Plugin(
        plugin: Plugin,
        onAction: (ExtensionsAction) -> Unit,
        modifier: Modifier,
        onClick: () -> Unit,
    ) {
        val description = plugin.info.description

        BasePreferenceWidget(
            modifier = modifier,
            title = plugin.info.name,
            subcomponent =
                {
                    Column(modifier = Modifier.padding(horizontal = PrefsHorizontalPadding)) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small)
                        ) {
                            val language = plugin.info.language
                            if (!language.isNullOrBlank()) {
                                Text(
                                    text = getNameNextToFlagEmoji(language) ?: language,
                                    modifier = Modifier
                                        .secondaryItemAlpha(),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Text(
                                text = "v${plugin.info.version}",
                                modifier = Modifier
                                    .secondaryItemAlpha(),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            val sizeBytes = plugin.source?.size
                            if (sizeBytes != null) {
                                Text(
                                    text = formatShortFileSize(LocalContext.current, sizeBytes),
                                    modifier = Modifier
                                        .secondaryItemAlpha(),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            if (plugin.info.tvTypes.contains(TvType.NSFW)) {
                                Text(
                                    text = "18+",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorResource(R.color.adultColor)
                                )
                            }
                        }
                        if (!description.isNullOrBlank()) {
                            Text(
                                text = description,
                                modifier = Modifier
                                    .secondaryItemAlpha(),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
            icon = {
                RepositoryIcon(
                    iconUrl = plugin.info.iconUrl,
                    defaultIcon = painterResource(R.drawable.extension_24px)
                )
            },
            onClick = onClick,
            onLongClick = {
            },
            widget = {
                when (val status = plugin.status) {
                    is PluginStatus.Downloading -> {
                        val total = status.total
                        if (total != null) {
                            CircularProgressIndicator(
                                progress = {
                                    status.progress.toFloat() / total.toFloat()
                                },
                                modifier = Modifier
                                    .size(IconButtonDefaults.smallContainerSize())
                                    .padding(MaterialTheme.padding.small),
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                        } else {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(IconButtonDefaults.smallContainerSize())
                                    .padding(MaterialTheme.padding.small),
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                    }

                    is PluginStatus.Failed -> {
                        var errorDialog by remember { mutableStateOf(false) }
                        IconButton(onClick = {
                            errorDialog = true
                        }) {
                            Icon(
                                painter = painterResource(R.drawable.warning_24px),
                                contentDescription = null
                            )
                        }
                        if (errorDialog) {
                            ErrorDialog(
                                error = status.cause,
                                tag = stringResource(
                                    R.string.plugin_load_fail,
                                    plugin.info.name
                                ),
                                confirmButton = {
                                    WhiteButton(
                                        text = stringResource(R.string.reload_error),
                                        onClick = {
                                            errorDialog = false
                                            onAction(ExtensionsAction.DownloadPlugin(plugin.uuid))
                                        })
                                },
                                onDismissRequest = {
                                    errorDialog = false
                                }
                            )
                        }
                    }

                    PluginStatus.Idle -> {
                        if (plugin.downloadLocation != null) {
                            val cs3Plugin =
                                (plugin.source?.pluginUrl?.let(PluginManager.urlPlugins::get)
                                    ?: (PluginManager.plugins[plugin.downloadLocation])) as? com.lagradost.cloudstream3.plugins.Plugin

                            Row {
                                val settings = cs3Plugin?.openSettings
                                if (settings != null) {
                                    val context = LocalContext.current
                                    IconButton(onClick = {
                                        try {
                                            settings.invoke(context)
                                        } catch (e: Throwable) {
                                            Log.e(
                                                "PluginAdapter",
                                                "Failed to open ${plugin.info.name} settings: ${
                                                    Log.getStackTraceString(e)
                                                }"
                                            )
                                        }
                                    }) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_baseline_tune_24),
                                            contentDescription = null
                                        )
                                    }
                                }
                                IconButton(onClick = {
                                    onAction(ExtensionsAction.DeletePlugin(plugin.uuid))
                                }) {
                                    Icon(
                                        painter = painterResource(R.drawable.delete_24px),
                                        contentDescription = null
                                    )
                                }
                            }
                        } else {
                            IconButton(onClick = {
                                onAction(ExtensionsAction.DownloadPlugin(plugin.uuid))
                            }) {
                                Icon(
                                    painter = painterResource(R.drawable.netflix_download),
                                    contentDescription = null
                                )
                            }
                        }
                    }
                }
            },
        )
    }

    @Composable
    fun AddDialog(dialog: AddRepositoryDialog?, onAction: (ExtensionsAction) -> Unit) {
        if (dialog == null) {
            return
        }

        AlertDialog(
            containerColor = MaterialTheme.colorScheme.background,
            onDismissRequest = {
                onAction(ExtensionsAction.SetRepositoryDialog(null))
            },
            confirmButton = {
                WhiteButton(text = stringResource(R.string.add_repository), onClick = {
                    onAction(ExtensionsAction.DownloadRepositoryWithDialog(url = dialog.url))
                })
            },
            dismissButton = {
                BlackButton(text = stringResource(R.string.cancel), onClick = {
                    onAction(ExtensionsAction.SetRepositoryDialog(null))
                })
            },
            text = {
                Column {
                    BlackTextField(
                        value = dialog.url,
                        placeHolder = stringResource(R.string.repository_url_hint),
                        keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Uri),
                        singleLine = true
                    ) { newValue ->
                        onAction(
                            ExtensionsAction.SetRepositoryDialog(
                                dialog.copy(
                                    url = newValue,
                                    error = null
                                )
                            )
                        )
                    }
                    Crossfade(dialog.loading) { loading ->
                        if (loading) {
                            Column {
                                Spacer(modifier = Modifier.height(MaterialTheme.padding.medium))
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.onBackground,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                )
                            }
                        } else {
                            ErrorButton(dialog.error)
                        }
                    }
                }
            },
            title = {
                Text(text = stringResource(R.string.add_repository))
            })
    }

    @Composable
    fun ErrorButton(error: Throwable?) {
        var shownError by remember { mutableStateOf(false) }

        if (shownError && error != null) {
            ErrorDialog(
                error = error,
                tag = stringResource(R.string.error_invalid_url),
                confirmButton = {},
                onDismissRequest = {
                    shownError = false
                })
        }

        AnimatedVisibility(error != null, enter = fadeIn(), exit = fadeOut()) {
            if (error == null) {
                return@AnimatedVisibility
            }

            val message = error.message
            TextPreferenceWidget(
                modifier = Modifier.rounded(),
                title = when {
                    error is JacksonException -> {
                        stringResource(R.string.no_repository_found_error)
                    }

                    error is SerializationException -> {
                        stringResource(R.string.no_repository_found_error)
                    }

                    message != null -> {
                        message
                    }

                    else -> stringResource(R.string.error_invalid_data)
                },
                icon = painterResource(R.drawable.warning_24px),
                iconTint = LocalContentColor.current
            ) {
                shownError = true
            }
        }
    }

    @Composable
    fun RepositoryIcon(iconUrl: String?, defaultIcon: Painter) {
        if (iconUrl != null) {
            AsyncImage(
                model = remapUrl(url = iconUrl, size = 24.dp),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                error = defaultIcon
            )
        } else {
            Icon(
                painter = defaultIcon,
                tint = MaterialTheme.colorScheme.onBackground,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
        }
    }

    @Composable
    fun FloatingActionButton(
        state: ExtensionsState,
        expanded: MutableState<Boolean>,
        onAction: (ExtensionsAction) -> Unit
    ) {
        val clipboard = LocalClipboard.current
        val scope = rememberCoroutineScope()

        ExtendedFloatingActionButton(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onBackground,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp,
                focusedElevation = 0.dp,
                hoveredElevation = 0.dp
            ),
            expanded = expanded.value,
            text = {
                Text(stringResource(R.string.add_repository))
            },
            icon = {
                Icon(
                    painter = painterResource(R.drawable.add_24px),
                    contentDescription = stringResource(R.string.add_repository)
                )
            },
            onClick = {
                scope.launch {
                    val copiedText = clipboard.getPlainText()?.trim() ?: ""

                    /** text is of format <repository name> : <repository url> */
                    val url = if (copiedText.contains(SHAREABLE_REPO_SEPARATOR)) {
                        val (_, url) = copiedText.split(
                            SHAREABLE_REPO_SEPARATOR,
                            limit = 2
                        )
                        url.trim()
                    }
                    /** Can this realistically be an url? */
                    else if (copiedText.length < 200 && !copiedText.contains("\n")) {
                        copiedText
                    }
                    /** Otherwise we just do a clear */
                    else {
                        ""
                    }
                    onAction(
                        ExtensionsAction.SetRepositoryDialog(
                            AddRepositoryDialog(url = url)
                        )
                    )
                }
            })
    }

    @Composable
    fun BottomBar(
        state: ExtensionsState,
        onAction: (ExtensionsAction) -> Unit
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(MaterialTheme.padding.small)
        ) {
            Text(stringResource(R.string.extensions))
            Spacer(modifier = Modifier.height(MaterialTheme.padding.small))
            val shape = RoundedCornerShape(3.dp)
            val size = 10.dp
            Row(modifier = Modifier.clip(shape)) {
                Box(
                    modifier = Modifier
                        .height(size)
                        .background(color = MaterialTheme.colorScheme.onBackground)
                        .weight(
                            maxOf(
                                state.plugins.state.downloadedAndEnabled.toFloat(),
                                0.1f
                            )
                        )
                )
                Box(
                    modifier = Modifier
                        .height(size)
                        .background(color = MaterialTheme.colorScheme.primary)
                        .weight(
                            maxOf(
                                state.plugins.state.downloadedAndDisabled.toFloat(),
                                0.1f
                            )
                        )
                )
                Box(
                    modifier = Modifier
                        .height(size)
                        .background(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        .weight(maxOf(state.plugins.state.notDownloaded.toFloat(), 0.1f))
                )
            }
            Spacer(modifier = Modifier.height(MaterialTheme.padding.small))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(
                    MaterialTheme.padding.extraSmall
                )
            ) {
                Box(
                    modifier = Modifier
                        .size(size)
                        .background(
                            color = MaterialTheme.colorScheme.onBackground,
                            shape = shape
                        )
                )
                Text(
                    text = stringResource(
                        R.string.plugins_downloaded,
                        state.plugins.state.downloadedAndEnabled
                    ),
                    style = MaterialTheme.typography.labelMedium
                )
                Box(
                    modifier = Modifier
                        .size(size)
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = shape
                        )
                )
                Text(
                    text = stringResource(
                        R.string.plugins_disabled,
                        state.plugins.state.downloadedAndDisabled
                    ),
                    style = MaterialTheme.typography.labelMedium
                )
                Box(
                    modifier = Modifier
                        .size(size)
                        .background(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            shape = shape
                        )
                )
                Text(
                    text = stringResource(
                        R.string.plugins_not_downloaded,
                        state.plugins.state.notDownloaded
                    ),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }

    @Composable
    fun Repository(
        modifier: Modifier,
        repository: Repository,
        onAction: (ExtensionsAction) -> Unit
    ) {
        var deleteDialog by remember { mutableStateOf(false) }

        if (deleteDialog) {
            ActionDialog(
                title = stringResource(R.string.delete_repository),
                text = stringResource(R.string.delete_repository_plugins),
                dismiss = {
                    deleteDialog = false
                },
                confirm = {
                    onAction(ExtensionsAction.DeleteRepository(repository.uuid))
                },
                confirmText = stringResource(R.string.delete),
                dismissText = stringResource(R.string.cancel),
            )
        }

        val clipboard = LocalClipboard.current
        val scope = rememberCoroutineScope()
        val copyTextLabel = stringResource(R.string.repo_copy_label)

        BasePreferenceWidget(
            modifier = modifier,
            title = repository.info.name,
            subcomponent = {
                Text(
                    text = repository.info.description ?: repository.info.url,
                    modifier = Modifier
                        .padding(horizontal = PrefsHorizontalPadding)
                        .secondaryItemAlpha(),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 10,
                )
            },
            icon = {
                RepositoryIcon(
                    iconUrl = repository.info.iconUrl,
                    painterResource(
                        when {
                            repository.info.url.contains("codeberg") -> R.drawable.codeberg
                            repository.info.url.contains("pastebin") -> R.drawable.pastebin
                            else -> R.drawable.ic_github_logo
                        }
                    )
                )
            },
            onClick = {
                onAction(ExtensionsAction.SelectRepository(repository.uuid))
            },
            onLongClick = {
                val shareableRepoData =
                    "${repository.info.name}$SHAREABLE_REPO_SEPARATOR\n ${repository.info.url}"

                scope.launch {
                    clipboard.setPlainText(
                        label = copyTextLabel,
                        text = shareableRepoData
                    )
                }
            },
            widget = {
                when (val reason = repository.status) {
                    is RepositoryStatus.Failure -> {
                        var errorDialog by remember { mutableStateOf(false) }
                        IconButton(onClick = {
                            errorDialog = true
                        }) {
                            Icon(
                                painter = painterResource(R.drawable.warning_24px),
                                contentDescription = null
                            )
                        }
                        if (errorDialog) {
                            ErrorDialog(
                                error = reason.cause,
                                tag = stringResource(R.string.error_invalid_data),
                                confirmButton = {
                                    WhiteButton(text = stringResource(R.string.reload_error)) {
                                        onAction(ExtensionsAction.ReloadRepository(repository.uuid))
                                    }
                                    WhiteButton(text = stringResource(R.string.delete_repository)) {
                                        deleteDialog = true
                                    }
                                },
                                onDismissRequest = {
                                    errorDialog = false
                                })
                        }
                    }

                    RepositoryStatus.Loading, RepositoryStatus.Success -> {
                        IconButton(onClick = {
                            deleteDialog = true
                        }) {
                            Icon(
                                painter = painterResource(R.drawable.delete_24px),
                                contentDescription = null
                            )
                            /*CircularProgressIndicator(
                                modifier = Modifier
                                    .size(IconButtonDefaults.smallContainerSize())
                                    .padding(MaterialTheme.padding.small),
                                color = MaterialTheme.colorScheme.onBackground,
                            )*/
                        }
                    }
                }
            },
        )
    }

    @Composable
    fun remapUrl(url: String, size: Dp): String {
        tailrec fun findClosestBase2(target: Int, current: Int = 16, max: Int = 512): Int {
            if (current >= max) return max
            if (current >= target) return current
            return findClosestBase2(target, current * 2, max)
        }

        val pxValue = with(LocalDensity.current) { maxOf(1, size.toPx().roundToInt()) }
        return url.replace(
            "%size%",
            "${findClosestBase2(pxValue, 16, 512)}"
        ).replace(
            "%exact_size%",
            "$pxValue"
        )
    }
}