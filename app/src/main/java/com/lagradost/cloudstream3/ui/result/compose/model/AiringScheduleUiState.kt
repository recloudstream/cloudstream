package com.lagradost.cloudstream3.ui.result.compose.model

import android.content.Context
import androidx.compose.runtime.Immutable
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.syncproviders.AccountManager.Companion.secondsToReadable
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Immutable
data class AiringScheduleUiState(
    val statusText: String? = null,
    val isOngoing: Boolean = false,
    val targetUnixTimeSeconds: Long? = null,
    val displayEpisodeShort: String? = null,
    val displayEpisodeLong: String? = null,
    val releaseDateFormatted: String? = null,
    val dayOfWeek: String? = null,
    val fallbackDateText: String? = null
) {
    val hasAiringInfo: Boolean
        get() = (targetUnixTimeSeconds != null) || !fallbackDateText.isNullOrBlank()
}

fun resolveAiringSchedule(
    context: Context,
    statusText: String? = null,
    nextAiringUnixTime: Long? = null,
    nextAiringEpisode: String? = null,
    nextAiringDate: String? = null,
    episodes: List<ResultEpisode>? = null
): AiringScheduleUiState? {
    val isOngoing = statusText?.contains("ongoing", ignoreCase = true) == true

    val nowMs = APIHolder.unixTimeMS
    val nowSec = APIHolder.unixTime
    val upcomingEpisode = episodes?.filter { (it.airDate ?: 0L) > nowMs }?.minByOrNull { it.airDate ?: Long.MAX_VALUE }

    val resolvedUnixTime = if (nextAiringUnixTime != null && nextAiringUnixTime > nowSec) {
        nextAiringUnixTime
    } else {
        upcomingEpisode?.airDate?.div(1000L)
    }

    val resolvedEpisode = if (!nextAiringEpisode.isNullOrBlank()) {
        nextAiringEpisode
    } else if (upcomingEpisode != null) {
        if (upcomingEpisode.season != null && upcomingEpisode.season > 1) {
            context.getString(R.string.next_season_episode_format, upcomingEpisode.season, upcomingEpisode.episode)
        } else {
            context.getString(R.string.next_episode_format, upcomingEpisode.episode)
        }
    } else {
        null
    }

    val resolvedDate = if (!nextAiringDate.isNullOrBlank()) {
        nextAiringDate
    } else if (upcomingEpisode?.airDate != null) {
        val diffSec = (upcomingEpisode.airDate - nowMs) / 1000
        if (diffSec > 0) {
            context.getString(
                R.string.episode_upcoming_format,
                secondsToReadable(diffSec.toInt(), "")
            )
        } else {
            null
        }
    } else {
        null
    }

    if (statusText.isNullOrBlank() && resolvedUnixTime == null && resolvedDate.isNullOrBlank()) {
        return null
    }

    var releaseDateFormatted: String? = null
    var dayOfWeek: String? = null
    if (resolvedUnixTime != null && resolvedUnixTime > 0) {
        try {
            val date = Date(resolvedUnixTime * 1000)
            dayOfWeek = SimpleDateFormat("EEE", Locale.getDefault()).format(date)
            releaseDateFormatted = SimpleDateFormat("EEEE, dd MMM • HH:mm", Locale.getDefault()).format(date)
        } catch (_: Exception) {}
    }

    var displayEpisodeShort: String? = null
    var displayEpisodeLong: String? = null
    if (resolvedEpisode != null) {
        val match = Regex("""(?:Season\s*(\d+)\s*)?(?:Episode|Ep\.?)\s*(\d+)""", RegexOption.IGNORE_CASE).find(resolvedEpisode)
        if (match != null) {
            val season = match.groupValues.getOrNull(1)?.takeIf { it.isNotBlank() }
            val ep = match.groupValues.getOrNull(2)?.takeIf { it.isNotBlank() }
            if (season != null && ep != null) {
                displayEpisodeShort = "S$season:E$ep"
                displayEpisodeLong = "Season $season Episode $ep"
            } else if (ep != null) {
                displayEpisodeShort = "Ep $ep"
                displayEpisodeLong = "Episode $ep"
            } else {
                displayEpisodeLong = resolvedEpisode
            }
        } else {
            val digitMatch = Regex("""\b(\d+)\b""").find(resolvedEpisode)
            if (digitMatch != null) {
                displayEpisodeShort = "Ep ${digitMatch.value}"
            }
            displayEpisodeLong = resolvedEpisode
        }
    }

    return AiringScheduleUiState(
        statusText = statusText,
        isOngoing = isOngoing,
        targetUnixTimeSeconds = resolvedUnixTime,
        displayEpisodeShort = displayEpisodeShort,
        displayEpisodeLong = displayEpisodeLong,
        releaseDateFormatted = releaseDateFormatted,
        dayOfWeek = dayOfWeek,
        fallbackDateText = resolvedDate
    )
}
