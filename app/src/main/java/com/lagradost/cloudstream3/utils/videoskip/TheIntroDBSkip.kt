package com.lagradost.cloudstream3.utils.videoskip

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.getImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.getTMDbId
import com.lagradost.cloudstream3.LoadResponse.Companion.isMovie
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.app

/** https://theintrodb.org/docs */
class TheIntroDBSkip : SkipAPI() {
    override val name = "TheIntroDB"
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Cartoon, TvType.Anime, TvType.Movie)

    val mainUrl = "https://api.theintrodb.org"

    override suspend fun stamps(
        data: LoadResponse,
        episode: ResultEpisode,
        episodeDurationMs: Long
    ): List<SkipStamp>? {
        val idSuffix =
            data.getTMDbId()?.let { tmdbId -> "tmdb_id=$tmdbId" }
                ?: data.getImdbId()?.let { imdbId -> "imdb_id=$imdbId" }
                ?: return null

        val url = if (data.isMovie()) {
            "$mainUrl/v2/media?$idSuffix"
        } else {
            val season = episode.season ?: return null
            "$mainUrl/v2/media?$idSuffix&season=$season&episode=${episode.episode}"
        }
        val root = app.get(url).parsed<Root>()
        return arrayOf(
            root.intro to SkipType.Intro,
            root.credits to SkipType.Credits,
            root.recap to SkipType.Recap,
            root.preview to SkipType.Preview
        ).map { (list, type) ->
            list.map { stamp -> SkipStamp(type, stamp.startMs, stamp.endMs) }
        }.flatten()
    }

    data class Root(
        @JsonProperty("tmdb_id")
        val tmdbId: Long,
        @JsonProperty("type")
        val type: String,
        @JsonProperty("intro")
        val intro: List<Stamp> = emptyList(),
        @JsonProperty("recap")
        val recap: List<Stamp> = emptyList(),
        @JsonProperty("credits")
        val credits: List<Stamp> = emptyList(),
        @JsonProperty("preview")
        val preview: List<Stamp> = emptyList(),
    )

    data class Stamp(
        @JsonProperty("start_ms")
        val startMs: Long,
        @JsonProperty("end_ms")
        val endMs: Long,
    )
}