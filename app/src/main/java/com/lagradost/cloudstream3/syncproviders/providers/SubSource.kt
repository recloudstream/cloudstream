package com.lagradost.cloudstream3.syncproviders.providers

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.subtitles.AbstractSubtitleEntities
import com.lagradost.cloudstream3.subtitles.SubtitleResource
import com.lagradost.cloudstream3.syncproviders.AuthData
import com.lagradost.cloudstream3.syncproviders.SubtitleAPI
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.SubtitleHelper

class SubSourceApi : SubtitleAPI() {
    override val name = "SubSource"
    override val idPrefix = "subsource"

    override val requiresLogin = false

    companion object {
        const val APIURL = "https://api.subsource.net/api"
        const val DOWNLOADENDPOINT = "https://api.subsource.net/api/downloadSub"
    }

    override suspend fun search(
        auth: AuthData?,
        query: AbstractSubtitleEntities.SubtitleSearch
    ): List<AbstractSubtitleEntities.SubtitleEntity>? {

        //Only supports Imdb Id search for now
        if (query.imdbId == null) return null
        val queryLang = SubtitleHelper.fromTwoLettersToLanguage(query.lang!!)
        val type = if ((query.seasonNumber ?: 0) > 0) TvType.TvSeries else TvType.Movie

        val searchRes = app.post(
            url = "$APIURL/searchMovie",
            data = mapOf(
                "query" to query.imdbId!!
            )
        ).parsedSafe<ApiSearch>() ?: return null

        val postData = if (type == TvType.TvSeries) {
            mapOf(
                "langs" to "[]",
                "movieName" to searchRes.found.first().linkName,
                "season" to "season-${query.seasonNumber}"
            )
        } else {
            mapOf(
                "langs" to "[]",
                "movieName" to searchRes.found.first().linkName,
            )
        }

        val getMovieRes = app.post(
            url = "$APIURL/getMovie",
            data = postData
        ).parsedSafe<ApiResponse>().let {
            // api doesn't has episode number or lang filtering
            if (type == TvType.Movie) {
                it?.subs?.filter { sub ->
                    sub.lang == queryLang
                }
            } else {
                it?.subs?.filter { sub ->
                    sub.releaseName!!.contains(
                        String.format(
                            null,
                            "E%02d",
                            query.epNumber
                        )
                    ) && sub.lang == queryLang
                }
            }
        } ?: return null

        return getMovieRes.map { subtitle ->
            AbstractSubtitleEntities.SubtitleEntity(
                idPrefix = this.idPrefix,
                name = subtitle.releaseName!!,
                lang = subtitle.lang!!,
                data = SubData(
                    movie = subtitle.linkName!!,
                    lang = subtitle.lang,
                    id = subtitle.subId.toString(),
                ).toJson(),
                type = type,
                source = this.name,
                epNumber = query.epNumber,
                seasonNumber = query.seasonNumber,
                isHearingImpaired = subtitle.hi == 1,
            )
        }
    }

    override suspend fun SubtitleResource.getResources(
        auth: AuthData?,
        subtitle: AbstractSubtitleEntities.SubtitleEntity
    ) {
        val parsedSub = parseJson<SubData>(subtitle.data)

        val subRes = app.post(
            url = "$APIURL/getSub",
            data = mapOf(
                "movie" to parsedSub.movie,
                "lang" to subtitle.lang,
                "id" to parsedSub.id
            )
        ).parsedSafe<SubTitleLink>() ?: return

        this.addZipUrl(
            "$DOWNLOADENDPOINT/${subRes.sub.downloadToken}"
        ) { name, _ ->
            name
        }
    }

    data class ApiSearch(
        @JsonProperty("success") val success: Boolean,
        @JsonProperty("found") val found: List<Found>,
    )

    data class Found(
        @JsonProperty("id") val id: Long,
        @JsonProperty("title") val title: String,
        @JsonProperty("seasons") val seasons: Long,
        @JsonProperty("type") val type: String,
        @JsonProperty("releaseYear") val releaseYear: Long,
        @JsonProperty("linkName") val linkName: String,
    )

    data class ApiResponse(
        @JsonProperty("success") val success: Boolean,
        @JsonProperty("movie") val movie: Movie,
        @JsonProperty("subs") val subs: List<Sub>,
    )

    data class Movie(
        @JsonProperty("id") val id: Long? = null,
        @JsonProperty("type") val type: String? = null,
        @JsonProperty("year") val year: Long? = null,
        @JsonProperty("fullName") val fullName: String? = null,
    )

    data class Sub(
        @JsonProperty("hi") val hi: Int? = null,
        @JsonProperty("fullLink") val fullLink: String? = null,
        @JsonProperty("linkName") val linkName: String? = null,
        @JsonProperty("lang") val lang: String? = null,
        @JsonProperty("releaseName") val releaseName: String? = null,
        @JsonProperty("subId") val subId: Long? = null,
    )

    data class SubData(
        @JsonProperty("movie") val movie: String,
        @JsonProperty("lang") val lang: String,
        @JsonProperty("id") val id: String,
    )

    data class SubTitleLink(
        @JsonProperty("sub") val sub: SubToken,
    )

    data class SubToken(
        @JsonProperty("downloadToken") val downloadToken: String,
    )
}