package com.lagradost.cloudstream3.movieproviders

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.loadExtractor
import java.util.ArrayList

class KisskhProvider : MainAPI() {
    override var mainUrl = "https://kisskh.me"
    override var name = "Kisskh"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.AsianDrama,
        TvType.Anime
    )

    override val mainPage = mainPageOf(
        "&type=2&sub=0&country=2&status=0&order=1" to "Movie Pupular",
        "&type=2&sub=0&country=2&status=0&order=2" to "Movie Last Update",
        "&type=1&sub=0&country=2&status=0&order=1" to "TVSeries Popular",
        "&type=1&sub=0&country=2&status=0&order=2" to "TVSeries Last Update",
        "&type=3&sub=0&country=0&status=0&order=1" to "Anime Popular",
        "&type=3&sub=0&country=0&status=0&order=2" to "Anime Last Update",
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val home = app.get("$mainUrl/api/DramaList/List?page=$page${request.data}")
            .parsedSafe<Responses>()?.data
            ?.mapNotNull { media ->
                media.toSearchResponse()
            } ?: throw ErrorLoadingException("Invalid Json reponse")
        return newHomePageResponse(request.name, home)
    }

    private fun Media.toSearchResponse(): SearchResponse? {

        return newAnimeSearchResponse(
            title ?: return null,
            "$title/$id",
            TvType.TvSeries,
        ) {
            this.posterUrl = thumbnail
            addSub(episodesCount)
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse =
            app.get("$mainUrl/api/DramaList/Search?q=$query&type=0", referer = "$mainUrl/").text
        return tryParseJson<ArrayList<Media>>(searchResponse)?.mapNotNull { media ->
            media.toSearchResponse()
        } ?: throw ErrorLoadingException("Invalid Json reponse")
    }

    private fun getTitle(str: String): String {
        return str.replace(Regex("[^a-zA-Z0-9]"), "-")
    }

    override suspend fun load(url: String): LoadResponse? {
        val id = url.split("/")
        val res = app.get(
            "$mainUrl/api/DramaList/Drama/${id.last()}?isq=false",
            referer = "$mainUrl/Drama/${
                getTitle(id.first())
            }?id=${id.last()}"
        ).parsedSafe<MediaDetail>()
            ?: throw ErrorLoadingException("Invalid Json reponse")

        val episodes = res.episodes?.map { eps ->
            Episode(
                data = Data(res.title, eps.number, res.id, eps.id).toJson(),
                episode = eps.number
            )
        } ?: throw ErrorLoadingException("No Episode")

        return newTvSeriesLoadResponse(
            res.title ?: return null,
            url,
            if (res.type == "Movie" || episodes.size == 1) TvType.Movie else TvType.TvSeries,
            episodes
        ) {
            this.posterUrl = res.thumbnail
            this.year = res.releaseDate?.split("-")?.first()?.toIntOrNull()
            this.plot = res.description
            this.tags = listOf("${res.country}", "${res.status}", "${res.type}")
            this.showStatus = when (res.status) {
                "Completed" -> ShowStatus.Completed
                "Ongoing" -> ShowStatus.Ongoing
                else -> null
            }
        }

    }

    private fun getLanguage(str: String): String {
        return when (str) {
            "Indonesia" -> "Indonesian"
            else -> str
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val loadData = parseJson<Data>(data)

        app.get(
            "$mainUrl/api/DramaList/Episode/${loadData.epsId}.png?err=false&ts=&time=",
            referer = "$mainUrl/Drama/${getTitle("${loadData.title}")}/Episode-${loadData.eps}?id=${loadData.id}&ep=${loadData.epsId}&page=0&pageSize=100"
        ).parsedSafe<Sources>()?.let { source ->
            listOf(source.video, source.thirdParty).apmap { link ->
                safeApiCall {
                    if (link?.contains(".m3u8") == true) {
                        M3u8Helper.generateM3u8(
                            this.name,
                            link,
                            referer = "$mainUrl/",
                            headers = mapOf("Origin" to mainUrl)
                        ).forEach(callback)
                    } else {
                        loadExtractor(
                            link?.substringBefore("=http") ?: return@safeApiCall,
                            "$mainUrl/",
                            subtitleCallback,
                            callback
                        )
                    }
                }
            }
        }

        // parsedSafe doesn't work in <List<Object>>
        app.get("$mainUrl/api/Sub/${loadData.epsId}").text.let { res ->
            tryParseJson<List<Subtitle>>(res)?.map { sub ->
                subtitleCallback.invoke(
                    SubtitleFile(
                        getLanguage(sub.label ?: return@map),
                        sub.src ?: return@map
                    )
                )
            }
        }

        return true

    }

    data class Data(
        val title: String?,
        val eps: Int?,
        val id: Int?,
        val epsId: Int?,
    )

    data class Sources(
        @JsonProperty("Video") val video: String?,
        @JsonProperty("ThirdParty") val thirdParty: String?,
    )

    data class Subtitle(
        @JsonProperty("src") val src: String?,
        @JsonProperty("label") val label: String?,
    )

    data class Responses(
        @JsonProperty("data") val data: ArrayList<Media>? = arrayListOf(),
    )

    data class Media(
        @JsonProperty("episodesCount") val episodesCount: Int?,
        @JsonProperty("thumbnail") val thumbnail: String?,
        @JsonProperty("id") val id: Int?,
        @JsonProperty("title") val title: String?,
    )

    data class Episodes(
        @JsonProperty("id") val id: Int?,
        @JsonProperty("number") val number: Int?,
        @JsonProperty("sub") val sub: Int?,
    )

    data class MediaDetail(
        @JsonProperty("description") val description: String?,
        @JsonProperty("releaseDate") val releaseDate: String?,
        @JsonProperty("status") val status: String?,
        @JsonProperty("type") val type: String?,
        @JsonProperty("country") val country: String?,
        @JsonProperty("episodes") val episodes: ArrayList<Episodes>? = arrayListOf(),
        @JsonProperty("thumbnail") val thumbnail: String?,
        @JsonProperty("id") val id: Int?,
        @JsonProperty("title") val title: String?,
    )

}