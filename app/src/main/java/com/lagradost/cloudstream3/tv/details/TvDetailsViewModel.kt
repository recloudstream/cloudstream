package com.lagradost.cloudstream3.tv.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.tv.data.TvDetailsRepository
import com.lagradost.cloudstream3.tv.model.TvContentRef
import com.lagradost.cloudstream3.tv.model.TvDetailsAction
import com.lagradost.cloudstream3.tv.model.TvDetailsUiState
import com.lagradost.cloudstream4.compose.ActionHandler
import com.lagradost.cloudstream4.compose.DefaultStateContainer
import com.lagradost.cloudstream4.compose.StateContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Lifecycle-aware Details state holder (MVI / [StateContainer]).
 * Loads once per [TvContentRef]; no duplicate fetches on recomposition.
 * Watch Now is a clean stub callback surface — no player wiring.
 */
class TvDetailsViewModel(
    private val repository: TvDetailsRepository = TvDetailsRepository(),
) : ViewModel(),
    StateContainer<TvDetailsUiState> by DefaultStateContainer(TvDetailsUiState.Loading()),
    ActionHandler<TvDetailsAction> {

    private var loadJob: Job? = null
    private var boundRef: TvContentRef? = null

    /** Invoked by the UI host for the stub Watch Now action (Phase 4: no player). */
    var onWatchNowStub: (() -> Unit)? = null

    fun bind(ref: TvContentRef) {
        if (boundRef == ref && state.value !is TvDetailsUiState.Error) {
            // Same ref and not in error — keep existing Content / Loading.
            if (state.value is TvDetailsUiState.Content || state.value is TvDetailsUiState.Loading) {
                return
            }
        }
        boundRef = ref
        loadDetails(ref)
    }

    override fun onAction(action: TvDetailsAction) {
        when (action) {
            TvDetailsAction.Retry -> boundRef?.let { loadDetails(it) }
            TvDetailsAction.Back -> Unit // navigation owned by shell
            TvDetailsAction.WatchNow -> onWatchNowStub?.invoke()
        }
    }

    private fun loadDetails(ref: TvContentRef) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            updateState {
                TvDetailsUiState.Loading(titleHint = ref.title.takeIf { it.isNotBlank() })
            }
            val result = try {
                withContext(Dispatchers.IO) { repository.load(ref) }
            } catch (t: Throwable) {
                logError(t)
                TvDetailsRepository.LoadResult.Failure(
                    t.message ?: "Unexpected error loading details",
                )
            }
            updateState {
                when (result) {
                    is TvDetailsRepository.LoadResult.Success ->
                        TvDetailsUiState.Content(result.details)

                    is TvDetailsRepository.LoadResult.Failure ->
                        TvDetailsUiState.Error(
                            message = result.message,
                            titleHint = ref.title.takeIf { it.isNotBlank() },
                        )
                }
            }
        }
    }
}
