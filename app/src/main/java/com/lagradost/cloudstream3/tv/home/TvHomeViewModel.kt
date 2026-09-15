package com.lagradost.cloudstream3.tv.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.tv.data.TvContinueWatchingRepository
import com.lagradost.cloudstream3.tv.data.TvContinueWatchingResumeResolver
import com.lagradost.cloudstream3.tv.data.TvHomeRepository
import com.lagradost.cloudstream3.tv.model.TvAvailabilityClassifier
import com.lagradost.cloudstream3.tv.model.TvAvailabilityKind
import com.lagradost.cloudstream3.tv.model.TvContentRef
import com.lagradost.cloudstream3.tv.model.TvContinueWatchingClassifier
import com.lagradost.cloudstream3.tv.model.TvCwClass
import com.lagradost.cloudstream3.tv.model.TvHeroWatchNow
import com.lagradost.cloudstream3.tv.model.TvHeroWatchResult
import com.lagradost.cloudstream3.tv.model.TvHomeAction
import com.lagradost.cloudstream3.tv.model.TvHomeEvent
import com.lagradost.cloudstream3.tv.model.TvHomeUiState
import com.lagradost.cloudstream3.tv.model.TvMediaItem
import com.lagradost.cloudstream3.tv.model.TvResumeHint
import com.lagradost.cloudstream4.compose.ActionHandler
import com.lagradost.cloudstream4.compose.DefaultStateContainer
import com.lagradost.cloudstream4.compose.StateContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Lifecycle-aware Home state holder (MVI / [StateContainer]).
 * Loads once from [TvHomeRepository]; Continue Watching refreshed on resume.
 * Phase 9: CW / Hero series resume resolved here via [TvContinueWatchingResumeResolver]
 * (not in Composables). Phase 10: remove CW via existing removeLastWatched; never silent-fail → demo.
 */
