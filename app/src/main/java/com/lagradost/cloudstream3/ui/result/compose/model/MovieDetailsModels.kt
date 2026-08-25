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

fun getPlayButtonText(
    context: Context,
    resumeStatus: ResumeWatchingStatus?,
    episodesToDisplay: List<ResultEpisode>,
    isMovie: Boolean
): String {
    if (resumeStatus != null) {
        val resumeEp = resumeStatus.result
        val prefix = if (resumeStatus.progress != null) {
            context.getString(R.string.resume)
        } else {
            context.getString(R.string.play_movie_button)
        }
        if (resumeStatus.isMovie) {
            return prefix
        }
        val sShort = context.getString(R.string.season_short)
        val eShort = context.getString(R.string.episode_short)
        val s = resumeEp.season
        val e = resumeEp.episode
        val epCode = if (s != null && s > 0 && e > 0) {
            "$sShort$s:$eShort$e"
        } else if (e > 0) {
            "$eShort$e"
        } else {
            ""
        }
        return if (epCode.isNotBlank()) "$prefix $epCode" else prefix
    }
    val firstEp = episodesToDisplay.firstOrNull()
    if (firstEp != null && !isMovie) {
        val sShort = context.getString(R.string.season_short)
        val eShort = context.getString(R.string.episode_short)
        val s = firstEp.season
        val e = firstEp.episode
        val epCode = if (s != null && s > 0 && e > 0) {
            "$sShort$s:$eShort$e"
        } else if (e > 0) {
            "$eShort$e"
        } else {
            ""
        }
        val playStr = context.getString(R.string.play_movie_button)
        return if (epCode.isNotBlank()) "$playStr $epCode" else playStr
    }
    return context.getString(R.string.play_movie_button)
}
