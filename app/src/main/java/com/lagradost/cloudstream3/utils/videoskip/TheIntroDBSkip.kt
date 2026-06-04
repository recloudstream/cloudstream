package com.lagradost.cloudstream3.utils.videoskip

import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.getImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.getTMDbId
import com.lagradost.cloudstream3.LoadResponse.Companion.isMovie
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.app
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** https://theintrodb.org/docs */
class TheIntroDBSkip : SkipAPI() {
    override val name = "TheIntroDB"
    override val supportedTypes = setOf(
        TvType.TvSeries, TvType.Cartoon, TvType.Anime, TvType.Movie,
        TvType.AsianDrama
    )

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
            list.map { stamp ->
                SkipStamp(
                    type,
                    stamp.startMs ?: 0L,
                    stamp.endMs ?: episodeDurationMs
                )
            }
        }.flatten()
    }

    @Serializable
    data class Root(
        @SerialName("tmdb_id") val tmdbId: Long,
        @SerialName("type") val type: String,
        @SerialName("intro") val intro: List<Stamp> = emptyList(),
        @SerialName("recap") val recap: List<Stamp> = emptyList(),
        @SerialName("credits") val credits: List<Stamp> = emptyList(),
        @SerialName("preview") val preview: List<Stamp> = emptyList(),
    )

    @Serializable
    data class Stamp(
        @SerialName("start_ms") val startMs: Long?,
        @SerialName("end_ms") val endMs: Long?,
    )
}