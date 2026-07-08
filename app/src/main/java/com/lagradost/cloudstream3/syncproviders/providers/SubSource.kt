package com.lagradost.cloudstream3.syncproviders.providers

import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.subtitles.AbstractSubtitleEntities
import com.lagradost.cloudstream3.subtitles.SubtitleResource
import com.lagradost.cloudstream3.syncproviders.AuthData
import com.lagradost.cloudstream3.syncproviders.SubtitleAPI
import com.lagradost.cloudstream3.utils.SubtitleHelper
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.concurrent.TimeUnit

class SubSourceApi : SubtitleAPI() {
    override val name = "SubSource"
    override val idPrefix = "subsource"

    override val requiresLogin = false

    companion object {
        const val APIURL = "https://api.subsource.net/v1"
    }

    override suspend fun search(
        auth: AuthData?,
        query: AbstractSubtitleEntities.SubtitleSearch
    ): List<AbstractSubtitleEntities.SubtitleEntity>? {
        //Only supports Imdb Id search for now
        if (query.imdbId == null) return null
        val queryLang = SubtitleHelper.fromTagToEnglishLanguageName(query.lang)
        val type = if ((query.seasonNumber ?: 0) > 0) TvType.TvSeries else TvType.Movie

        val searchResponse = app.post(
            url = "$APIURL/movie/search",
            json = mapOf(
                "includeSeasons" to false,
                "limit" to 15,
                "query" to query.imdbId!!,
                "signal" to "{}"
            ),
            cacheTime = 120,
            cacheUnit = TimeUnit.MINUTES,
        ).parsedSafe<SearchRoot>() ?: return null

        val firstResult = searchResponse.results.firstOrNull() ?: return null

        val apiResponse = app.get(
            url = "$APIURL${firstResult.link.replace("series", "subtitles")}",
            cacheTime = 120,
            cacheUnit = TimeUnit.MINUTES,
        ).parsedSafe<ItemRoot>() ?: return null

        val filteredSubtitles = apiResponse.subtitles.filter { sub ->
            sub.releaseType != "trailer" &&
                    sub.language.equals(queryLang, true)
        }

        // api doesn't has episode number or lang filtering
        val subtitles = if (type == TvType.Movie) {
            filteredSubtitles
        } else {
            val shouldContain = String.format(
                null,
                "E%02d",
                query.epNumber
            )
            filteredSubtitles.filter { sub ->
                sub.releaseInfo.contains(
                    shouldContain
                )
            }
        }

        return subtitles.map { subtitle ->
            AbstractSubtitleEntities.SubtitleEntity(
                idPrefix = this.idPrefix,
                name = subtitle.releaseInfo,
                lang = subtitle.language,
                data = subtitle.link,
                type = type,
                source = this.name,
                epNumber = query.epNumber,
                seasonNumber = query.seasonNumber,
                isHearingImpaired = subtitle.hearingImpaired == 1,
            )
        }
    }

    override suspend fun SubtitleResource.getResources(
        auth: AuthData?,
        subtitle: AbstractSubtitleEntities.SubtitleEntity
    ) {
        val data = app.get("$APIURL/subtitle/${subtitle.data}")
            .parsedSafe<DownloadRoot>()
            ?: return

        this.addZipUrl(
            "$APIURL/subtitle/download/${data.subtitle.downloadToken}"
        ) { name, _ ->
            name
        }
    }


    @Serializable
    data class SearchRoot(
        @SerialName("success") var success: Boolean? = null,
        @SerialName("results") var results: ArrayList<Results> = arrayListOf(),
        @SerialName("users") var users: ArrayList<Users> = arrayListOf()
    )

    @Serializable
    data class Users(

        @SerialName("id") var id: Int? = null,
        @SerialName("displayname") var displayname: String? = null,
        @SerialName("avatar") var avatar: String? = null,
        @SerialName("badges") var badges: ArrayList<String> = arrayListOf()

    )

    @Serializable
    data class Results(
        @SerialName("id") var id: Int? = null,
        @SerialName("title") var title: String? = null,
        @SerialName("type") var type: String? = null,
        @SerialName("link") var link: String,
        @SerialName("releaseYear") var releaseYear: Int? = null,
        @SerialName("poster") var poster: String? = null,
        @SerialName("subtitleCount") var subtitleCount: String? = null,
        @SerialName("rating") var rating: Double? = null,
        @SerialName("cast") var cast: ArrayList<String> = arrayListOf(),
        @SerialName("genres") var genres: ArrayList<String> = arrayListOf(),
        @SerialName("score") var score: Double? = null
    )

    @Serializable

    data class ItemRoot(

        // @SerialName("media_type" ) var mediaType : String?              = null,
        @SerialName("subtitles") var subtitles: ArrayList<Subtitles>,
        //@SerialName("movie"      ) var movie     : Movie?               = Movie()

    )

    @Serializable
    data class Subtitles(

        @SerialName("id") var id: Int? = null,
        @SerialName("language") var language: String,
        @SerialName("release_type") var releaseType: String? = null,
        @SerialName("release_info") var releaseInfo: String,
        @SerialName("upload_date") var uploadDate: String? = null,
        @SerialName("hearing_impaired") var hearingImpaired: Int? = null,
        @SerialName("caption") var caption: String? = null,
        @SerialName("rating") var rating: String? = null,
        @SerialName("uploader_id") var uploaderId: Int? = null,
        @SerialName("uploader_displayname") var uploaderDisplayname: String? = null,
        @SerialName("uploader_badges") var uploaderBadges: ArrayList<String> = arrayListOf(),
        @SerialName("link") var link: String,
        @SerialName("production_type") var productionType: String? = null,
        @SerialName("last_subtitle") var lastSubtitle: Boolean? = null

    )

    @Serializable
    data class DownloadRoot(
        @SerialName("subtitle") var subtitle: Subtitle,
        //@SerializedName("movie"         ) var movie         : Movie?         = Movie(),
        //@SerializedName("donationLinks" ) var donationLinks : DonationLinks? = DonationLinks(),
        //@SerializedName("isDownloaded"  ) var isDownloaded  : Boolean?       = null,
        //@SerializedName("user_rated"    ) var userRated     : String?        = null
    )

    @Serializable
    data class Subtitle(

        @SerialName("id") var id: Int? = null,
        @SerialName("uploaded_at") var uploadedAt: String? = null,
        @SerialName("language") var language: String? = null,
        @SerialName("rating") var rating: String? = null,
        //SerialName("rates"            ) var rates           : Rates?              = Rates(),
        @SerialName("uploaded_by") var uploadedBy: Int? = null,
        //@SerialName("contribs"         ) var contribs        : ArrayList<Contribs> = arrayListOf(),
        @SerialName("release_info") var releaseInfo: ArrayList<String> = arrayListOf(),
        @SerialName("commentary") var commentary: String? = null,
        @SerialName("files") var files: String? = null,
        @SerialName("size") var size: String? = null,
        @SerialName("downloads") var downloads: Int? = null,
        @SerialName("comments") var comments: Int? = null,
        @SerialName("production_type") var productionType: String? = null,
        @SerialName("release_type") var releaseType: String? = null,
        @SerialName("episode") var episode: String? = null,
        @SerialName("hearing_impaired") var hearingImpaired: Int? = null,
        @SerialName("foreign_parts") var foreignParts: String? = null,
        @SerialName("framerate") var framerate: String? = null,
        @SerialName("preview") var preview: String? = null,
        @SerialName("user_uploaded") var userUploaded: Boolean? = null,
        @SerialName("download_token") var downloadToken: String

    )
}
