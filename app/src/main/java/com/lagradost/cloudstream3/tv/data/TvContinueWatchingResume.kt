package com.lagradost.cloudstream3.tv.data

import com.lagradost.cloudstream3.tv.model.TvContentRef
import com.lagradost.cloudstream3.tv.model.TvDetailsContent
import com.lagradost.cloudstream3.tv.model.TvEpisode
import com.lagradost.cloudstream3.tv.model.TvPlaybackRequest
import com.lagradost.cloudstream3.tv.model.TvResumeHint

/**
 * Phase 9 read-only series/anime resume resolver.
 *
 * [TvContentRef] = load identity (url + apiName + title).
 * [TvResumeHint] = optional CW context (season / episode / episodeId / parentId) — never invents URLs.
 *
 * Exact path when CW has season+episode (or episodeId):
 *   TvContentRef → [TvDetailsRepository.load] (same as Details)
 *   → match [TvEpisode] by episodeId → season+ep → parent+ep
 *   → [TvPlaybackRequest.fromEpisode] → existing [com.lagradost.cloudstream3.tv.playback.TvPlaybackBridge]
 *
 * Precise missing data on CW alone: Episode.data (and full episode metadata).
 * Does **not** invent episode URLs, write PosDur, or add a second persistence architecture.
 */
class TvContinueWatchingResumeResolver(
    private val detailsRepository: TvDetailsRepository = TvDetailsRepository(),
) {

    sealed interface ResolveResult {
        /** Exact episode matched and playable — launch via existing bridge. */
        data class DirectPlay(val request: TvPlaybackRequest) : ResolveResult

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
            return ResolveResult.OpenDetails(ref.copy(resumeHint = hint))
        }
        return when (val load = detailsRepository.load(ref)) {
            is TvDetailsRepository.LoadResult.Failure ->
                ResolveResult.OpenDetails(ref.copy(resumeHint = hint), load.message)

            is TvDetailsRepository.LoadResult.Success -> {
                val details = load.details
                if (!details.hasEpisodeSelector) {
                    // Loaded as Movie/Other despite series typeLabel — open Details, never swap silently.
                    return ResolveResult.OpenDetails(
                        ref.copy(resumeHint = hint),
                        "Content type is ${details.variantLabel} — open Details instead of forcing episode resume.",
                    )
                }
                val episode = matchEpisode(details, hint)
                when {
                    episode == null -> ResolveResult.OpenDetails(
                        ref.copy(resumeHint = hint),
                        "Saved episode not found — pick one on Details.",
                    )
                    !episode.isPlayable -> ResolveResult.OpenDetails(
                        ref.copy(resumeHint = hint),
                        "Saved episode has no playable data — pick another on Details.",
                    )
                    else -> ResolveResult.DirectPlay(
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
     * 3. parentId + episodeNumber (season ignored — stale season fallback when parent known)
     * Prefer playable matches when multiple.
     */
    fun matchEpisode(details: TvDetailsContent, hint: TvResumeHint): TvEpisode? {
        val byId = hint.episodeId?.let { id -> details.episodeById(id) }
        if (byId != null) return byId

        val targetEp = hint.episode
        if (targetEp != null && hint.season != null) {
            val bySeasonEp = findBySeasonAndEpisode(details, hint.season, targetEp)
            if (bySeasonEp != null) return bySeasonEp
        }

        // parent+ep: details load is already scoped to the CW parent identity;
        // when parentId is present, allow episode-number match ignoring stale season.
        if (hint.parentId != null && targetEp != null) {
            val byParentEp = findByEpisodeNumber(details, targetEp)
            if (byParentEp != null) return byParentEp
        }

        // season unknown but episode present (no parentId) — best-effort season-agnostic match
        if (targetEp != null && hint.season == null) {
            return findByEpisodeNumber(details, targetEp)
        }
        return null
    }

    private fun findBySeasonAndEpisode(
        details: TvDetailsContent,
        targetSeason: Int,
        targetEp: Int,
    ): TvEpisode? {
        val all = details.dubGroups.asSequence()
            .flatMap { it.seasons.asSequence() }
            .flatMap { season -> season.episodes.asSequence() }
            .filter { ep ->
                ep.episodeNumber == targetEp && seasonMatches(ep, targetSeason)
            }
            .toList()
        if (all.isEmpty()) return null
        return all.firstOrNull { it.isPlayable } ?: all.firstOrNull()
    }

    private fun findByEpisodeNumber(details: TvDetailsContent, targetEp: Int): TvEpisode? {
        val all = details.dubGroups.asSequence()
            .flatMap { it.seasons.asSequence() }
            .flatMap { it.episodes.asSequence() }
            .filter { it.episodeNumber == targetEp }
            .toList()
        if (all.isEmpty()) return null
        return all.firstOrNull { it.isPlayable } ?: all.firstOrNull()
    }

    private fun seasonMatches(ep: TvEpisode, targetSeason: Int): Boolean {
        val epSeason = ep.displaySeason ?: ep.seasonIndex ?: 0
        return epSeason == targetSeason
    }
}
