package com.lagradost.cloudstream3.tv.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.tv.data.TvDetailsRepository
import com.lagradost.cloudstream3.tv.model.TvContentRef
import com.lagradost.cloudstream3.tv.model.TvDetailsAction
import com.lagradost.cloudstream3.tv.model.TvDetailsContent
import com.lagradost.cloudstream3.tv.model.TvDetailsUiState
import com.lagradost.cloudstream3.tv.data.TvContinueWatchingResumeResolver
import kotlinx.coroutines.CancellationException
import com.lagradost.cloudstream3.tv.model.TvEpisodeDefaults
import com.lagradost.cloudstream3.tv.model.TvResumeHint
import com.lagradost.cloudstream3.tv.model.TvPlaybackRequest
import com.lagradost.cloudstream4.compose.ActionHandler
import com.lagradost.cloudstream4.compose.DefaultStateContainer
import com.lagradost.cloudstream4.compose.StateContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Lifecycle-aware Details state holder (MVI / [StateContainer]).
 * Loads once per [TvContentRef]; selection (dub / season / episode) lives in UiState only —
 * no FocusRequester / Activity in state.
 *
 * Playback: builds immutable [TvPlaybackRequest] and forwards via [onPlaybackRequest]
 * (Activity → TvPlaybackBridge). No DataStore / PosDur writes.
 *
 * Phase 9: optional [TvContentRef.resumeHint] restores season/episode selection after load
 * (read-only match). Does not auto-play from Details — Home CW orchestrates fromEpisode.
 */
class TvDetailsViewModel(
    private val repository: TvDetailsRepository = TvDetailsRepository(),
    private val resumeResolver: TvContinueWatchingResumeResolver = TvContinueWatchingResumeResolver(repository),
) : ViewModel(),
    StateContainer<TvDetailsUiState> by DefaultStateContainer(TvDetailsUiState.Loading()),
    ActionHandler<TvDetailsAction> {

    private var loadJob: Job? = null
    private var boundRef: TvContentRef? = null

    /** Activity host callback — receives immutable request only. */
    var onPlaybackRequest: ((TvPlaybackRequest) -> Unit)? = null

    fun bind(ref: TvContentRef) {
        if (boundRef == ref && state.value !is TvDetailsUiState.Error) {
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
            TvDetailsAction.Back -> Unit
            TvDetailsAction.WatchNow -> emitMoviePlayback()
            TvDetailsAction.PlaySelectedEpisode -> emitEpisodePlayback()
            is TvDetailsAction.SelectDubStatus -> selectDub(action.dubStatusId)
            is TvDetailsAction.SelectSeason -> selectSeason(action.seasonIndex)
            is TvDetailsAction.SelectEpisode -> selectEpisode(action.episodeId)
        }
    }

    private fun emitMoviePlayback() {
        val content = state.value as? TvDetailsUiState.Content ?: return
        if (content.details.variantLabel != "Movie") return
        onPlaybackRequest?.invoke(TvPlaybackRequest.fromDetails(content.details))
    }

    private fun emitEpisodePlayback() {
        val content = state.value as? TvDetailsUiState.Content ?: return
        if (!content.details.hasEpisodeSelector) return
        val episode = content.selectedEpisode ?: return
        if (!episode.isPlayable) return
        onPlaybackRequest?.invoke(
            TvPlaybackRequest.fromEpisode(content.details, episode),
        )
    }

    private fun selectDub(dubStatusId: Int) {
        updateState {
            val content = this as? TvDetailsUiState.Content ?: return@updateState this
            if (content.selectedDubStatusId == dubStatusId) return@updateState content
            val seasons = content.details.seasonsForDub(dubStatusId)
            val seasonIndex = TvEpisodeDefaults.pickSeasonIndex(seasons)
            val season = seasons.firstOrNull { it.seasonIndex == seasonIndex }
            val episodeId = TvEpisodeDefaults.pickEpisodeId(season)
            content.copy(
                selectedDubStatusId = dubStatusId,
                selectedSeasonIndex = seasonIndex,
                selectedEpisodeId = episodeId,
            )
        }
    }

    private fun selectSeason(seasonIndex: Int) {
        updateState {
            val content = this as? TvDetailsUiState.Content ?: return@updateState this
            if (content.selectedSeasonIndex == seasonIndex) return@updateState content
            val season = content.details.seasonsForDub(content.selectedDubStatusId)
                .firstOrNull { it.seasonIndex == seasonIndex }
            val episodeId = TvEpisodeDefaults.pickEpisodeId(season)
            content.copy(
                selectedSeasonIndex = seasonIndex,
                selectedEpisodeId = episodeId,
            )
        }
    }

    private fun selectEpisode(episodeId: Int) {
        updateState {
            val content = this as? TvDetailsUiState.Content ?: return@updateState this
            content.copy(selectedEpisodeId = episodeId)
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
                if (t is CancellationException) throw t
                logError(t)
                TvDetailsRepository.LoadResult.Failure(
                    t.message ?: "Unexpected error loading details",
                )
            }
            updateState {
                when (result) {
                    is TvDetailsRepository.LoadResult.Success ->
                        contentWithResumeRestore(result.details, ref.resumeHint)

                    is TvDetailsRepository.LoadResult.Failure ->
                        TvDetailsUiState.Error(
                            message = result.message,
                            titleHint = ref.title.takeIf { it.isNotBlank() },
                        )
                }
            }
        }
    }

    /**
     * Apply CW resume hint when possible: episodeId first, else season+episode.
     * Falls back to Phase 6 defaults — never invents episodes or writes DataStore.
     */
    private fun contentWithResumeRestore(
        details: TvDetailsContent,
        hint: TvResumeHint?,
    ): TvDetailsUiState.Content {
        if (hint == null || !details.hasEpisodeSelector || !hint.hasExactEpisode) {
            return TvDetailsUiState.Content(
                details = details,
                selectedDubStatusId = details.defaultDubStatusId,
                selectedSeasonIndex = details.defaultSeasonIndex,
                selectedEpisodeId = details.defaultEpisodeId,
            )
        }
        val matched = resumeResolver.matchEpisode(details, hint)
        if (matched == null) {
            return TvDetailsUiState.Content(
                details = details,
                selectedDubStatusId = details.defaultDubStatusId,
                selectedSeasonIndex = details.defaultSeasonIndex,
                selectedEpisodeId = details.defaultEpisodeId,
            )
        }
        val seasonIndex = matched.seasonIndex ?: 0
        return TvDetailsUiState.Content(
            details = details,
            selectedDubStatusId = matched.dubStatusId,
            selectedSeasonIndex = seasonIndex,
            selectedEpisodeId = matched.id,
        )
    }
}
