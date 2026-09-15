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
 * Loads once from [TvHomeRepository]; no duplicate fetches on recomposition.
 */
class TvHomeViewModel(
    private val repository: TvHomeRepository = TvHomeRepository(),
) : ViewModel(),
    StateContainer<TvHomeUiState> by DefaultStateContainer(TvHomeUiState.Loading),
    ActionHandler<TvHomeAction> {

    private var loadJob: Job? = null

    init {
        loadCatalog()
    }

    override fun onAction(action: TvHomeAction) {
        when (action) {
            TvHomeAction.Retry -> loadCatalog()
            TvHomeAction.UseMockFallback -> {
                loadJob?.cancel()
                updateState {
                    TvHomeUiState.Content(repository.mockFallbackCatalog())
                }
            }
        }
    }

    private fun loadCatalog() {
        loadJob?.cancel()
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
}
