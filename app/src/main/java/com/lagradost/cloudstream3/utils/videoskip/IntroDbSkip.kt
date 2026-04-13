package com.lagradost.cloudstream3.utils.videoskip

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.getImdbId
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.ui.result.ResultEpisode

class IntroDbSkip : SkipAPI() {
    override val name = "IntroDb"

    override val supportedTypes = setOf(TvType.TvSeries, TvType.AsianDrama)

    override suspend fun stamps(
        data: LoadResponse,
        episode: ResultEpisode,
        episodeDurationMs: Long
    ): List<SkipStamp>? {
        val season = episode.season ?: return null
        val imdbId = data.getImdbId() ?: return null

        val url =
            "https://api.introdb.app/segments?imdb_id=$imdbId&season=$season&episode=${episode.episode}"
        val response = app.get(url).parsed<IntroDbResponse>()

        return listOfNotNull(
            response.intro?.let {
                val start = it.startMs ?: return@let null
                val end = it.endMs ?: return@let null
                SkipStamp(
                    type = SkipType.Opening,
                    startMs = start,
                    endMs = end
                )
            },
            response.recap?.let {
                val start = it.startMs ?: return@let null
                val end = it.endMs ?: return@let null
                SkipStamp(
                    type = SkipType.Recap,
                    startMs = start,
                    endMs = end
                )
            },
            response.outro?.let {
                val start = it.startMs ?: return@let null
                val end = it.endMs ?: return@let null
                SkipStamp(
                    type = SkipType.Ending,
                    startMs = start,
                    endMs = end
                )
            }
        )
    }


    data class IntroDbResponse(
        @JsonProperty("imdb_id") val imdbId: String?,
        val season: Int?,
        val episode: Int?,
        val intro: Segment?,
        val recap: Segment?,
        val outro: Segment?,
    )

    data class Segment(
        @JsonProperty("start_sec") val startSec: Double?,
        @JsonProperty("end_sec") val endSec: Double?,
        @JsonProperty("start_ms") val startMs: Long?,
        @JsonProperty("end_ms") val endMs: Long?,
        val confidence: Double?,
        @JsonProperty("submission_count") val submissionCount: Int?,
        @JsonProperty("updated_at") val updatedAt: String?,
    )
}