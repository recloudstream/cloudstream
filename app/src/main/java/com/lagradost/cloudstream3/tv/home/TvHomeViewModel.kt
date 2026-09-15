package com.lagradost.cloudstream3.tv.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.tv.data.TvHomeRepository
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
 * Loads once from [TvHomeRepository]; Continue Watching refreshed on resume (read-only).
 * No duplicate DataStore reads on recomposition — only enter / resume / Retry.
 */
class TvHomeViewModel(
    private val repository: TvHomeRepository = TvHomeRepository(),
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
                        TvHomeUiState.Empty(result.providerName)

                    is TvHomeRepository.LoadResult.Failure ->
                        TvHomeUiState.Error(result.message, canUseMockFallback = true)
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
                return@launch
            }
            // Only apply if still Content with same provider catalog (avoid clobbering Retry).
            val latest = state.value
            if (latest is TvHomeUiState.Content && !latest.catalog.usingMockFallback) {
                updateState { TvHomeUiState.Content(refreshed) }
            }
        }
    }
}
