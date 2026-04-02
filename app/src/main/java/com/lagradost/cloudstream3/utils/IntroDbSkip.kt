package com.lagradost.cloudstream3.utils

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.lagradost.api.Log
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.getImdbId
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.ui.result.ResultEpisode

object IntroDbSkip {
    private const val TAG = "IntroDb"

    private fun shouldSkipToNextEpisode(endMs: Long, episodeDurationMs: Long): Boolean {
        return episodeDurationMs - endMs < 20_000L
    }

    suspend fun getResult(
        imdbId: String,
        season: Int,
        episode: Int,
    ): IntroDbResponse? {
        return try {
            val url = "https://api.introdb.app/segments?imdb_id=$imdbId&season=$season&episode=$episode&segment_type=intro"
            app.get(url).parsed<IntroDbResponse>()
        } catch (t: Throwable) {
            Log.i(TAG, "error = ${t.message}")
            logError(t)
            null
        }
    }

    private fun Segment?.toSkipStamp(
        type: EpisodeSkip.SkipType,
        episodeDurationMs: Long,
        hasNextEpisode: Boolean,
    ): EpisodeSkip.SkipStamp? {
        val segment = this ?: return null
        val startMs = segment.startMs ?: return null
        val endMs = segment.endMs ?: return null
        return EpisodeSkip.SkipStamp(
            type = type,
            skipToNextEpisode = hasNextEpisode && shouldSkipToNextEpisode(
                endMs,
                episodeDurationMs
            ),
            startMs = startMs,
            endMs = endMs
        )
    }

    suspend fun getStamps(
        data: LoadResponse,
        episode: ResultEpisode,
        episodeDurationMs: Long,
        hasNextEpisode: Boolean,
    ): List<EpisodeSkip.SkipStamp> {
        val season = episode.season ?: return emptyList()
        val episodeNumber = episode.episode
        val imdbId = data.getImdbId() ?: return emptyList()

        val result = getResult(
            imdbId = imdbId,
            season = season,
            episode = episodeNumber
        ) ?: return emptyList()

        return listOfNotNull(
            result.intro.toSkipStamp(
                EpisodeSkip.SkipType.Opening,
                episodeDurationMs,
                hasNextEpisode
            ),
            result.recap.toSkipStamp(
                EpisodeSkip.SkipType.Recap,
                episodeDurationMs,
                hasNextEpisode
            ),
            result.outro.toSkipStamp(
                EpisodeSkip.SkipType.Credits,
                episodeDurationMs,
                hasNextEpisode
            )
        )
    }

    data class IntroDbResponse(
        @JsonProperty("imdb_id") @JsonSerialize val imdbId: String?,
        @JsonSerialize val season: Int?,
        @JsonSerialize val episode: Int?,
        @JsonSerialize val intro: Segment?,
        @JsonSerialize val recap: Segment?,
        @JsonSerialize val outro: Segment?,
    )

    data class Segment(
        @JsonProperty("start_sec") @JsonSerialize val startSec: Double?,
        @JsonProperty("end_sec") @JsonSerialize val endSec: Double?,
        @JsonProperty("start_ms") @JsonSerialize val startMs: Long?,
        @JsonProperty("end_ms") @JsonSerialize val endMs: Long?,
        @JsonSerialize val confidence: Double?,
        @JsonProperty("submission_count") @JsonSerialize val submissionCount: Int?,
        @JsonProperty("updated_at") @JsonSerialize val updatedAt: String?,
    )
}
