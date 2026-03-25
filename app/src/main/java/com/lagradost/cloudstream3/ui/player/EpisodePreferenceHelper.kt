package com.lagradost.cloudstream3.ui.player

import com.lagradost.cloudstream3.CloudStreamApp.Companion.getKey
import com.lagradost.cloudstream3.CloudStreamApp.Companion.setKey
import com.lagradost.cloudstream3.isEpisodeBased
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.utils.AppContextUtils.sortSubs
import com.lagradost.cloudstream3.utils.DataStoreHelper.currentAccount
import com.lagradost.cloudstream3.utils.ExtractorLink

private const val PLAYER_EPISODE_PREFERENCES = "player_episode_preferences"

/**
 * Maximum age for episode preferences in milliseconds (30 days).
 * Preferences older than this are automatically expired and removed
 * to prevent backup files from growing endlessly.
 */
private const val PREFERENCE_MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000

internal data class EpisodePlaybackPreference(
    val sourceDisplayName: String? = null,
    val subtitleOriginalName: String? = null,
    /** Subtitle URL for more stable matching. */
    val subtitleUrl: String? = null,
    /** Subtitle name suffix (e.g. " 3" in "Turkish 3") for cross-episode matching when URLs change. */
    val subtitleNameSuffix: String? = null,
    val subtitleLanguageTag: String? = null,
    val subtitlesDisabled: Boolean = false,
    /** Timestamp when this preference was last saved, used for expiration. */
    val savedAt: Long = System.currentTimeMillis(),
)

internal data class ResolvedEpisodeSubtitlePreference(
    val subtitle: SubtitleData?,
    val blockFallback: Boolean,
)

/**
 * Helper object for managing episode playback preferences (source and subtitle selections).
 * Stores the user's selected source and subtitle for each series so that the same selections are automatically applied when switching episodes.
 * Preferences are automatically expired after 30 days.
 */
internal object EpisodePreferenceHelper {

    fun getSourceDisplayName(link: Pair<ExtractorLink?, ExtractorUri?>?): String? {
        return link?.first?.name ?: link?.second?.name
    }

    /**
     * Resolves the preferred source from the stored episode preference.
     * Returns null if no preference exists or the preferred source is not available.
     */
    fun resolvePreferenceSource(
        links: List<Pair<ExtractorLink?, ExtractorUri?>>,
        preference: EpisodePlaybackPreference?,
    ): Pair<ExtractorLink?, ExtractorUri?>? {
        val preferredSourceName = preference?.sourceDisplayName ?: return null
        return links.firstOrNull { getSourceDisplayName(it) == preferredSourceName }
    }

    /**
     * Resolves the preferred subtitle from the stored episode preference.
     *
     * Matching priority:
     * 1. Exact match by originalName + URL (most stable, same episode re-selection)
     * 2. Match by originalName + nameSuffix (cross-episode: e.g. "Turkish 3" stays "Turkish 3")
     * 3. Match by originalName only (fallback if suffix not found)
     * 4. Match by languageTag + nameSuffix (fallback with suffix preference)
     * 5. Match by languageTag only (broadest fallback)
     *
     * If subtitles were explicitly disabled, returns blockFallback=true
     * to prevent auto-selection from overriding the user's choice.
     */
    fun resolvePreferenceSubtitle(
        subtitles: Set<SubtitleData>,
        preference: EpisodePlaybackPreference?,
    ): ResolvedEpisodeSubtitlePreference {
        if (preference == null) {
            return ResolvedEpisodeSubtitlePreference(
                subtitle = null,
                blockFallback = false,
            )
        }

        if (preference.subtitlesDisabled) {
            return ResolvedEpisodeSubtitlePreference(
                subtitle = null,
                blockFallback = true,
            )
        }

        val sortedSubtitles = sortSubs(subtitles)

        // Priority 1: Match by originalName + URL (most stable, works for same episode)
        sortedSubtitles.firstOrNull { subtitle ->
            subtitle.originalName == preference.subtitleOriginalName &&
                    subtitle.url == preference.subtitleUrl
        }?.let { subtitle ->
            return ResolvedEpisodeSubtitlePreference(
                subtitle = subtitle,
                blockFallback = false,
            )
        }

        // Priority 2: Match by originalName + nameSuffix (cross-episode, preserves "Turkish 3" selection)
        if (preference.subtitleNameSuffix != null) {
            preference.subtitleOriginalName?.let { originalName ->
                sortedSubtitles.firstOrNull { subtitle ->
                    subtitle.originalName == originalName &&
                            subtitle.nameSuffix == preference.subtitleNameSuffix
                }?.let { subtitle ->
                    return ResolvedEpisodeSubtitlePreference(
                        subtitle = subtitle,
                        blockFallback = false,
                    )
                }
            }
        }

        // Priority 3: Match by originalName only
        preference.subtitleOriginalName?.let { originalName ->
            sortedSubtitles.firstOrNull { subtitle ->
                subtitle.originalName == originalName
            }?.let { subtitle ->
                return ResolvedEpisodeSubtitlePreference(
                    subtitle = subtitle,
                    blockFallback = false,
                )
            }
        }

        // Priority 4: Match by languageTag + nameSuffix
        if (preference.subtitleNameSuffix != null) {
            preference.subtitleLanguageTag?.let { languageTag ->
                sortedSubtitles.firstOrNull { subtitle ->
                    subtitle.matchesLanguageCode(languageTag) &&
                            subtitle.nameSuffix == preference.subtitleNameSuffix
                }?.let { subtitle ->
                    return ResolvedEpisodeSubtitlePreference(
                        subtitle = subtitle,
                        blockFallback = false,
                    )
                }
            }
        }

        // Priority 5: Match by languageTag only
        preference.subtitleLanguageTag?.let { languageTag ->
            sortedSubtitles.firstOrNull { subtitle ->
                subtitle.matchesLanguageCode(languageTag)
            }?.let { subtitle ->
                return ResolvedEpisodeSubtitlePreference(
                    subtitle = subtitle,
                    blockFallback = false,
                )
            }
        }

        return ResolvedEpisodeSubtitlePreference(
            subtitle = null,
            blockFallback = false,
        )
    }

