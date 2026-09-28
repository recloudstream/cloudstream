package com.lagradost.cloudstream3.ui.player

import com.lagradost.cloudstream3.utils.ExtractorLinkType

internal data class EpisodeSourceSelection(
    val source: String?,
    val name: String,
    val quality: Int?,
    val type: ExtractorLinkType?,
)

internal sealed interface EpisodeSubtitleSelection {
    data class Selected(
        val originalName: String,
        val nameSuffix: String,
    ) : EpisodeSubtitleSelection

    data object Disabled : EpisodeSubtitleSelection
}

internal data class EpisodeSubtitleResolution(
    val subtitle: SubtitleData?,
    val selectionMatched: Boolean,
    val subtitlesDisabled: Boolean,
)

internal fun VideoLink.toEpisodeSourceSelection(): EpisodeSourceSelection? {
    val link = first
    if (link != null) {
        return EpisodeSourceSelection(link.source, link.name, link.quality, link.type)
    }

    val uri = second ?: return null
    return EpisodeSourceSelection(null, uri.name, null, null)
}

internal fun findEpisodeSource(
    links: List<VideoLink>,
    selection: EpisodeSourceSelection?,
): VideoLink? {
    return selection?.let { remembered ->
        links.firstOrNull { it.toEpisodeSourceSelection() == remembered }
    }
}

internal fun createEpisodeSubtitleSelection(
    subtitle: SubtitleData?,
    subtitlesDisabled: Boolean,
): EpisodeSubtitleSelection? {
    return when {
        subtitle != null -> EpisodeSubtitleSelection.Selected(
            subtitle.originalName,
            subtitle.nameSuffix,
        )

        subtitlesDisabled -> EpisodeSubtitleSelection.Disabled
        else -> null
    }
}

internal fun findEpisodeSubtitle(
    subtitles: Set<SubtitleData>,
    selection: EpisodeSubtitleSelection?,
): SubtitleData? {
    val remembered = selection as? EpisodeSubtitleSelection.Selected ?: return null
    return subtitles.firstOrNull {
        it.originalName == remembered.originalName &&
                it.nameSuffix == remembered.nameSuffix
    }
}

internal fun resolveEpisodeSubtitle(
    subtitles: Set<SubtitleData>,
    selection: EpisodeSubtitleSelection?,
    fallback: () -> SubtitleData?,
): EpisodeSubtitleResolution {
    val rememberedSubtitle = findEpisodeSubtitle(subtitles, selection)
    val subtitlesDisabled = selection == EpisodeSubtitleSelection.Disabled
    return EpisodeSubtitleResolution(
        subtitle = when {
            subtitlesDisabled -> null
            rememberedSubtitle != null -> rememberedSubtitle
            else -> fallback()
        },
        selectionMatched = rememberedSubtitle != null,
        subtitlesDisabled = subtitlesDisabled,
    )
}
