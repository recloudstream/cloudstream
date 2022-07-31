package com.lagradost.cloudstream3.movieproviders

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.animeproviders.GogoanimeProvider.Companion.getStatus
import com.lagradost.cloudstream3.utils.DataStore.toKotlinObject
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName
import java.net.URI

class AsiaFlixProvider : MainAPI() {
    override var mainUrl = "https://asiaflix.app"
    override var name = "AsiaFlix"
    override val hasQuickSearch = false
    override val hasMainPage = true
    override val hasChromecastSupport = false
    override val supportedTypes = setOf(TvType.AsianDrama)

    private val apiUrl = "https://api.asiaflix.app/api/v2"

    data class DashBoardObject(
        @JsonProperty("sectionName") val sectionName: String,
        @JsonProperty("type") val type: String?,
        @JsonProperty("data") val data: List<Data>?
    )

    data class Episodes(
        @JsonProperty("_id") val _id: String,
        @JsonProperty("epUrl") val epUrl: String?,
        @JsonProperty("number") val number: Int?,
        @JsonProperty("type") val type: String?,
        @JsonProperty("extracted") val extracted: String?,
        @JsonProperty("videoUrl") val videoUrl: String?
    )


    data class Data(
        @JsonProperty("_id") val _id: String,
        @JsonProperty("name") val name: String,
        @JsonProperty("altNames") val altNames: String?,
        @JsonProperty("image") val image: String?,
        @JsonProperty("tvStatus") val tvStatus: String?,
        @JsonProperty("genre") val genre: String?,
        @JsonProperty("releaseYear") val releaseYear: Int?,
        @JsonProperty("createdAt") val createdAt: Long?,
        @JsonProperty("episodes") val episodes: List<Episodes>?,
        @JsonProperty("views") val views: Int?
    )


    data class DramaPage(
        @JsonProperty("_id") val _id: String,
        @JsonProperty("name") val name: String,
        @JsonProperty("altNames") val altNames: String?,
        @JsonProperty("synopsis") val synopsis: String?,
        @JsonProperty("image") val image: String?,
        @JsonProperty("language") val language: String?,
        @JsonProperty("dramaUrl") val dramaUrl: String?,
        @JsonProperty("published") val published: Boolean?,
        @JsonProperty("tvStatus") val tvStatus: String?,
        @JsonProperty("firstAirDate") val firstAirDate: String?,
        @JsonProperty("genre") val genre: String?,
        @JsonProperty("releaseYear") val releaseYear: Int?,
        @JsonProperty("createdAt") val createdAt: Long?,
        @JsonProperty("modifiedAt") val modifiedAt: Long?,
        @JsonProperty("episodes") val episodes: List<Episodes>,
        @JsonProperty("__v") val __v: Int?,
        @JsonProperty("cdnImage") val cdnImage: String?,
        @JsonProperty("views") val views: Int?
    )

    private fun Data.toSearchResponse(): TvSeriesSearchResponse {
        return TvSeriesSearchResponse(
            name,
            _id,
            this@AsiaFlixProvider.name,
            TvType.AsianDrama,
            image,
            releaseYear,
            episodes?.size,
        )
    }

    private fun Episodes.toEpisode(): Episode? {
        if (videoUrl != null && videoUrl.contains("watch/null") || number == null) return null
        return videoUrl?.let {
            Episode(
                it,
                null,
                number,
            )
        }
    }

    private fun DramaPage.toLoadResponse(): TvSeriesLoadResponse {
        return TvSeriesLoadResponse(
            name,
            "$mainUrl$dramaUrl/$_id".replace("drama-detail", "show-details"),
            this@AsiaFlixProvider.name,
            TvType.AsianDrama,
            episodes.mapNotNull { it.toEpisode() }.sortedBy { it.episode },
            image,
            releaseYear,
            synopsis,
            getStatus(tvStatus ?: ""),
            null,
            genre?.split(",")?.map { it.trim() }
        )
    }

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val headers = mapOf("X-Requested-By" to "asiaflix-web")
        val response = app.get("$apiUrl/dashboard", headers = headers).text

        val customMapper =
            mapper.copy().configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true)
        // Hack, because it can either be object or a list
        val cleanedResponse = Regex(""""data":(\{.*?),\{"sectionName"""").replace(response) {
            """"data":null},{"sectionName""""
        }

        val dashBoard = customMapper.readValue<List<DashBoardObject>?>(cleanedResponse)

        val listItems = dashBoard?.mapNotNull {
            it.data?.map { data ->
                data.toSearchResponse()
            }?.let { searchResponse ->
                HomePageList(it.sectionName, searchResponse)
            }
        }
        return HomePageResponse(listItems ?: listOf())
    }

    data class Link(
        @JsonProperty("url") val url: String?,
    )

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (isCasting) return false
        val headers = mapOf("X-Requested-By" to "asiaflix-web")
        app.get(
            "$apiUrl/utility/get-stream-links?url=$data",
            headers = headers
        ).text.toKotlinObject<Link>().url?.let {
//            val fixedUrl = "https://api.asiaflix.app/api/v2/utility/cors-proxy/playlist/${URLEncoder.encode(it, StandardCharsets.UTF_8.toString())}"
            callback.invoke(
                ExtractorLink(
                    name,
                    name,
                    it,
                    "https://asianload1.com/",
                    /** <------ This provider should be added instead */
                    getQualityFromName(it),
                    URI(it).path.endsWith(".m3u8")
                )
            )
        }
        return true
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        val headers = mapOf("X-Requested-By" to "asiaflix-web")
        val url = "$apiUrl/drama/search?q=$query"
        val response = app.get(url, headers = headers).text
        return mapper.readValue<List<Data>?>(response)?.map { it.toSearchResponse() }
    }

    override suspend fun load(url: String): LoadResponse {
        val headers = mapOf("X-Requested-By" to "asiaflix-web")
        val requestUrl = "$apiUrl/drama?id=${url.split("/").lastOrNull()}"
        val response = app.get(requestUrl, headers = headers).text
        val dramaPage = response.toKotlinObject<DramaPage>()
        return dramaPage.toLoadResponse()
    }
}
