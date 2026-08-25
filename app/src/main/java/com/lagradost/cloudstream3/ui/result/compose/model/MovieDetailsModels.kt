package com.lagradost.cloudstream3.ui.result.compose.model

import android.content.Context
import androidx.compose.runtime.Immutable
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.ui.result.ResumeWatchingStatus
import com.lagradost.cloudstream3.ui.result.compose.components.MovieCardItem

@Immutable
data class MovieTrailerData(
    val title: String,
    val runtime: String,
    val rawTrailer: Any? = null
)

@Immutable
data class MovieRecommendationRow(
    val rowIndex: Int,
    val items: List<MovieCardItem>
)

private fun formatEpisodeCode(context: Context, season: Int?, episode: Int?): String {
    val s = season ?: 0
    val e = episode ?: 0
    val sShort = context.getString(R.string.season_short)
    val eShort = context.getString(R.string.episode_short)
    return when {
        s > 0 && e > 0 -> "$sShort$s:$eShort$e"
        e > 0 -> "$eShort$e"
        else -> ""
    }
}

private fun formatWithEpisodeCode(prefix: String, epCode: String): String {
    return if (epCode.isNotBlank()) "$prefix $epCode" else prefix
}

private fun resolveResumeButtonText(context: Context, resumeStatus: ResumeWatchingStatus): String {
    val prefix = if (resumeStatus.progress != null) {
        context.getString(R.string.resume)
    } else {
        context.getString(R.string.play_movie_button)
    }
    if (resumeStatus.isMovie) {
        return prefix
    }
    val resumeEp = resumeStatus.result
    val epCode = formatEpisodeCode(context, resumeEp.season, resumeEp.episode)
    return formatWithEpisodeCode(prefix, epCode)
}

private fun resolveFirstEpisodeButtonText(
    context: Context,
    episodesToDisplay: List<ResultEpisode>,
    isMovie: Boolean
): String {
    val playStr = context.getString(R.string.play_movie_button)
    val firstEp = episodesToDisplay.firstOrNull()
    if (firstEp != null && !isMovie) {
        val epCode = formatEpisodeCode(context, firstEp.season, firstEp.episode)
        return formatWithEpisodeCode(playStr, epCode)
    }
    return playStr
}

fun getPlayButtonText(
    context: Context,
    resumeStatus: ResumeWatchingStatus?,
    episodesToDisplay: List<ResultEpisode>,
    isMovie: Boolean
): String {
    if (resumeStatus != null) {
        return resolveResumeButtonText(context, resumeStatus)
    }
    return resolveFirstEpisodeButtonText(context, episodesToDisplay, isMovie)
}
