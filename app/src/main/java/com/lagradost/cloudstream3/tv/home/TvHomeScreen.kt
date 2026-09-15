package com.lagradost.cloudstream3.tv.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.WideButton
import androidx.tv.material3.WideButtonDefaults
import com.lagradost.cloudstream3.tv.components.TvFocusScale
import com.lagradost.cloudstream3.tv.components.TvOnResume
import com.lagradost.cloudstream3.tv.data.TvContinueWatchingResume
import com.lagradost.cloudstream3.tv.model.TvContentRef
import com.lagradost.cloudstream3.tv.model.TvContinueWatchingClassifier
import com.lagradost.cloudstream3.tv.model.TvCwClass
import com.lagradost.cloudstream3.tv.model.TvHomeAction
import com.lagradost.cloudstream3.tv.model.TvHomeCatalog
import com.lagradost.cloudstream3.tv.model.TvHomeUiState
import com.lagradost.cloudstream3.tv.model.TvMediaItem
import com.lagradost.cloudstream3.tv.model.TvPlaybackRequest
import com.lagradost.cloudstream3.tv.model.TvRailIds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Hoisted Home focus memory — survives destination switches in [com.lagradost.cloudstream3.tv.navigation.TvNavigationShell].
 * Indices are coerced against dynamic rail item counts after catalog loads.
 */
class TvHomeFocusState(
    val railFocusIndices: SnapshotStateMap<String, Int> = mutableStateMapOf(),
) {
    var lastFocusedRailId: String? by mutableStateOf(null)
    var initialHeroFocusDone: Boolean by mutableStateOf(false)

    fun indexFor(railId: String): Int = railFocusIndices[railId] ?: 0

    fun update(railId: String, index: Int) {
        railFocusIndices[railId] = index
        lastFocusedRailId = railId
    }

    /** Drop remembered indices for rails that disappeared or shrank past the index. */
    fun pruneTo(catalog: TvHomeCatalog) {
        val alive = catalog.rails.associate { it.id to it.items.size }
        val stale = railFocusIndices.keys.filter { it !in alive }
        stale.forEach { railFocusIndices.remove(it) }
        alive.forEach { (id, size) ->
            if (size <= 0) {
                railFocusIndices.remove(id)
            } else {
                val idx = railFocusIndices[id] ?: return@forEach
                if (idx > size - 1) railFocusIndices[id] = size - 1
            }
        }
        if (lastFocusedRailId != null && lastFocusedRailId !in alive) {
            lastFocusedRailId = null
        }
    }
}

@Composable
fun rememberTvHomeFocusState(): TvHomeFocusState = remember { TvHomeFocusState() }

@Composable
fun TvHomeScreen(
    focusState: TvHomeFocusState,
    modifier: Modifier = Modifier,
    onOpenDetails: (TvContentRef) -> Unit = {},
    onPlaybackRequest: (TvPlaybackRequest) -> Unit = {},
    viewModel: TvHomeViewModel = viewModel(),
) {
    val uiState by viewModel.state.collectAsState()
    var notice by remember { mutableStateOf<String?>(null) }
    var resolvingCwId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val resumeResolver = remember { TvContinueWatchingResume() }

    // Refresh Continue Watching on enter / Activity resume (read-only; no homepage re-fetch).
    LaunchedEffect(Unit) { viewModel.onAction(TvHomeAction.RefreshContinueWatching) }
    TvOnResume {
        viewModel.onAction(TvHomeAction.RefreshContinueWatching)
    }

    fun openDetailsOrNotice(item: TvMediaItem) {
        val ref = TvContentRef.fromMediaItem(item)
        if (ref != null) {
            notice = null
            onOpenDetails(ref)
        } else {
            notice = if (item.isMock) {
                "Demo item — details unavailable (never loads fake IDs)."
            } else {
                "Missing provider URL — cannot open details."
            }
        }
    }

    fun onContinueWatchingClick(item: TvMediaItem) {
        val classification = TvContinueWatchingClassifier.classifyMediaItem(item)
        when (classification.clazz) {
            TvCwClass.NotSafelyPlayable -> {
                notice = classification.reason
            }
            TvCwClass.DirectPlayable -> {
                val request = TvContinueWatchingClassifier.moviePlaybackRequest(item)
                if (request == null || request.isMock) {
                    notice = "Demo item — never becomes a real TvPlaybackRequest."
                } else {
                    notice = null
                    onPlaybackRequest(request)
                }
            }
            TvCwClass.PlayableAfterDetails -> {
                val ref = TvContentRef.fromMediaItem(item)
                if (ref == null) {
                    notice = "Missing provider URL — cannot open details."
                    return
                }
                val hint = item.resumeHint
                val seriesTypes = setOf("TvSeries", "Anime", "Cartoon", "AsianDrama", "OVA")
                val isSeriesFamily = item.typeLabel in seriesTypes
                if (isSeriesFamily && hint != null && hint.hasExactEpisode) {
                    if (resolvingCwId != null) return
                    resolvingCwId = item.id
                    notice = "Resuming…"
                    scope.launch {
                        val result = try {
                            withContext(Dispatchers.IO) {
                                resumeResolver.resolveSeriesResume(ref, hint)
                            }
                        } catch (t: Throwable) {
                            TvContinueWatchingResume.ResolveResult.OpenDetails(
                                ref,
                                t.message ?: "Failed to resume — open Details.",
                            )
                        } finally {
                            resolvingCwId = null
                        }
                        when (result) {
                            is TvContinueWatchingResume.ResolveResult.Playback -> {
                                if (result.request.isMock) {
                                    notice = "Demo item — never becomes a real TvPlaybackRequest."
                                } else {
                                    notice = null
                                    onPlaybackRequest(result.request)
                                }
                            }
                            is TvContinueWatchingResume.ResolveResult.OpenDetails -> {
                                notice = result.message
                                onOpenDetails(result.ref.copy(resumeHint = hint))
                            }
                            is TvContinueWatchingResume.ResolveResult.Unavailable -> {
                                notice = result.message
                            }
                        }
                    }
                } else {
                    notice = null
                    onOpenDetails(ref)
                }
            }
        }
    }

    fun onRailItemClick(railId: String, item: TvMediaItem) {
        if (railId == TvRailIds.CONTINUE) {
            onContinueWatchingClick(item)
        } else {
            openDetailsOrNotice(item)
        }
    }

    when (val state = uiState) {
        is TvHomeUiState.Loading -> TvHomeLoadingPane(modifier)
        is TvHomeUiState.Content -> TvHomeContentPane(
            catalog = state.catalog,
            focusState = focusState,
            notice = notice,
            onOpenItem = ::onRailItemClick,
            onWatchNowStub = { /* Phase 4: clean stub — no player */ },
            modifier = modifier,
        )
        is TvHomeUiState.Empty -> TvHomeStatusPane(
            title = "Nothing here",
            body = buildString {
                append(state.message)
                state.providerName?.let { append("\nProvider: $it") }
                append("\n\nRetry after plugins load, or open the explicit demo catalog.")
            },
            primaryLabel = "Retry",
            onPrimary = { viewModel.onAction(TvHomeAction.Retry) },
            secondaryLabel = "Load demo catalog",
            onSecondary = { viewModel.onAction(TvHomeAction.UseMockFallback) },
            modifier = modifier,
        )
        is TvHomeUiState.Error -> TvHomeStatusPane(
            title = "Couldn't load Home",
            body = state.message +
                if (state.canUseMockFallback) {
                    "\n\nRetry when a homepage provider is ready, or load the demo catalog (explicit fallback)."
                } else {
                    ""
                },
            primaryLabel = "Retry",
            onPrimary = { viewModel.onAction(TvHomeAction.Retry) },
            secondaryLabel = if (state.canUseMockFallback) "Load demo catalog" else null,
            onSecondary = if (state.canUseMockFallback) {
                { viewModel.onAction(TvHomeAction.UseMockFallback) }
            } else {
                null
            },
            modifier = modifier,
        )
    }
}

