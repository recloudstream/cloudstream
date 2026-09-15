package com.lagradost.cloudstream3.tv.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.lagradost.cloudstream3.tv.components.TvConfirmDialog
import com.lagradost.cloudstream3.tv.components.TvFocusScale
import com.lagradost.cloudstream3.tv.components.TvOnResume
import com.lagradost.cloudstream3.tv.model.TvAvailabilityKind
import com.lagradost.cloudstream3.tv.model.TvContentRail as TvRailModel
import com.lagradost.cloudstream3.tv.model.TvContentRef
import com.lagradost.cloudstream3.tv.model.TvHeroWatchNow
import com.lagradost.cloudstream3.tv.model.TvHeroWatchResult
import com.lagradost.cloudstream3.tv.model.TvHomeAction
import com.lagradost.cloudstream3.tv.model.TvHomeCatalog
import com.lagradost.cloudstream3.tv.model.TvHomeEvent
import com.lagradost.cloudstream3.tv.model.TvHomeUiState
import com.lagradost.cloudstream3.tv.model.TvMediaItem
import com.lagradost.cloudstream3.tv.model.TvPlaybackRequest
import com.lagradost.cloudstream3.tv.model.TvRailIds
import kotlinx.coroutines.flow.Flow

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

    /** After CW remove — keep focus on neighbor index (coerced by [pruneTo]). */
    fun preferNeighborAfterRemove(railId: String, removedIndex: Int) {
        val next = removedIndex.coerceAtLeast(0)
        railFocusIndices[railId] = next
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

private data class CwRemoveCandidate(
    val item: TvMediaItem,
    val parentId: Int,
    val index: Int,
)

/** Result of validating a CW long-press remove request. */
private sealed interface CwRemoveRequest {
    data class Confirm(val candidate: CwRemoveCandidate) : CwRemoveRequest
    data class Blocked(val notice: String) : CwRemoveRequest
}

private fun detailsUnavailableNotice(item: TvMediaItem): String =
    if (item.isMock) {
        "${TvAvailabilityKind.PlaybackUnavailable.label}: Demo item — details unavailable (never loads fake IDs)."
    } else {
        "${TvAvailabilityKind.Unavailable.label}: Missing provider URL — cannot open details."
    }

private fun resolveCwRemoveRequest(item: TvMediaItem, index: Int): CwRemoveRequest {
    if (item.isMock) {
        return CwRemoveRequest.Blocked(
            "${TvAvailabilityKind.PlaybackUnavailable.label}: Demo CW cannot be removed from real history.",
        )
    }
    val parentId = item.resumeHint?.parentId
    if (parentId == null) {
        return CwRemoveRequest.Blocked(
            "${TvAvailabilityKind.Unavailable.label}: Missing parentId — cannot remove (no fake remove).",
        )
    }
    return CwRemoveRequest.Confirm(CwRemoveCandidate(item, parentId, index))
}

private fun heroWatchLabel(hero: TvMediaItem): String =
    when (TvHeroWatchNow.resolve(hero)) {
        is TvHeroWatchResult.Play -> "Watch Now"
        is TvHeroWatchResult.ResolveSeries -> "Watch Now"
        is TvHeroWatchResult.OpenDetails -> "Details"
        is TvHeroWatchResult.Demo -> "Demo"
        is TvHeroWatchResult.Unavailable -> "Unavailable"
    }

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
    var removeCandidate by remember { mutableStateOf<CwRemoveCandidate?>(null) }

    LaunchedEffect(Unit) { viewModel.onAction(TvHomeAction.RefreshContinueWatching) }
    TvOnResume {
        viewModel.onAction(TvHomeAction.RefreshContinueWatching)
    }

    TvHomeEventEffects(
        events = viewModel.events,
        onPlaybackRequest = onPlaybackRequest,
        onOpenDetails = onOpenDetails,
        onNotice = { notice = it },
        onClearNotice = { notice = null },
    )

    Box(modifier = modifier.fillMaxSize()) {
        TvHomeStateHost(
            uiState = uiState,
            focusState = focusState,
            notice = notice,
            onOpenDetails = onOpenDetails,
            onNotice = { notice = it },
            onClearNotice = { notice = null },
            onAction = viewModel::onAction,
            onRequestRemoveCw = { item, index ->
                when (val req = resolveCwRemoveRequest(item, index)) {
                    is CwRemoveRequest.Confirm -> removeCandidate = req.candidate
                    is CwRemoveRequest.Blocked -> notice = req.notice
                }
            },
        )

        removeCandidate?.let { candidate ->
            TvHomeCwRemoveDialog(
                candidate = candidate,
                focusState = focusState,
                onDismiss = { removeCandidate = null },
                onConfirmRemove = { parentId ->
                    viewModel.onAction(TvHomeAction.RemoveContinueWatching(parentId))
                    notice = null
                },
            )
        }
    }
}

