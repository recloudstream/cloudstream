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
        get() = targetUnixTimeSeconds != null || !fallbackDateText.isNullOrBlank()
}

private fun findUpcomingEpisode(episodes: List<ResultEpisode>?, nowMs: Long): ResultEpisode? {
    return episodes?.filter { (it.airDate ?: 0L) > nowMs }?.minByOrNull { it.airDate ?: Long.MAX_VALUE }
}

private fun resolveUpcomingEpisode(
    context: Context,
    nextAiringEpisode: String?,
    upcomingEpisode: ResultEpisode?
): String? {
    if (!nextAiringEpisode.isNullOrBlank()) {
        return nextAiringEpisode
    }
    if (upcomingEpisode == null) {
        return null
    }
    return if (upcomingEpisode.season != null && upcomingEpisode.season > 1) {
        context.getString(R.string.next_season_episode_format, upcomingEpisode.season, upcomingEpisode.episode)
    } else {
        context.getString(R.string.next_episode_format, upcomingEpisode.episode)
    }
}

private fun resolveUpcomingDate(
    context: Context,
    nextAiringDate: String?,
    upcomingEpisode: ResultEpisode?,
    nowMs: Long
): String? {
    if (!nextAiringDate.isNullOrBlank()) {
        return nextAiringDate
    }
    val airDate = upcomingEpisode?.airDate ?: return null
    val diffSec = (airDate - nowMs) / 1000
    return if (diffSec > 0) {
        context.getString(R.string.episode_upcoming_format, secondsToReadable(diffSec.toInt(), ""))
    } else {
        null
    }
}

private fun formatAiringDateTime(resolvedUnixTime: Long?): Pair<String?, String?> {
    if (resolvedUnixTime == null || resolvedUnixTime <= 0) return null to null
    return try {
        val date = Date(resolvedUnixTime * 1000)
        val dayOfWeek = SimpleDateFormat("EEE", Locale.getDefault()).format(date)
        val releaseDate = SimpleDateFormat("EEEE, dd MMM • HH:mm", Locale.getDefault()).format(date)
        releaseDate to dayOfWeek
    } catch (_: Exception) {
        null to null
    }
}

private fun parseEpisodeDisplayLabels(resolvedEpisode: String?): Pair<String?, String?> {
    if (resolvedEpisode == null) return null to null

    val match = Regex("""(?:Season\s*(\d+)\s*)?(?:Episode|Ep\.?)\s*(\d+)""", RegexOption.IGNORE_CASE).find(resolvedEpisode)
    if (match != null) {
        val season = match.groupValues.getOrNull(1)?.takeIf { it.isNotBlank() }
        val ep = match.groupValues.getOrNull(2)?.takeIf { it.isNotBlank() }
        return when {
            season != null && ep != null -> "S$season:E$ep" to "Season $season Episode $ep"
            ep != null -> "Ep $ep" to "Episode $ep"
            else -> null to resolvedEpisode
        }
    }

    val digitMatch = Regex("""\b(\d+)\b""").find(resolvedEpisode)
    val shortLabel = digitMatch?.let { "Ep ${it.value}" }
    return shortLabel to resolvedEpisode
}

fun resolveAiringSchedule(
    context: Context,
    statusText: String? = null,
    isOngoing: Boolean = false,
    nextAiringUnixTime: Long? = null,
    nextAiringEpisode: String? = null,
    nextAiringDate: String? = null,
    episodes: List<ResultEpisode>? = null
): AiringScheduleUiState? {
    val nowMs = APIHolder.unixTimeMS
    val nowSec = APIHolder.unixTime
    val upcomingEpisode = findUpcomingEpisode(episodes, nowMs)

    val resolvedUnixTime = if (nextAiringUnixTime != null && nextAiringUnixTime > nowSec) {
        nextAiringUnixTime
    } else {
        upcomingEpisode?.airDate?.div(1000L)
    }

    val resolvedEpisode = resolveUpcomingEpisode(context, nextAiringEpisode, upcomingEpisode)
    val resolvedDate = resolveUpcomingDate(context, nextAiringDate, upcomingEpisode, nowMs)

    if (statusText.isNullOrBlank() && resolvedUnixTime == null && resolvedDate.isNullOrBlank()) {
        return null
    }

    val (releaseDateFormatted, dayOfWeek) = formatAiringDateTime(resolvedUnixTime)
    val (displayEpisodeShort, displayEpisodeLong) = parseEpisodeDisplayLabels(resolvedEpisode)

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
