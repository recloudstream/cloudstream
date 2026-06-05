package com.lagradost.cloudstream3.syncproviders.providers

import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.subtitles.AbstractSubtitleEntities
import com.lagradost.cloudstream3.subtitles.SubtitleResource
import com.lagradost.cloudstream3.syncproviders.AuthData
import com.lagradost.cloudstream3.syncproviders.SubtitleAPI
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.SubtitleHelper
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
        val queryLang = SubtitleHelper.fromTagToEnglishLanguageName(query.lang)
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

    @Serializable
    data class ApiSearch(
        @SerialName("success") val success: Boolean,
        @SerialName("found") val found: List<Found>,
    )

    @Serializable
    data class Found(
        @SerialName("id") val id: Long,
        @SerialName("title") val title: String,
        @SerialName("seasons") val seasons: Long,
        @SerialName("type") val type: String,
        @SerialName("releaseYear") val releaseYear: Long,
        @SerialName("linkName") val linkName: String,
    )

    @Serializable
    data class ApiResponse(
        @SerialName("success") val success: Boolean,
        @SerialName("movie") val movie: Movie,
        @SerialName("subs") val subs: List<Sub>,
    )

    @Serializable
    data class Movie(
        @SerialName("id") val id: Long? = null,
        @SerialName("type") val type: String? = null,
        @SerialName("year") val year: Long? = null,
        @SerialName("fullName") val fullName: String? = null,
    )

    @Serializable
    data class Sub(
        @SerialName("hi") val hi: Int? = null,
        @SerialName("fullLink") val fullLink: String? = null,
        @SerialName("linkName") val linkName: String? = null,
        @SerialName("lang") val lang: String? = null,
        @SerialName("releaseName") val releaseName: String? = null,
        @SerialName("subId") val subId: Long? = null,
    )

    @Serializable
    data class SubData(
        @SerialName("movie") val movie: String,
        @SerialName("lang") val lang: String,
        @SerialName("id") val id: String,
    )

    @Serializable
    data class SubTitleLink(
        @SerialName("sub") val sub: SubToken,
    )

    @Serializable
    data class SubToken(
        @SerialName("downloadToken") val downloadToken: String,
    )
}