@Composable
private fun TvHomeContentPane(
    catalog: TvHomeCatalog,
    focusState: TvHomeFocusState,
    notice: String?,
    onOpenItem: (railId: String, item: TvMediaItem) -> Unit,
    onWatchNowStub: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val watchFocusRequester = remember { FocusRequester() }
    var pendingRestoreRailId by remember { mutableStateOf<String?>(null) }
    val visibleRails = remember(catalog.rails) { catalog.rails.filter { it.items.isNotEmpty() } }

    LaunchedEffect(catalog) {
        focusState.pruneTo(catalog)
    }

    // Composition-enter: first visit → hero; return → rail restore.
    LaunchedEffect(Unit) {
        if (!focusState.initialHeroFocusDone) {
            runCatching { watchFocusRequester.requestFocus() }
            focusState.initialHeroFocusDone = true
        } else {
            pendingRestoreRailId = focusState.lastFocusedRailId
        }
    }

    // After GeneratorPlayer pops (Activity ON_RESUME): restore CW / last rail focus; neighbor if gone (pruneTo).
    TvOnResume {
        val railId = focusState.lastFocusedRailId
        if (railId != null) {
            pendingRestoreRailId = railId
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        if (catalog.usingMockFallback || catalog.rails.any { it.isMock } || catalog.hero.isMock) {
            item(key = "mock-banner") {
                val parts = buildList {
                    if (catalog.usingMockFallback) add("Full demo catalog (explicit fallback)")
                    else {
                        if (catalog.hero.isMock) add("Hero is demo")
                        catalog.rails.filter { it.isMock }.forEach { add("${it.title} is demo") }
                    }
                    catalog.providerName?.let { add("Provider: $it") }
                }
                Text(
                    text = parts.joinToString(" · "),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
        if (!notice.isNullOrBlank()) {
            item(key = "cw-notice") {
                Text(
                    text = notice,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
        item(key = "hero") {
            TvHeroSection(
                hero = catalog.hero,
                watchFocusRequester = watchFocusRequester,
                detailsEnabled = TvContentRef.fromMediaItem(catalog.hero) != null,
                onWatchNow = onWatchNowStub,
                onDetails = { onOpenItem("hero", catalog.hero) },
            )
        }
        itemsIndexed(visibleRails, key = { _, rail -> rail.id }) { _, rail ->
            val shouldRestore = pendingRestoreRailId == rail.id
            TvContentRail(
                rail = rail,
                lastFocusedIndex = focusState.indexFor(rail.id),
                onFocusedIndexChanged = { index -> focusState.update(rail.id, index) },
                onItemClick = { item -> onOpenItem(rail.id, item) },
                restoreFocus = shouldRestore,
                onRestoreConsumed = {
                    if (pendingRestoreRailId == rail.id) {
                        pendingRestoreRailId = null
                    }
                },
            )
        }
    }
}

@Composable
private fun TvHomeLoadingPane(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Loading catalog…",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TvHomeStatusPane(
    title: String,
    body: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String?,
    onSecondary: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val retryFocus = remember { FocusRequester() }
    LaunchedEffect(title) {
        runCatching { retryFocus.requestFocus() }
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(48.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = onPrimary,
            modifier = Modifier.focusRequester(retryFocus),
            scale = ButtonDefaults.scale(focusedScale = TvFocusScale.ButtonFocused),
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
