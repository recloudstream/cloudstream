package com.lagradost.cloudstream3.tv.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.tv.data.TvSearchRepository
import com.lagradost.cloudstream3.tv.model.TvSearchAction
import com.lagradost.cloudstream3.tv.model.TvSearchUiState
import com.lagradost.cloudstream4.compose.ActionHandler
import com.lagradost.cloudstream4.compose.DefaultStateContainer
import com.lagradost.cloudstream4.compose.StateContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

/**
 * Lifecycle-aware Search state holder (MVI / [StateContainer]).
 * Explicit submit only — no per-keystroke search (mobile SearchFragment submits on IME Done;
 * DebounceQuery exists in shared compose but is unused by production search).
 *
 * Cancels superseded searches via job cancel + generation counter so stale results never overwrite.
 * Query text lives in [queryText] (separate from result UiState) so Idle/Loading/Content all
 * preserve the field when returning from Details.
 */
class TvSearchViewModel(
    private val repository: TvSearchRepository = TvSearchRepository(),
) : ViewModel(),
    StateContainer<TvSearchUiState> by DefaultStateContainer(TvSearchUiState.Idle),
    ActionHandler<TvSearchAction> {

    /** Draft query shown in the search field — survives Details overlay (Activity ViewModelStore). */
    private val _queryText = kotlinx.coroutines.flow.MutableStateFlow("")
    val queryText: kotlinx.coroutines.flow.StateFlow<String> = _queryText

    private var searchJob: Job? = null
    private val searchGeneration = AtomicInteger(0)

    override fun onAction(action: TvSearchAction) {
        when (action) {
            is TvSearchAction.UpdateQuery -> {
                _queryText.value = action.query
            }
            TvSearchAction.Submit -> submitSearch(_queryText.value)
            TvSearchAction.Clear -> clearSearch()
            TvSearchAction.Retry -> {
                val q = when (val s = state.value) {
                    is TvSearchUiState.Error -> s.query
                    is TvSearchUiState.Empty -> s.query
                    is TvSearchUiState.Content -> s.catalog.query
                    is TvSearchUiState.Loading -> s.query
                    TvSearchUiState.Idle -> _queryText.value
                }
                if (q.isNotBlank()) {
                    _queryText.value = q
                    submitSearch(q)
                }
            }
        }
    }

    private fun clearSearch() {
        searchJob?.cancel()
        searchGeneration.incrementAndGet()
        _queryText.value = ""
        updateState { TvSearchUiState.Idle }
    }

    private fun submitSearch(raw: String) {
        val query = raw.trim()
        if (query.length <= 1) {
            updateState {
                TvSearchUiState.Error(
                    query = query,
                    message = "Enter at least 2 characters, then press Search.",
                )
            }
            return
        }

        searchJob?.cancel()
        val generation = searchGeneration.incrementAndGet()
        searchJob = viewModelScope.launch {
            updateState { TvSearchUiState.Loading(query) }
            val result = try {
                withContext(Dispatchers.IO) {
                    repository.search(query) { searchGeneration.get() == generation }
                }
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                logError(t)
                TvSearchRepository.SearchResult.Failure(
                    query = query,
                    message = t.message ?: "Unexpected search error",
                )
            }

            // Stale / cancelled — do not overwrite newer state.
            if (searchGeneration.get() != generation) return@launch

            updateState {
                when (result) {
                    is TvSearchRepository.SearchResult.Success ->
                        TvSearchUiState.Content(result.catalog)

                    is TvSearchRepository.SearchResult.Empty ->
                        TvSearchUiState.Empty(
                            query = result.query,
                            message = buildString {
                                append("No results for \"${result.query}\".")
                                if (result.failedProviderCount > 0) {
                                    append(" (${result.failedProviderCount} provider(s) failed)")
                                }
                            },
                            failedProviderCount = result.failedProviderCount,
                            providerCount = result.providerCount,
                        )

                    is TvSearchRepository.SearchResult.Failure -> {
                        if (result.message == "Search cancelled") {
                            // Keep prior non-loading state if cancelled mid-flight by a newer search.
                            this
                        } else {
                            TvSearchUiState.Error(result.query, result.message)
                        }
                    }
                }
            }
        }
    }
}
