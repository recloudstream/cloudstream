package com.lagradost.cloudstream3.tv.model

/**
 * Phase 10 — richer stale / availability labels.
 *
 * Only distinctions the backend / existing TV adapters can establish.
 * Never invent provider presence, playback readiness, or silent content swaps.
 */
enum class TvAvailabilityKind(val label: String) {
    /** Content identity present and path is known-good for this surface. */
    Available("Available"),

    /** Insufficient identity or explicitly not safely actionable. */
    Unavailable("Unavailable"),

    /** No / missing homepage or search provider (plugins not ready, api missing). */
    ProviderMissing("Provider Missing"),

    /** Network / APIRepository / getMainPage / load failure. */
    LoadFailed("Load Failed"),

    /** Identity OK but Compose TV cannot play (Live/Torrent/comingSoon/demo/no episode data). */
    PlaybackUnavailable("Playback Unavailable"),
}

data class TvAvailabilityStatus(
    val kind: TvAvailabilityKind,
    val message: String,
) {
    val title: String get() = kind.label
}

/**
 * Map failure / empty messages already produced by TV repositories to a kind.
 * Heuristics stay conservative — prefer LoadFailed when ambiguous.
 */
object TvAvailabilityClassifier {

    fun fromHomeFailure(message: String): TvAvailabilityStatus {
        val m = message.lowercase()
        return when {
            m.contains("no homepage provider") ||
                m.contains("does not exist") ||
                m.contains("no search providers") ||
                m.contains("has no homepage") ||
                (m.contains("plugins") && m.contains("provider")) ->
                TvAvailabilityStatus(TvAvailabilityKind.ProviderMissing, message)

            else -> TvAvailabilityStatus(TvAvailabilityKind.LoadFailed, message)
        }
    }

    fun fromSearchFailure(message: String): TvAvailabilityStatus {
        val m = message.lowercase()
        return when {
            m.contains("no search providers") ||
                m.contains("provider does not exist") ||
                m.contains("plugins") ->
                TvAvailabilityStatus(TvAvailabilityKind.ProviderMissing, message)

            else -> TvAvailabilityStatus(TvAvailabilityKind.LoadFailed, message)
        }
    }

    fun fromDetailsFailure(message: String): TvAvailabilityStatus {
        val m = message.lowercase()
        return when {
            m.contains("does not exist") ||
                (m.contains("provider") && (m.contains("missing") || m.contains("not"))) ->
                TvAvailabilityStatus(TvAvailabilityKind.ProviderMissing, message)

            else -> TvAvailabilityStatus(TvAvailabilityKind.LoadFailed, message)
        }
    }

    fun fromWatchlistFailure(message: String): TvAvailabilityStatus =
        TvAvailabilityStatus(TvAvailabilityKind.LoadFailed, message)

    fun fromCwClassification(classification: TvCwClassification): TvAvailabilityStatus =
        when (classification.clazz) {
            TvCwClass.DirectPlayable,
            TvCwClass.PlayableAfterDetails,
            -> TvAvailabilityStatus(TvAvailabilityKind.Available, classification.reason)

            TvCwClass.NotSafelyPlayable -> {
                val m = classification.reason.lowercase()
                val kind = when {
                    m.contains("live") || m.contains("torrent") || m.contains("demo") ||
                        m.contains("not supported") ->
                        TvAvailabilityKind.PlaybackUnavailable
                    else -> TvAvailabilityKind.Unavailable
                }
                TvAvailabilityStatus(kind, classification.reason)
            }
        }
}

/**
 * Phase 10 Hero Watch Now — same identity rules as CW classifier.
 *
 * - Movie + identity → [Play] TvPlaybackRequest → bridge
 * - Series/Anime + Phase 9-safe exact episode hint → [ResolveSeries] (same resume path)
 * - Else Series/Anime / other → [OpenDetails]
 * - Mock → [Demo] never plays
 */
sealed interface TvHeroWatchResult {
    data class Play(val request: TvPlaybackRequest) : TvHeroWatchResult

    /** Series/Anime with exact S/E or episodeId — resolve via TvContinueWatchingResume. */
    data class ResolveSeries(
        val ref: TvContentRef,
        val hint: TvResumeHint,
    ) : TvHeroWatchResult

    data class OpenDetails(val ref: TvContentRef, val message: String? = null) : TvHeroWatchResult
    data class Demo(val message: String) : TvHeroWatchResult
    data class Unavailable(val message: String, val playbackRelated: Boolean = false) : TvHeroWatchResult
}

object TvHeroWatchNow {

    private val seriesOrAnimeTypes = setOf(
        "TvSeries",
        "Anime",
        "Cartoon",
        "AsianDrama",
        "OVA",
    )

    fun resolve(item: TvMediaItem): TvHeroWatchResult {
        if (item.isMock) {
            return TvHeroWatchResult.Demo(
                "Demo item — Watch Now unavailable (never becomes a real TvPlaybackRequest).",
            )
        }
        val classification = TvContinueWatchingClassifier.classifyMediaItem(item)
        when (classification.clazz) {
            TvCwClass.NotSafelyPlayable -> {
                val status = TvAvailabilityClassifier.fromCwClassification(classification)
                return TvHeroWatchResult.Unavailable(
                    classification.reason,
                    playbackRelated = status.kind == TvAvailabilityKind.PlaybackUnavailable,
                )
            }
            TvCwClass.DirectPlayable -> {
                val request = TvContinueWatchingClassifier.moviePlaybackRequest(item)
                    ?: return TvHeroWatchResult.Unavailable(
                        "Movie identity incomplete — cannot Watch Now.",
                        playbackRelated = true,
                    )
                return TvHeroWatchResult.Play(request)
            }
            TvCwClass.PlayableAfterDetails -> {
                val ref = TvContentRef.fromMediaItem(item)
                    ?: return TvHeroWatchResult.Unavailable(
                        "Missing provider URL — cannot open Details.",
                    )
                val hint = item.resumeHint
                val isSeriesFamily = item.typeLabel in seriesOrAnimeTypes
                if (isSeriesFamily && hint != null && hint.hasExactEpisode) {
                    return TvHeroWatchResult.ResolveSeries(ref.copy(resumeHint = hint), hint)
                }
                return TvHeroWatchResult.OpenDetails(
                    ref,
                    if (isSeriesFamily) {
                        "Series/Anime — open Details to pick an episode (no deterministic hero target)."
                    } else {
                        "Open Details — do not guess playback variant from hero alone."
                    },
                )
            }
        }
    }
}
