package com.lagradost.cloudstream3.syncproviders.providers

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.subtitles.AbstractSubtitleEntities
import com.lagradost.cloudstream3.subtitles.SubtitleResource
import com.lagradost.cloudstream3.syncproviders.AuthData
import com.lagradost.cloudstream3.syncproviders.SubtitleAPI
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.SubtitleHelper.fromLanguageToTagIETF

class SubSourceApi : SubtitleAPI() {
    override val name = "SubSource"
    override val idPrefix = "subsource"

    override val requiresLogin = false

    companion object {
        const val APIURL = "https://api.subsource.net/v1"
        const val DOWNLOADENDPOINT = "https://api.subsource.net/v1/subtitle/download"
    }

    override suspend fun search(
        auth: AuthData?,
        query: AbstractSubtitleEntities.SubtitleSearch
    ): List<AbstractSubtitleEntities.SubtitleEntity>? {

        if (query.query.isNullOrEmpty() && query.imdbId.isNullOrEmpty()) return null
        val queryLangSubSource = langTagIETF2SubSource[query.lang.toString()] ?: ""
        val type = if ((query.seasonNumber ?: 0) > 0) TvType.TvSeries else TvType.Movie

        // 1st search for the movie/series
        val searchRes = app.post(
            url = "$APIURL/movie/search",
            data = mapOf(
                "query" to {query.imdbId ?: query.query}.toString(),
                "includeSeasons" to "false",
                "limit" to "15",
                "signal" to "{}",
            )
        ).parsedSafe<ApiSearch>() ?: return null


        val urlPath = searchRes.results.first().link +
                      if (type != TvType.TvSeries) ""
                      else "/season-${query.seasonNumber ?: 1}"

        // 2nd get subtitles links for that movie/series
        val getMovieRes = app.get(
            url = "$APIURL/$urlPath?language=$queryLangSubSource&sort_by_date=false"
        ).parsedSafe<ApiSubtitlesLinks>().let {
            // api doesn't has episode number or lang filtering
            if (type == TvType.Movie) {
                it?.subtitles?.filter { subtitle ->
                    subtitle.language == queryLangSubSource
                }
            } else {
                it?.subtitles?.filter { subtitle ->
                    subtitle.releaseInfo!!.contains(
                        String.format(
                            null,
                            "E%02d",
                            query.epNumber
                        )
                    ) && subtitle.language == queryLangSubSource
                }
            }
        } ?: return null

        return getMovieRes.map { subtitle ->
            AbstractSubtitleEntities.SubtitleEntity(
                idPrefix = this.idPrefix,
                name = searchRes.results.first().title,
                lang = langTagIETF2SubSource.entries.find { it.value == subtitle.language }?.key ?:
                       fromLanguageToTagIETF(subtitle.language?.replace("_", " ")) ?:
                       subtitle.language!!,
                data = subtitle.link ?: "",
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
        val movieUrlPath = subtitle.data

        // 3rd get the subtitle downloadToken
        val subRes = app.get(
            url = "$APIURL/$movieUrlPath"
        ).parsedSafe<ApiSubDownloadToken>() ?: return

        this.addZipUrl(
            "$DOWNLOADENDPOINT/${subRes.subtitle.downloadToken}"
        ) { name, _ ->
            name
        }
    }

    data class ApiSearch(
        @JsonProperty("success") val success: Boolean,
        @JsonProperty("results") val results: List<results>,
    )

    data class results(
        @JsonProperty("id") val id: Long,
        @JsonProperty("title") val title: String,
        @JsonProperty("type") val type: String, // "movie" or "tvseries"
        @JsonProperty("link") val link: String,
        @JsonProperty("releaseYear") val releaseYear: Int,
        @JsonProperty("poster") val poster: String,
        @JsonProperty("subtitleCount") val subtitleCount: Long?,
        @JsonProperty("rating") val rating: Float?,
        @JsonProperty("cast") val cast: List<String>?,
        @JsonProperty("genres") val genres: List<String>?,
        @JsonProperty("score") val score: Float?,
    )

    data class ApiSubtitlesLinks(
        @JsonProperty("media_type") val mediaType: String, // "movie" or "seasons" or "tvseries"
        @JsonProperty("movie") val movie: Movie,
        @JsonProperty("subtitles") val subtitles: List<Subtitle>?,
        @JsonProperty("seasons") val seasons: List<Seasson>?,
    )

    data class Subtitle(
        @JsonProperty("id") val id: Long?,
        @JsonProperty("uploaded_at") val uploadedAt: String?,
        @JsonProperty("language") val language: String?, // language name with underscore
        @JsonProperty("release_type") val releaseType: String?,
        @JsonProperty("release_info") val releaseInfo: String?, // description could include S01.E01
        @JsonProperty("upload_date") val upload_date: String?,
        @JsonProperty("hearing_impaired") val hearingImpaired: Int?,
        @JsonProperty("caption") val caption: String?,
        @JsonProperty("uploader_id") val uploaderId: Long?,
        @JsonProperty("uploaded_by") val uploadedBy: Long?,
        @JsonProperty("uploader_displayname") val uploaderDisplayname: String?,
        @JsonProperty("uploader_badges") val uploaderBadges: List<String>?,
        @JsonProperty("link") val link: String?,
        @JsonProperty("production_type") val productionType: String?,
        @JsonProperty("last_subtitle") val lastSubtitle: Boolean?,
        @JsonProperty("commentary") val commentary: String?,
        @JsonProperty("files") val files: String?,
        @JsonProperty("size") val size: Long?,
        @JsonProperty("downloads") val downloads: Long?,
        @JsonProperty("foreign_parts") val foreignParts: String?,
        @JsonProperty("framerate") val framerate: String?,
        @JsonProperty("preview") val preview: String?,
        @JsonProperty("user_uploaded") val userUploaded: Boolean?,
        @JsonProperty("rating") val rating: String?,
        @JsonProperty("rates") val rates: Map<String, Long>?,
        @JsonProperty("contribs") val contribs: List<Map<String, Any>>?,
        @JsonProperty("download_token") val downloadToken: String?,
    )

    data class Movie(
        @JsonProperty("id") val id: Long? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("release_year") val releaseYear: Int? = null,
        @JsonProperty("source") val source: String? = null,
        @JsonProperty("source_data") val sourceData: SourceData? = null,
        @JsonProperty("type") val type: String? = null, // "movie" or "tvseries"
        @JsonProperty("cast") val cast: List<String>? = null,
        @JsonProperty("poster") val posterUrl: String? = null,
        @JsonProperty("season") val season: String? = null,
        @JsonProperty("full_link_name") val fullLinkName: String? = null,
        @JsonProperty("link_name") val linkName: String? = null,
    )

    data class SourceData(
        @JsonProperty("endYear") val endYear: String? = null,
        @JsonProperty("genres") val genres: List<String>? = null,
        @JsonProperty("link") val link: String? = null,
        @JsonProperty("rating") val rating: Float? = null,
        @JsonProperty("source") val source: String? = null,
        @JsonProperty("votes") val votes: Long? = null,
    )

    data class Seasson(
        @JsonProperty("season") val season: Int?,
        @JsonProperty("subtitlesCount") val subtitlesCount: Int?,
        @JsonProperty("year") val year: Int?,
        @JsonProperty("poster") val posterUrl: String?,
    )

    data class ApiSubDownloadToken(
        @JsonProperty("isDownloaded") val isDownloaded: Boolean?,
        @JsonProperty("movie") val movie: String,
        @JsonProperty("subtitle") val subtitle: Subtitle,
        @JsonProperty("user_rated") val userRated: String,
    )

    // find `^(?!.*"language").*$\n` and replace by ""
    // find `.*"language":` and replace by ""
    // find `^(.*)(\r?\n\1)+$` and replace by "$1"
    private val langTagIETF2SubSource = mapOf(
       "ar" to "arabic",
       "bg" to "bulgarian",
       "bn" to "bengali",
       "cs" to "czech",
       "da" to "danish",
       "de" to "german",
       "el" to "greek",
       "en" to "english",
       "es" to "spanish",
       "et" to "estonian",
       "fa" to "farsi_persian",
       "fi" to "finnish",
       "fi" to "french",
       "he" to "hebrew",
       "hr" to "croatian",
       "hu" to "hungarian",
       "id" to "indonesian",
       "is" to "icelandic",
       "it" to "italian",
       "ja" to "japanese",
       "kl" to "greenlandic",
       "ko" to "korean",
       "ku" to "kurdish",
       "mk" to "macedonian",
       "ml" to "malayalam",
       "ms" to "malay",
       "nl" to "dutch",
       "no" to "norwegian",
       "pl" to "polish",
       "pt-br" to "brazilian_portuguese",
       "pt" to "portuguese",
       "ro" to "romanian",
       "ru" to "russian",
       "si" to "sinhala",
       "sk" to "slovak",
       "sl" to "slovenian",
       "sq" to "albanian",
       "sr" to "serbian",
       "su" to "sundanese",
       "sv" to "swedish",
       "th" to "thai",
       "tr" to "turkish",
       "ur" to "urdu",
       "vi" to "vietnamese",
       "zh-hant" to "big_5_code",
       "zh" to "chinese_bg_code",
    )
}