class TvHomeViewModel(
    private val repository: TvHomeRepository = TvHomeRepository(),
    private val continueWatchingRepository: TvContinueWatchingRepository = TvContinueWatchingRepository(),
    private val resumeResolver: TvContinueWatchingResumeResolver = TvContinueWatchingResumeResolver(),
) : ViewModel(),
    StateContainer<TvHomeUiState> by DefaultStateContainer(TvHomeUiState.Loading),
    ActionHandler<TvHomeAction> {

    private var loadJob: Job? = null
    private var cwRefreshJob: Job? = null
    private var resumeJob: Job? = null

    private val _events = MutableSharedFlow<TvHomeEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<TvHomeEvent> = _events.asSharedFlow()

    init {
        loadCatalog()
    }

    override fun onAction(action: TvHomeAction) {
        when (action) {
            TvHomeAction.Retry -> loadCatalog()
            TvHomeAction.UseMockFallback -> {
                loadJob?.cancel()
                cwRefreshJob?.cancel()
                resumeJob?.cancel()
                updateState {
                    TvHomeUiState.Content(repository.mockFallbackCatalog())
                }
            }
            TvHomeAction.RefreshContinueWatching -> refreshContinueWatching()
            is TvHomeAction.RemoveContinueWatching -> removeContinueWatching(action.parentId)
            is TvHomeAction.ResumeContinueWatching -> resumeContinueWatching(action.item)
            is TvHomeAction.HeroWatchNow -> heroWatchNow(action.item)
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
                if (t is CancellationException) throw t
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
                if (t is CancellationException) throw t
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
                if (t is CancellationException) throw t
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
                if (t is CancellationException) throw t
                logError(t)
                return@launch
            }
            val after = state.value
            if (after is TvHomeUiState.Content && !after.catalog.usingMockFallback) {
                updateState { TvHomeUiState.Content(refreshed) }
            }
        }
    }

    /** Phase 9 — classify CW click; movies DirectPlay; series resolve off main thread. */
    private fun resumeContinueWatching(item: TvMediaItem) {
        val classification = TvContinueWatchingClassifier.classifyMediaItem(item)
        val availability = TvAvailabilityClassifier.fromCwClassification(classification)
        when (classification.clazz) {
            TvCwClass.NotSafelyPlayable -> {
                emitNotice("${availability.kind.label}: ${classification.reason}")
            }
            TvCwClass.DirectPlayable -> {
                val request = TvContinueWatchingClassifier.moviePlaybackRequest(item)
                if (request == null || request.isMock) {
                    emitNotice(
                        "${TvAvailabilityKind.PlaybackUnavailable.label}: " +
                            "Demo item — never becomes a real TvPlaybackRequest.",
                    )
                } else {
                    emitEvent(TvHomeEvent.Play(request))
                }
            }
            TvCwClass.PlayableAfterDetails -> {
                val ref = TvContentRef.fromMediaItem(item)
                if (ref == null) {
                    emitNotice(
                        "${TvAvailabilityKind.Unavailable.label}: " +
                            "Missing provider URL — cannot open details.",
                    )
                    return
                }
                val hint = item.resumeHint
                val seriesTypes = setOf("TvSeries", "Anime", "Cartoon", "AsianDrama", "OVA")
                val isSeriesFamily = item.typeLabel in seriesTypes
                if (isSeriesFamily && hint != null && hint.hasExactEpisode) {
                    resolveSeriesResume(ref, hint)
                } else {
                    emitEvent(TvHomeEvent.OpenDetails(ref.copy(resumeHint = hint)))
                }
            }
        }
    }

    /** Phase 10 Hero Watch Now — same CW resolve path for series with exact episode. */
    private fun heroWatchNow(item: TvMediaItem) {
        when (val result = TvHeroWatchNow.resolve(item)) {
            is TvHeroWatchResult.Play -> {
                if (result.request.isMock) {
                    emitNotice(
                        "${TvAvailabilityKind.PlaybackUnavailable.label}: Demo — never plays.",
                    )
                } else {
                    emitEvent(TvHomeEvent.Play(result.request))
                }
            }
            is TvHeroWatchResult.ResolveSeries -> {
                resolveSeriesResume(result.ref, result.hint)
            }
            is TvHeroWatchResult.OpenDetails -> {
                if (result.message != null) {
                    emitNotice(result.message)
                    emitEvent(TvHomeEvent.OpenDetails(result.ref, clearNotice = false))
                } else {
                    emitEvent(TvHomeEvent.OpenDetails(result.ref))
                }
            }
            is TvHeroWatchResult.Demo -> {
                emitNotice("${TvAvailabilityKind.PlaybackUnavailable.label}: ${result.message}")
            }
            is TvHeroWatchResult.Unavailable -> {
                val kind = if (result.playbackRelated) {
                    TvAvailabilityKind.PlaybackUnavailable
                } else {
                    TvAvailabilityKind.Unavailable
                }
                emitNotice("${kind.label}: ${result.message}")
            }
        }
    }

    private fun resolveSeriesResume(ref: TvContentRef, hint: TvResumeHint) {
        resumeJob?.cancel()
        resumeJob = viewModelScope.launch {
            emitNotice("Resuming…")
            val result = try {
                withContext(Dispatchers.IO) {
                    resumeResolver.resolveSeriesResume(ref, hint)
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                logError(t)
                TvContinueWatchingResumeResolver.ResolveResult.OpenDetails(
                    ref.copy(resumeHint = hint),
                    t.message ?: "Failed to resume — open Details.",
                )
            }
            when (result) {
                is TvContinueWatchingResumeResolver.ResolveResult.DirectPlay -> {
                    if (result.request.isMock) {
                        emitNotice(
                            "${TvAvailabilityKind.PlaybackUnavailable.label}: " +
                                "Demo item — never becomes a real TvPlaybackRequest.",
                        )
                    } else {
                        emitEvent(TvHomeEvent.Play(result.request))
                    }
                }
                is TvContinueWatchingResumeResolver.ResolveResult.OpenDetails -> {
                    val msg = result.message
                    if (msg != null) {
                        emitNotice("${TvAvailabilityKind.PlaybackUnavailable.label}: $msg")
                        emitEvent(
                            TvHomeEvent.OpenDetails(
                                result.ref.copy(resumeHint = hint),
                                clearNotice = false,
                            ),
                        )
                    } else {
                        emitEvent(
                            TvHomeEvent.OpenDetails(result.ref.copy(resumeHint = hint)),
                        )
                    }
                }
                is TvContinueWatchingResumeResolver.ResolveResult.Unavailable -> {
                    emitNotice("${TvAvailabilityKind.Unavailable.label}: ${result.message}")
                }
            }
        }
    }

    private fun emitNotice(message: String) {
        emitEvent(TvHomeEvent.Notice(message))
    }

    private fun emitEvent(event: TvHomeEvent) {
        // tryEmit keeps order when called from an already-running VM coroutine.
        if (!_events.tryEmit(event)) {
            viewModelScope.launch { _events.emit(event) }
        }
    }
}
