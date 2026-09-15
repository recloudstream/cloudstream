package com.lagradost.cloudstream3.tv.watchlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.tv.data.TvWatchlistRepository
import com.lagradost.cloudstream3.tv.model.TvWatchlistAction
import com.lagradost.cloudstream3.tv.model.TvWatchlistUiState
import com.lagradost.cloudstream4.compose.ActionHandler
import com.lagradost.cloudstream4.compose.DefaultStateContainer
import com.lagradost.cloudstream4.compose.StateContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Lifecycle-aware Watchlist / Library state (Local list only, read-only).
 * Loads on enter / resume / Retry — never polls, never writes DataStore.
 */
class TvWatchlistViewModel(
    private val repository: TvWatchlistRepository = TvWatchlistRepository(),
) : ViewModel(),
    StateContainer<TvWatchlistUiState> by DefaultStateContainer(TvWatchlistUiState.Loading),
    ActionHandler<TvWatchlistAction> {

    private var loadJob: Job? = null

    override fun onAction(action: TvWatchlistAction) {
        when (action) {
            TvWatchlistAction.Retry,
            TvWatchlistAction.Refresh,
            -> load()
        }
    }

    private fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            updateState { TvWatchlistUiState.Loading }
            val result = try {
                withContext(Dispatchers.IO) { repository.loadWatchlist() }
            } catch (t: Throwable) {
                logError(t)
                TvWatchlistRepository.LoadResult.Failure(
                    t.message ?: "Unexpected error reading Library",
                )
            }
            updateState {
                when (result) {
                    is TvWatchlistRepository.LoadResult.Success ->
                        TvWatchlistUiState.Content(result.catalog)

                    is TvWatchlistRepository.LoadResult.Empty ->
                        TvWatchlistUiState.Empty(accountName = result.catalog.accountName)

                    is TvWatchlistRepository.LoadResult.Failure ->
                        TvWatchlistUiState.Error(result.message)
                }
            }
        }
    }
}
