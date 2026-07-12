package com.lagradost.cloudstream3.syncproviders.providers

import com.fasterxml.jackson.annotation.JsonProperty
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
        @JsonProperty("success") @SerialName("success") var success: Boolean? = null,
        @JsonProperty("results") @SerialName("results") var results: ArrayList<Results> = arrayListOf(),
        @JsonProperty("users") @SerialName("users") var users: ArrayList<Users> = arrayListOf()
    )

    @Serializable
    data class Users(

        @JsonProperty("id") @SerialName("id") var id: Int? = null,
        @JsonProperty("displayname") @SerialName("displayname") var displayname: String? = null,
        @JsonProperty("avatar") @SerialName("avatar") var avatar: String? = null,
        @JsonProperty("badges") @SerialName("badges") var badges: ArrayList<String> = arrayListOf()

    )

    @Serializable
    data class Results(
        @JsonProperty("id") @SerialName("id") var id: Int? = null,
        @JsonProperty("title") @SerialName("title") var title: String? = null,
        @JsonProperty("type") @SerialName("type") var type: String? = null,
        @JsonProperty("link") @SerialName("link") var link: String,
        @JsonProperty("releaseYear") @SerialName("releaseYear") var releaseYear: Int? = null,
        @JsonProperty("poster") @SerialName("poster") var poster: String? = null,
        @JsonProperty("subtitleCount") @SerialName("subtitleCount") var subtitleCount: String? = null,
        @JsonProperty("rating") @SerialName("rating") var rating: Double? = null,
        @JsonProperty("cast") @SerialName("cast") var cast: ArrayList<String> = arrayListOf(),
        @JsonProperty("genres") @SerialName("genres") var genres: ArrayList<String> = arrayListOf(),
        @JsonProperty("score") @SerialName("score") var score: Double? = null
    )

    @Serializable

    data class ItemRoot(

        // @SerialName("media_type" ) var mediaType : String?              = null,
        @JsonProperty("subtitles") @SerialName("subtitles") var subtitles: ArrayList<Subtitles>,
        //@SerialName("movie"      ) var movie     : Movie?               = Movie()

    )

    @Serializable
    data class Subtitles(

        @JsonProperty("id") @SerialName("id") var id: Int? = null,
        @JsonProperty("language") @SerialName("language") var language: String,
        @JsonProperty("release_type") @SerialName("release_type") var releaseType: String? = null,
        @JsonProperty("release_info") @SerialName("release_info") var releaseInfo: String,
        @JsonProperty("upload_date") @SerialName("upload_date") var uploadDate: String? = null,
        @JsonProperty("hearing_impaired") @SerialName("hearing_impaired") var hearingImpaired: Int? = null,
        @JsonProperty("caption") @SerialName("caption") var caption: String? = null,
        @JsonProperty("rating") @SerialName("rating") var rating: String? = null,
        @JsonProperty("uploader_id") @SerialName("uploader_id") var uploaderId: Int? = null,
        @JsonProperty("uploader_displayname") @SerialName("uploader_displayname") var uploaderDisplayname: String? = null,
        @JsonProperty("uploader_badges") @SerialName("uploader_badges") var uploaderBadges: ArrayList<String> = arrayListOf(),
        @JsonProperty("link") @SerialName("link") var link: String,
        @JsonProperty("production_type") @SerialName("production_type") var productionType: String? = null,
        @JsonProperty("last_subtitle") @SerialName("last_subtitle") var lastSubtitle: Boolean? = null

    )

    @Serializable
    data class DownloadRoot(
        @JsonProperty("subtitle") @SerialName("subtitle") var subtitle: Subtitle,
        //@SerializedName("movie"         ) var movie         : Movie?         = Movie(),
        //@SerializedName("donationLinks" ) var donationLinks : DonationLinks? = DonationLinks(),
        //@SerializedName("isDownloaded"  ) var isDownloaded  : Boolean?       = null,
        //@SerializedName("user_rated"    ) var userRated     : String?        = null
    )

    @Serializable
    data class Subtitle(

        @JsonProperty("id") @SerialName("id") var id: Int? = null,
        @JsonProperty("uploaded_at") @SerialName("uploaded_at") var uploadedAt: String? = null,
        @JsonProperty("language") @SerialName("language") var language: String? = null,
        @JsonProperty("rating") @SerialName("rating") var rating: String? = null,
        //SerialName("rates"            ) var rates           : Rates?              = Rates(),
        @JsonProperty("uploaded_by") @SerialName("uploaded_by") var uploadedBy: Int? = null,
        //@SerialName("contribs"         ) var contribs        : ArrayList<Contribs> = arrayListOf(),
        @JsonProperty("release_info") @SerialName("release_info") var releaseInfo: ArrayList<String> = arrayListOf(),
        @JsonProperty("commentary") @SerialName("commentary") var commentary: String? = null,
        @JsonProperty("files") @SerialName("files") var files: String? = null,
        @JsonProperty("size") @SerialName("size") var size: String? = null,
        @JsonProperty("downloads") @SerialName("downloads") var downloads: Int? = null,
        @JsonProperty("comments") @SerialName("comments") var comments: Int? = null,
        @JsonProperty("production_type") @SerialName("production_type") var productionType: String? = null,
        @JsonProperty("release_type") @SerialName("release_type") var releaseType: String? = null,
        @JsonProperty("episode") @SerialName("episode") var episode: String? = null,
        @JsonProperty("hearing_impaired") @SerialName("hearing_impaired") var hearingImpaired: Int? = null,
        @JsonProperty("foreign_parts") @SerialName("foreign_parts") var foreignParts: String? = null,
        @JsonProperty("framerate") @SerialName("framerate") var framerate: String? = null,
        @JsonProperty("preview") @SerialName("preview") var preview: String? = null,
        @JsonProperty("user_uploaded") @SerialName("user_uploaded") var userUploaded: Boolean? = null,
        @JsonProperty("download_token") @SerialName("download_token") var downloadToken: String

    )
}
