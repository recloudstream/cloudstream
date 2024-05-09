package com.lagradost.cloudstream3.syncproviders.providers

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.subtitles.AbstractSubProvider
import com.lagradost.cloudstream3.subtitles.AbstractSubtitleEntities
import com.lagradost.cloudstream3.subtitles.SubtitleResource
import com.lagradost.cloudstream3.utils.AppUtils

class SubDL : AbstractSubProvider {
    //API Documentation: https://subdl.com/api-doc
    val mainUrl = "https://subdl.com/"
    val name = "SubDL"
    override val idPrefix = "subdl"
    companion object {
        const val APIKEY = "zRJl5QA-8jNA2i0pE8cxANbEukANp7IM"
        const val APIENDPOINT = "https://api.subdl.com/api/v1/subtitles"
        const val DOWNLOADENDPOINT = "https://dl.subdl.com"
    }

    override suspend fun search(query: AbstractSubtitleEntities.SubtitleSearch): List<AbstractSubtitleEntities.SubtitleEntity>? {

        val imdbId = query.imdb
        val queryText = query.query
        val epNum = query.epNumber ?: 0
        val seasonNum = query.seasonNumber ?: 0
        val yearNum = query.year ?: 0

        val epQuery = if (epNum > 0) "&episode_number=$epNum" else ""
        val seasonQuery = if (seasonNum > 0) "&season_number=$seasonNum" else ""
        val yearQuery = if (yearNum > 0) "&year=$yearNum" else ""

        val searchQueryUrl = when (imdbId) {
            //Use imdb_id to search if its valid
            null -> "$APIENDPOINT?api_key=$APIKEY&film_name=$queryText&languages=${query.lang}$epQuery$seasonQuery$yearQuery"
            else -> "$APIENDPOINT?api_key=$APIKEY&imdb_id=$imdbId&languages=${query.lang}$epQuery$seasonQuery$yearQuery"
        }

        val req = app.get(
            url = searchQueryUrl,
            headers = mapOf(
                "Accept" to "application/json"
            )
        )

        if (!req.isSuccessful) {
            return null
        }

        val results = mutableListOf<AbstractSubtitleEntities.SubtitleEntity>()

        AppUtils.tryParseJson<ApiResponse>(req.text)?.let {resp ->

            resp.subtitles?.forEach { subtitle ->

                val name = subtitle.releaseName
                val lang = subtitle.lang.replaceFirstChar { it.uppercase() }
                val resEpNum = subtitle.episode ?: query.epNumber
                val resSeasonNum = subtitle.season ?: query.seasonNumber
                val year = resp.results?.firstOrNull()?.year ?: query.year
                val type = if ((resSeasonNum ?: 0) > 0) TvType.TvSeries else TvType.Movie

                results.add(
                    AbstractSubtitleEntities.SubtitleEntity(
                        idPrefix = this.idPrefix,
                        name = name,
                        lang = lang,
                        data = "${DOWNLOADENDPOINT}${subtitle.url}",
                        type = type,
                        source = this.name,
                        epNumber = resEpNum,
                        seasonNumber = resSeasonNum,
                        year = year,
                    )
                )
            }
        }

        return results
    }

    override suspend fun SubtitleResource.getResources(data: AbstractSubtitleEntities.SubtitleEntity) {
        this.addZipUrl(data.data) { name, _ ->
            name
        }
    }

    data class ApiResponse(
        @JsonProperty("status") val status: Boolean? = null,
        @JsonProperty("results") val results: List<Result>? = null,
        @JsonProperty("subtitles") val subtitles: List<Subtitle>? = null,
    )

    data class Result(
        @JsonProperty("sd_id") val sdId: Int? = null,
        @JsonProperty("type") val type: String? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("imdb_id") val imdbId: String? = null,
        @JsonProperty("tmdb_id") val tmdbId: Long? = null,
        @JsonProperty("first_air_date") val firstAirDate: String? = null,
        @JsonProperty("year") val year: Int? = null,
    )

    data class Subtitle(
        @JsonProperty("release_name") val releaseName: String,
        @JsonProperty("name") val name: String,
        @JsonProperty("lang") val lang: String,
        @JsonProperty("author") val author: String? = null,
        @JsonProperty("url") val url: String? = null,
        @JsonProperty("season") val season: Int? = null,
        @JsonProperty("episode") val episode: Int? = null,
    )
}