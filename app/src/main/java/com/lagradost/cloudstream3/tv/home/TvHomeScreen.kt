package com.lagradost.cloudstream3.tv.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.tv.model.TvMockCatalog
import com.lagradost.cloudstream3.tv.model.TvMockHomeState

/**
 * Hoisted Home focus memory — survives destination switches in [TvNavigationShell].
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
}

@Composable
fun rememberTvHomeFocusState(): TvHomeFocusState = remember { TvHomeFocusState() }

@Composable
fun TvHomeScreen(
    focusState: TvHomeFocusState,
    modifier: Modifier = Modifier,
    state: TvMockHomeState = TvMockCatalog.home,
) {
    val watchFocusRequester = remember { FocusRequester() }
    // One-shot rail id to restore when re-entering Home after another destination.
    var pendingRestoreRailId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (!focusState.initialHeroFocusDone) {
            runCatching { watchFocusRequester.requestFocus() }
            focusState.initialHeroFocusDone = true
        } else {
            pendingRestoreRailId = focusState.lastFocusedRailId
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        item(key = "hero") {
            TvHeroSection(
                hero = state.hero,
                watchFocusRequester = watchFocusRequester,
            )
        }
        itemsIndexed(state.rails, key = { _, rail -> rail.id }) { _, rail ->
            val shouldRestore = pendingRestoreRailId == rail.id
            TvContentRail(
                rail = rail,
                lastFocusedIndex = focusState.indexFor(rail.id),
                onFocusedIndexChanged = { index -> focusState.update(rail.id, index) },
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