    /** Extracts the parent ID for episode-based content, returns null for movies. */
    fun getEpisodePreferenceParentId(meta: Any?): Int? {
        return when (meta) {
            is ResultEpisode -> meta.parentId.takeIf { meta.tvType.isEpisodeBased() }
            is ExtractorUri -> meta.parentId.takeIf { meta.tvType?.isEpisodeBased() == true }
            else -> null
        }
    }

    /**
     * Retrieves the stored episode preference for the given meta.
     * Returns null if no preference is stored or if the preference has expired (older than 30 days).
     */
    fun getEpisodePreference(meta: Any?): EpisodePlaybackPreference? {
        val parentId = getEpisodePreferenceParentId(meta) ?: return null
        val preference = getKey<EpisodePlaybackPreference>(
            "$currentAccount/$PLAYER_EPISODE_PREFERENCES",
            parentId.toString()
        ) ?: return null

        // Expire preferences older than 30 days
        if (System.currentTimeMillis() - preference.savedAt > PREFERENCE_MAX_AGE_MS) {
            return null
        }

        return preference
    }

    /**
     * Updates the episode preference with the given transformation.
     * Automatically sets the savedAt timestamp for expiration tracking.
     */
    fun updateEpisodePreference(
        meta: Any?,
        update: EpisodePlaybackPreference.() -> EpisodePlaybackPreference,
    ) {
        val parentId = getEpisodePreferenceParentId(meta) ?: return
        val current = getEpisodePreference(meta) ?: EpisodePlaybackPreference()
        val updatedPreference = update(current).copy(savedAt = System.currentTimeMillis())
        setKey(
            "$currentAccount/$PLAYER_EPISODE_PREFERENCES",
            parentId.toString(),
            updatedPreference
        )
    }

    /** Saves the user's selected source for the current series. */
    fun persistSourcePreference(
        meta: Any?,
        link: Pair<ExtractorLink?, ExtractorUri?>?,
    ) {
        val sourceDisplayName = getSourceDisplayName(link) ?: return
        updateEpisodePreference(meta) {
            copy(sourceDisplayName = sourceDisplayName)
        }
    }

    /** Saves the user's selected subtitle for the current series. Uses URL + nameSuffix for matching. */
    fun persistSubtitlePreference(
        meta: Any?,
        subtitle: SubtitleData?,
    ) {
        updateEpisodePreference(meta) {
            if (subtitle == null) {
                copy(
                    subtitleOriginalName = null,
                    subtitleUrl = null,
                    subtitleNameSuffix = null,
                    subtitleLanguageTag = null,
                    subtitlesDisabled = true,
                )
            } else {
                copy(
                    subtitleOriginalName = subtitle.originalName,
                    subtitleUrl = subtitle.url,
                    subtitleNameSuffix = subtitle.nameSuffix,
                    subtitleLanguageTag = subtitle.getIETF_tag(),
                    subtitlesDisabled = false,
                )
            }
        }
    }

    /**
     * Checks whether we should wait for the preferred source to become available
     * before auto-starting playback. Returns true if a preference exists but the
     * preferred source has not been loaded yet.
     */
    fun shouldWaitForPreferredSource(
        links: Set<Pair<ExtractorLink?, ExtractorUri?>>,
        meta: Any?,
    ): Boolean {
        val preferredSourceName = getEpisodePreference(meta)?.sourceDisplayName ?: return false
        return links.none { link ->
            getSourceDisplayName(link) == preferredSourceName
        }
    }
}