@Composable
private fun TvHomeEventEffects(
    events: Flow<TvHomeEvent>,
    onPlaybackRequest: (TvPlaybackRequest) -> Unit,
    onOpenDetails: (TvContentRef) -> Unit,
    onNotice: (String) -> Unit,
    onClearNotice: () -> Unit,
) {
    LaunchedEffect(events) {
        events.collect { event ->
            when (event) {
                is TvHomeEvent.Play -> {
                    onClearNotice()
                    onPlaybackRequest(event.request)
                }
                is TvHomeEvent.OpenDetails -> {
                    if (event.clearNotice) onClearNotice()
                    onOpenDetails(event.ref)
                }
                is TvHomeEvent.Notice -> onNotice(event.message)
            }
        }
    }
}

@Composable
private fun TvHomeStateHost(
    uiState: TvHomeUiState,
    focusState: TvHomeFocusState,
    notice: String?,
    onOpenDetails: (TvContentRef) -> Unit,
    onNotice: (String) -> Unit,
    onClearNotice: () -> Unit,
    onAction: (TvHomeAction) -> Unit,
    onRequestRemoveCw: (TvMediaItem, Int) -> Unit,
) {
    when (uiState) {
        is TvHomeUiState.Loading -> TvHomeLoadingPane(Modifier.fillMaxSize())
        is TvHomeUiState.Content -> TvHomeContentPane(
            catalog = uiState.catalog,
            focusState = focusState,
            notice = notice,
            onOpenItem = { railId, item ->
                handleRailItemClick(
                    railId = railId,
                    item = item,
                    onAction = onAction,
                    onOpenDetails = onOpenDetails,
                    onNotice = onNotice,
                    onClearNotice = onClearNotice,
                )
            },
            onWatchNow = { onAction(TvHomeAction.HeroWatchNow(uiState.catalog.hero)) },
            onCwLongClick = onRequestRemoveCw,
            modifier = Modifier.fillMaxSize(),
        )
        is TvHomeUiState.Empty -> TvHomeEmptyPane(
            state = uiState,
            onRetry = { onAction(TvHomeAction.Retry) },
            onUseMock = { onAction(TvHomeAction.UseMockFallback) },
            modifier = Modifier.fillMaxSize(),
        )
        is TvHomeUiState.Error -> TvHomeErrorPane(
            state = uiState,
            onRetry = { onAction(TvHomeAction.Retry) },
            onUseMock = { onAction(TvHomeAction.UseMockFallback) },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private fun handleRailItemClick(
    railId: String,
    item: TvMediaItem,
    onAction: (TvHomeAction) -> Unit,
    onOpenDetails: (TvContentRef) -> Unit,
    onNotice: (String) -> Unit,
    onClearNotice: () -> Unit,
) {
    if (railId == TvRailIds.CONTINUE) {
        onAction(TvHomeAction.ResumeContinueWatching(item))
        return
    }
    val ref = TvContentRef.fromMediaItem(item)
    if (ref != null) {
        onClearNotice()
        onOpenDetails(ref)
    } else {
        onNotice(detailsUnavailableNotice(item))
    }
}

@Composable
private fun TvHomeEmptyPane(
    state: TvHomeUiState.Empty,
    onRetry: () -> Unit,
    onUseMock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val body = buildString {
        append(state.message)
        state.providerName?.let { append("\nProvider: $it") }
        append("\n\nReal empty catalog — not demo. Retry after plugins load, or open the explicit demo catalog.")
    }
    TvHomeStatusPane(
        title = state.availability.label,
        body = body,
        primaryLabel = "Retry",
        onPrimary = onRetry,
        secondaryLabel = "Load demo catalog",
        onSecondary = onUseMock,
        modifier = modifier,
    )
}

@Composable
private fun TvHomeErrorPane(
    state: TvHomeUiState.Error,
    onRetry: () -> Unit,
    onUseMock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val body = if (state.canUseMockFallback) {
        state.message +
            "\n\nRetry when a homepage provider is ready, or load the demo catalog (explicit fallback — never silent)."
    } else {
        state.message
    }
    TvHomeStatusPane(
        title = state.availability.label,
        body = body,
        primaryLabel = "Retry",
        onPrimary = onRetry,
        secondaryLabel = if (state.canUseMockFallback) "Load demo catalog" else null,
        onSecondary = if (state.canUseMockFallback) onUseMock else null,
        modifier = modifier,
    )
}

@Composable
private fun TvHomeCwRemoveDialog(
    candidate: CwRemoveCandidate,
    focusState: TvHomeFocusState,
    onDismiss: () -> Unit,
    onConfirmRemove: (parentId: Int) -> Unit,
) {
    TvConfirmDialog(
        title = "Remove from Continue Watching?",
        message = "Remove \"${candidate.item.title}\" from continue watching history? This uses the existing resume remove API.",
        confirmLabel = "Remove",
        onDismiss = onDismiss,
        onConfirm = {
            val idx = candidate.index
            onDismiss()
            focusState.preferNeighborAfterRemove(TvRailIds.CONTINUE, idx)
            onConfirmRemove(candidate.parentId)
            // Restore focus to CW neighbor after list updates.
            focusState.lastFocusedRailId = TvRailIds.CONTINUE
        },
    )
}

@Composable
private fun TvHomeContentPane(
    catalog: TvHomeCatalog,
    focusState: TvHomeFocusState,
    notice: String?,
    onOpenItem: (railId: String, item: TvMediaItem) -> Unit,
    onWatchNow: () -> Unit,
    onCwLongClick: (TvMediaItem, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val watchFocusRequester = remember { FocusRequester() }
    var pendingRestoreRailId by remember { mutableStateOf<String?>(null) }
    val visibleRails = remember(catalog.rails) { catalog.rails.filter { it.items.isNotEmpty() } }

    TvHomeContentFocusEffects(
        catalog = catalog,
        focusState = focusState,
        visibleRails = visibleRails,
        watchFocusRequester = watchFocusRequester,
        onPendingRestoreRailId = { pendingRestoreRailId = it },
    )

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        tvHomeBannerItems(catalog = catalog, notice = notice)
        item(key = "hero") {
            TvHomeHeroBlock(
                hero = catalog.hero,
                watchFocusRequester = watchFocusRequester,
                onWatchNow = onWatchNow,
                onDetails = { onOpenItem("hero", catalog.hero) },
            )
        }
        itemsIndexed(visibleRails, key = { _, rail -> rail.id }) { _, rail ->
            TvHomeRailBlock(
                rail = rail,
                focusState = focusState,
                restoreFocus = pendingRestoreRailId == rail.id,
                onOpenItem = onOpenItem,
                onCwLongClick = onCwLongClick,
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
private fun TvHomeContentFocusEffects(
    catalog: TvHomeCatalog,
    focusState: TvHomeFocusState,
    visibleRails: List<TvRailModel>,
    watchFocusRequester: FocusRequester,
    onPendingRestoreRailId: (String?) -> Unit,
) {
    LaunchedEffect(catalog) {
        focusState.pruneTo(catalog)
        // After remove refresh, restore CW focus if that was the last rail.
        if (focusState.lastFocusedRailId == TvRailIds.CONTINUE &&
            visibleRails.any { it.id == TvRailIds.CONTINUE }
        ) {
            onPendingRestoreRailId(TvRailIds.CONTINUE)
        }
    }

    LaunchedEffect(Unit) {
        if (!focusState.initialHeroFocusDone) {
            runCatching { watchFocusRequester.requestFocus() }
            focusState.initialHeroFocusDone = true
        } else {
            onPendingRestoreRailId(focusState.lastFocusedRailId)
        }
    }

    TvOnResume {
        val railId = focusState.lastFocusedRailId
        if (railId != null) {
            onPendingRestoreRailId(railId)
        }
    }
}

private fun LazyListScope.tvHomeBannerItems(catalog: TvHomeCatalog, notice: String?) {
    item(key = "home-state-banner") {
        TvHomeStateBanner(catalog = catalog)
    }
    if (catalog.usingMockFallback || catalog.rails.any { it.isMock } || catalog.hero.isMock) {
        item(key = "mock-banner") {
            TvHomeMockBanner(catalog = catalog)
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
}

@Composable
private fun TvHomeStateBanner(catalog: TvHomeCatalog) {
    val stateLabel = if (catalog.usingMockFallback) {
        "Home · Demo catalog (explicit)"
    } else {
        "Home · Real catalog"
    }
    Text(
        text = buildList {
            add(stateLabel)
            catalog.providerName?.let { add("Provider: $it") }
            if (!catalog.usingMockFallback && catalog.rails.none { it.id == TvRailIds.CONTINUE }) {
                add("CW empty")
            }
        }.joinToString(" · "),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 8.dp),
    )
}

@Composable
private fun TvHomeMockBanner(catalog: TvHomeCatalog) {
    val parts = buildList {
        if (catalog.usingMockFallback) {
            add("Full demo catalog (explicit fallback)")
        } else {
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

@Composable
private fun TvHomeHeroBlock(
    hero: TvMediaItem,
    watchFocusRequester: FocusRequester,
    onWatchNow: () -> Unit,
    onDetails: () -> Unit,
) {
    val heroRef = TvContentRef.fromMediaItem(hero)
    TvHeroSection(
        hero = hero,
        watchFocusRequester = watchFocusRequester,
        detailsEnabled = heroRef != null,
        watchLabel = heroWatchLabel(hero),
        onWatchNow = onWatchNow,
        onDetails = onDetails,
    )
}

@Composable
private fun TvHomeRailBlock(
    rail: TvRailModel,
    focusState: TvHomeFocusState,
    restoreFocus: Boolean,
    onOpenItem: (railId: String, item: TvMediaItem) -> Unit,
    onCwLongClick: (TvMediaItem, Int) -> Unit,
    onRestoreConsumed: () -> Unit,
) {
    val longClick = cwLongClickHandler(
        rail = rail,
        focusState = focusState,
        onCwLongClick = onCwLongClick,
    )
    TvContentRail(
        rail = rail,
        lastFocusedIndex = focusState.indexFor(rail.id),
        onFocusedIndexChanged = { index -> focusState.update(rail.id, index) },
        onItemClick = { item -> onOpenItem(rail.id, item) },
        onItemLongClick = longClick,
        restoreFocus = restoreFocus,
        onRestoreConsumed = onRestoreConsumed,
    )
}

private fun cwLongClickHandler(
    rail: TvRailModel,
    focusState: TvHomeFocusState,
    onCwLongClick: (TvMediaItem, Int) -> Unit,
): ((TvMediaItem) -> Unit)? {
    if (rail.id != TvRailIds.CONTINUE || rail.isMock) return null
    return { item ->
        val idx = rail.items.indexOfFirst { it.id == item.id }
            .takeIf { it >= 0 } ?: focusState.indexFor(rail.id)
        onCwLongClick(item, idx)
    }
}

@Composable
private fun TvHomeLoadingPane(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Loading…",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Home · ${TvAvailabilityKind.Available.label} pending",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
