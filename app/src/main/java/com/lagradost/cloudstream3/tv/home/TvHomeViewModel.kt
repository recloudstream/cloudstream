package com.lagradost.cloudstream3.tv.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.tv.data.TvContinueWatchingRepository
import com.lagradost.cloudstream3.tv.data.TvHomeRepository
import com.lagradost.cloudstream3.tv.model.TvAvailabilityClassifier
import com.lagradost.cloudstream3.tv.model.TvAvailabilityKind
import com.lagradost.cloudstream3.tv.model.TvHomeAction
import com.lagradost.cloudstream3.tv.model.TvHomeUiState
import com.lagradost.cloudstream4.compose.ActionHandler
import com.lagradost.cloudstream4.compose.DefaultStateContainer
import com.lagradost.cloudstream4.compose.StateContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Lifecycle-aware Home state holder (MVI / [StateContainer]).
 * Loads once from [TvHomeRepository]; Continue Watching refreshed on resume.
 * Phase 10: remove CW via existing removeLastWatched; never silent-fail → demo.
 */
class TvHomeViewModel(
    private val repository: TvHomeRepository = TvHomeRepository(),
    private val continueWatchingRepository: TvContinueWatchingRepository = TvContinueWatchingRepository(),
) : ViewModel(),
    StateContainer<TvHomeUiState> by DefaultStateContainer(TvHomeUiState.Loading),
    ActionHandler<TvHomeAction> {

    private var loadJob: Job? = null
    private var cwRefreshJob: Job? = null

    init {
        loadCatalog()
    }

    override fun onAction(action: TvHomeAction) {
        when (action) {
            TvHomeAction.Retry -> loadCatalog()
            TvHomeAction.UseMockFallback -> {
                loadJob?.cancel()
                cwRefreshJob?.cancel()
                updateState {
                    TvHomeUiState.Content(repository.mockFallbackCatalog())
                }
            }
            TvHomeAction.RefreshContinueWatching -> refreshContinueWatching()
            is TvHomeAction.RemoveContinueWatching -> removeContinueWatching(action.parentId)
        }
    }

    private fun loadCatalog() {
        loadJob?.cancel()
        cwRefreshJob?.cancel()
        loadJob = viewModelScope.launch {
            updateState { TvHomeUiState.Loading }
            val result = try {
                withContext(Dispatchers.IO) { repository.loadHome() }
            } catch (t: Throwable) {
                logError(t)
                TvHomeRepository.LoadResult.Failure(
                    t.message ?: "Unexpected error loading home catalog",
                )
            }
            updateState {
                when (result) {
                    is TvHomeRepository.LoadResult.Success ->
                        TvHomeUiState.Content(result.catalog)

                    is TvHomeRepository.LoadResult.Empty ->
                        TvHomeUiState.Empty(
                            providerName = result.providerName,
                            availability = TvAvailabilityKind.Unavailable,
                        )

                    is TvHomeRepository.LoadResult.Failure -> {
                        val status = TvAvailabilityClassifier.fromHomeFailure(result.message)
                        TvHomeUiState.Error(
                            message = result.message,
                            canUseMockFallback = true,
                            availability = status.kind,
                        )
                    }
                }
            }
        }
    }

    /** Read-only CW rail swap — skips when showing mock fallback or non-Content. */
    private fun refreshContinueWatching() {
        val current = state.value
        if (current !is TvHomeUiState.Content) return
        if (current.catalog.usingMockFallback) return
        cwRefreshJob?.cancel()
        cwRefreshJob = viewModelScope.launch {
            val refreshed = try {
                withContext(Dispatchers.IO) {
                    repository.withRefreshedContinueWatching(current.catalog)
                }
            } catch (t: Throwable) {
                logError(t)
                // CW refresh failure must not swap catalog into demo.
                return@launch
            }
            val latest = state.value
            if (latest is TvHomeUiState.Content && !latest.catalog.usingMockFallback) {
                updateState { TvHomeUiState.Content(refreshed) }
            }
        }
    }

    /**
     * Phase 10 — existing [DataStoreHelper.removeLastWatched] via repository.
     * Then re-read CW rail only (no homepage network, no demo swap).
     */
    private fun removeContinueWatching(parentId: Int) {
        val current = state.value
        if (current !is TvHomeUiState.Content) return
        if (current.catalog.usingMockFallback) return
        cwRefreshJob?.cancel()
        cwRefreshJob = viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    continueWatchingRepository.removeContinueWatching(parentId)
                }
            } catch (t: Throwable) {
                logError(t)
                return@launch
            }
            val latest = state.value
            if (latest !is TvHomeUiState.Content || latest.catalog.usingMockFallback) return@launch
            val refreshed = try {
                withContext(Dispatchers.IO) {
                    repository.withRefreshedContinueWatching(latest.catalog)
                }
            } catch (t: Throwable) {
                logError(t)
                return@launch
            }
            val after = state.value
            if (after is TvHomeUiState.Content && !after.catalog.usingMockFallback) {
                updateState { TvHomeUiState.Content(refreshed) }
            }
        }
    }
}
