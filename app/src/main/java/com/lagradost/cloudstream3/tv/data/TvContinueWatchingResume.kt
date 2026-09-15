package com.lagradost.cloudstream3.tv.data

import com.lagradost.cloudstream3.tv.model.TvContentRef
import com.lagradost.cloudstream3.tv.model.TvDetailsContent
import com.lagradost.cloudstream3.tv.model.TvEpisode
import com.lagradost.cloudstream3.tv.model.TvPlaybackRequest
import com.lagradost.cloudstream3.tv.model.TvResumeHint

/**
 * Phase 9 read-only series/anime resume resolver.
 *
 * Exact path when CW has season+episode (or episodeId):
 *   TvContentRef → [TvDetailsRepository.load] (same as Details)
 *   → match [TvEpisode] by episodeId, else season+episode
 *   → [TvPlaybackRequest.fromEpisode] → existing [com.lagradost.cloudstream3.tv.playback.TvPlaybackBridge]
 *
 * Precise missing data on CW alone: Episode.data (and full episode metadata).
 * Does **not** invent episode URLs, write PosDur, or add a second persistence architecture.
 */
class TvContinueWatchingResume(
    private val detailsRepository: TvDetailsRepository = TvDetailsRepository(),
) {

    sealed interface ResolveResult {
        data class Playback(val request: TvPlaybackRequest) : ResolveResult
        /** Open shared Details with optional restore hints — episode not matched or no exact target. */
        data class OpenDetails(val ref: TvContentRef, val message: String? = null) : ResolveResult
        data class Unavailable(val message: String) : ResolveResult
    }

    /**
     * Resolve series/anime CW click when [hint] has exact episode identity.
     * Caller must only invoke for B-class series/anime with [TvResumeHint.hasExactEpisode].
     */
    suspend fun resolveSeriesResume(
        ref: TvContentRef,
        hint: TvResumeHint,
    ): ResolveResult {
        if (!hint.hasExactEpisode) {
            return ResolveResult.OpenDetails(ref)
        }
        return when (val load = detailsRepository.load(ref)) {
            is TvDetailsRepository.LoadResult.Failure ->
                ResolveResult.OpenDetails(ref, load.message)

            is TvDetailsRepository.LoadResult.Success -> {
                val details = load.details
                if (!details.hasEpisodeSelector) {
                    // Loaded as Movie/Other despite series typeLabel — open Details, never swap silently.
                    return ResolveResult.OpenDetails(
                        ref,
                        "Content type is ${details.variantLabel} — open Details instead of forcing episode resume.",
                    )
                }
                val episode = matchEpisode(details, hint)
                when {
                    episode == null -> ResolveResult.OpenDetails(
                        ref.copy(
                            // Preserve title; restore still applied by Details from hint.
                        ),
                        "Saved episode not found — pick one on Details.",
                    )
                    !episode.isPlayable -> ResolveResult.OpenDetails(
                        ref,
                        "Saved episode has no playable data — pick another on Details.",
                    )
                    else -> ResolveResult.Playback(
                        TvPlaybackRequest.fromEpisode(details, episode, isMock = false),
                    )
                }
            }
        }
    }

    /**
     * Match order (never invent):
     * 1. episodeId exact (aligns with ResultViewModel2 / player cache keys)
     * 2. seasonIndex + episodeNumber (display season ?: seasonIndex)
     * Prefer playable matches when multiple.
     */
    fun matchEpisode(details: TvDetailsContent, hint: TvResumeHint): TvEpisode? {
        val byId = hint.episodeId?.let { id -> details.episodeById(id) }
        if (byId != null) return byId

        val targetEp = hint.episode ?: return null
        val targetSeason = hint.season
        val all = details.dubGroups.asSequence()
            .flatMap { it.seasons.asSequence() }
            .flatMap { season -> season.episodes.asSequence() }
            .filter { ep ->
                ep.episodeNumber == targetEp &&
                    seasonMatches(ep, targetSeason)
            }
            .toList()
        if (all.isEmpty()) return null
        return all.firstOrNull { it.isPlayable } ?: all.firstOrNull()
    }

    private fun seasonMatches(ep: TvEpisode, targetSeason: Int?): Boolean {
        if (targetSeason == null) return true
        val epSeason = ep.displaySeason ?: ep.seasonIndex ?: 0
        return epSeason == targetSeason
    }
}
