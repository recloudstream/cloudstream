package com.lagradost.cloudstream3.movieproviders

import android.text.Html
import com.fasterxml.jackson.annotation.*
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import org.json.JSONObject
import java.net.URI
import java.security.MessageDigest

data class Moviedata(
    @JsonProperty("id") val id: Long,
    @JsonProperty("name") val name: String,
    @JsonProperty("type") val type: String,
    @JsonProperty("release_date") val releaseDate: String,
    @JsonProperty("seasons_count") val seasonsCount: Long? = null,
    @JsonProperty("genres") val genres: List<Genre>,
    @JsonProperty("votes") val votes: List<Vote>,
    @JsonProperty("runtime") val runtime: Long? = null
)

data class Genre(
    @JsonProperty("name") val name: String,
    @JsonProperty("pivot") val pivot: Pivot,
)

data class Pivot(
    @JsonProperty("titleID") val titleID: Long,
    @JsonProperty("genreID") val genreID: Long,
)

data class Vote(
    @JsonProperty("title_id") val title_id: Long,
    @JsonProperty("average") val average: String,
    @JsonProperty("count") val count: Long,
    @JsonProperty("type") val type: String,
)

data class VideoElement(
    @JsonProperty("id") val id: Long,
    @JsonProperty("slug") val slug: String,
    @JsonProperty("images") val images: List<Image>,
)

data class Image(
    @JsonProperty("imageable_id") val imageableID: Long,
    @JsonProperty("imageable_type") val imageableType: String,
    @JsonProperty("server_id") val serverID: Long,
    @JsonProperty("proxy_id") val proxyID: Long,
    @JsonProperty("url") val url: String,
    @JsonProperty("type") val type: String,
//    @JsonProperty("sc_url") val scURL: String,
//    @JsonProperty("proxy") val proxy: Proxy,
//    @JsonProperty("server") val server: Proxy
)

// Proxy is not used and crashes otherwise

//data class Proxy(
//    @JsonProperty("id") val id: Long,
//    @JsonProperty("type") val type: String,
//    @JsonProperty("ip") val ip: String,
//    @JsonProperty("number") val number: Long,
//    @JsonProperty("storage") val storage: Long,
//    @JsonProperty("max_storage") val maxStorage: Long,
//    @JsonProperty("max_conversions") val maxConversions: Any? = null,
//    @JsonProperty("max_publications") val maxPublications: Any? = null,
//    @JsonProperty("created_at") val createdAt: String,
//    @JsonProperty("updated_at") val updatedAt: String,
//    @JsonProperty("upload_bandwidth") val uploadBandwidth: Any? = null,
//    @JsonProperty("upload_bandwidth_limit") val uploadBandwidthLimit: Any? = null
//)

data class Season(
    @JsonProperty("id") val id: Long,
    @JsonProperty("name") val name: String? = "",
    @JsonProperty("plot") val plot: String? = "",
    @JsonProperty("date") val date: String? = "",
    @JsonProperty("number") val number: Long,
    @JsonProperty("title_id") val title_id: Long,
    @JsonProperty("createdAt") val createdAt: String? = "",
    @JsonProperty("updated_at") val updatedAt: String? = "",
    @JsonProperty("episodes") val episodes: List<Episodejson>
)

data class Episodejson(
    @JsonProperty("id") val id: Long,
    @JsonProperty("number") val number: Long,
    @JsonProperty("name") val name: String? = "",
    @JsonProperty("plot") val plot: String? = "",
    @JsonProperty("season_id") val seasonID: Long,
    @JsonProperty("images") val images: List<ImageSeason>
)

data class ImageSeason(
    @JsonProperty("imageable_id") val imageableID: Long,
    @JsonProperty("imageable_type") val imageableType: String,
    @JsonProperty("server_id") val serverID: Long,
    @JsonProperty("proxy_id") val proxyID: Long,
    @JsonProperty("url") val url: String,
    @JsonProperty("type") val type: String,
    @JsonProperty("original_url") val originalURL: String
)

data class TrailerElement(
    @JsonProperty("id") val id: Long? = null,
    @JsonProperty("url") val url: String? = null,
    @JsonProperty("host") val host: String? = null,
    @JsonProperty("videoable_id") val videoableID: Long? = null,
    @JsonProperty("videoable_type") val videoableType: String? = null,
    @JsonProperty("created_at") val createdAt: String? = null,
    @JsonProperty("updated_at") val updatedAt: String? = null,
    @JsonProperty("size") val size: String? = null,
    @JsonProperty("created_by") val createdBy: String? = null,
    @JsonProperty("server_id") val serverID: Long? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("quality") val quality: String? = null,
    @JsonProperty("original_name") val originalName: Any? = null,
    @JsonProperty("views") val views: Long? = null,
    @JsonProperty("public") val public: Long? = null,
    @JsonProperty("proxy_id") val proxyID: Any? = null,
    @JsonProperty("proxy_default_id") val proxyDefaultID: Any? = null,
    @JsonProperty("scws_id") val scwsID: Any? = null
)


class StreamingcommunityProvider : MainAPI() {
    override var lang = "it"
    override var mainUrl = "https://streamingcommunity.best"
    override var name = "Streamingcommunity"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
    )

    private fun translatenumber(num: Int): Int? {
        return when (num) {
            67 -> 1
            71 -> 2
            72 -> 3
            73 -> 4
            74 -> 5
            75 -> 6
            76 -> 7
            77 -> 8
            78 -> 9
            79 -> 10
            133 -> 11
            else -> null
        }
    }

    private fun translateip(num: Int): String? {
        return when (num) {
            16 -> "sc-b1-01.scws-content.net"
            17 -> "sc-b1-02.scws-content.net"
            18 -> "sc-b1-03.scws-content.net"
            85 -> "sc-b1-04.scws-content.net"
            95 -> "sc-b1-05.scws-content.net"
            117 -> "sc-b1-06.scws-content.net"
            141 -> "sc-b1-07.scws-content.net"
            142 -> "sc-b1-08.scws-content.net"
            143 -> "sc-b1-09.scws-content.net"
            144 -> "sc-b1-10.scws-content.net"
            else -> null
        }
    }

    companion object {
        val posterMap = hashMapOf<String, String>()
    }

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val items = ArrayList<HomePageList>()
        val document = app.get(mainUrl).document
        document.select("slider-title").subList(0, 3).map { it ->
            if (it.attr("slider-name") != "In arrivo") {
                val films = it.attr("titles-json")
                val lista = mutableListOf<MovieSearchResponse>()
                val videoData = parseJson<List<VideoElement>>(films)

                videoData.subList(0, 12).map { searchr ->
                    val id = searchr.id
                    val name = searchr.slug
                    val img = searchr.images[0].url
                    val number = translatenumber(searchr.images[0].serverID.toInt())
                    val ip = translateip(searchr.images[0].proxyID.toInt())
                    val posterurl = "https://$ip/images/$number/$img"
                    val videourl = "$mainUrl/titles/$id-$name"
                    posterMap[videourl] = posterurl
                    val data = app.post("$mainUrl/api/titles/preview/$id", referer = mainUrl).text
                    val datajs = parseJson<Moviedata>(data)
                    val type: TvType = if (datajs.type == "movie") {
                        TvType.Movie
                    } else {
                        TvType.TvSeries
                    }

                    lista.add(
                        MovieSearchResponse(
                            datajs.name,
                            videourl,
                            this.name,
                            type,
                            posterurl,
                            datajs.releaseDate.substringBefore("-").filter { it.isDigit() }
                                .toIntOrNull(),
                            null,
                        )
                    )
                }
                items.add(HomePageList(it.attr("slider-name"), lista))
            }
        }
        if (items.size <= 0) throw ErrorLoadingException()
        return HomePageResponse(items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val queryformatted = query.replace(" ", "%20")
        val url = "$mainUrl/search?q=$queryformatted"
        val document = app.get(url).document

        val films =
            document.selectFirst("the-search-page")!!.attr("records-json").replace("&quot;", """"""")

        val searchresults = parseJson<List<VideoElement>>(films)
        return searchresults.map { result ->
            val id = result.id
            val name = result.slug
            val img = result.images[0].url
            val number = translatenumber(result.images[0].serverID.toInt())
            val ip = translateip(result.images[0].proxyID.toInt())
            val data = app.post("$mainUrl/api/titles/preview/$id", referer = mainUrl).text
            val datajs = parseJson<Moviedata>(data)
            val posterurl = "https://$ip/images/$number/$img"
            val videourl = "$mainUrl/titles/$id-$name"
            posterMap[videourl] = posterurl
            if (datajs.type == "movie") {
                val type = TvType.Movie
                MovieSearchResponse(
                    datajs.name,
                    videourl,
                    this.name,
                    type,
                    posterurl,
                    datajs.releaseDate.substringBefore("-").filter { it.isDigit() }.toIntOrNull(),
                    null,
                )
            } else {
                val type = TvType.TvSeries
                TvSeriesSearchResponse(
                    datajs.name,
                    videourl,
                    this.name,
                    type,
                    posterurl,
                    datajs.releaseDate.substringBefore("-").filter { it.isDigit() }.toIntOrNull(),
                    null,
                )
            }

        }

    }

    override suspend fun load(url: String): LoadResponse {

        val document = app.get(url).document
        val poster = posterMap[url]
        val id = url.substringBefore("-").filter { it.isDigit() }
        val data = app.post("$mainUrl/api/titles/preview/$id", referer = mainUrl).text

        val datajs = parseJson<Moviedata>(data)
        val type: TvType = if (datajs.type == "movie") {
            TvType.Movie
        } else {
            TvType.TvSeries
        }
        val trailerinfojs = document.select("slider-trailer").attr("videos")
        val trailerinfo = parseJson<List<TrailerElement>>(trailerinfojs)
        val trailerurl: String? = if (trailerinfo.isNotEmpty()) {
            "https://www.youtube.com/watch?v=${trailerinfo[0].url}"
        } else {
            null
        }

        val year = datajs.releaseDate.substringBefore("-")

        val correlatijs = document.selectFirst("slider-title")!!.attr("titles-json")
        val listacorr = mutableListOf<MovieSearchResponse>()
        val correlatidata = parseJson<List<VideoElement>>(correlatijs)
        val number : Int = if (correlatidata.size<=15) {correlatidata.size} else correlatidata.size-15

        correlatidata.take(number).map { searchr ->
            val idcorr = searchr.id
            val name = searchr.slug
            val img = searchr.images[0].url
            val number = translatenumber(searchr.images[0].serverID.toInt())
            val ip = translateip(searchr.images[0].proxyID.toInt())
            val datacorrel = app.post("$mainUrl/api/titles/preview/$idcorr", referer = mainUrl).text
            val datajscorrel = parseJson<Moviedata>(datacorrel)
            val videourl = "$mainUrl/titles/$idcorr-$name"
            val posterurl = "https://$ip/images/$number/$img"

            posterMap[videourl] = posterurl
            val typecorr: TvType = if (datajscorrel.type == "movie") {
                TvType.Movie
            } else {
                TvType.TvSeries
            }

            listacorr.add(
                MovieSearchResponse(
                    datajscorrel.name,
                    videourl,
                    this.name,
                    typecorr,
                    posterurl,
                    datajscorrel.releaseDate.substringBefore("-").filter { it.isDigit() }
                        .toIntOrNull(),
                    null,
                )
            )
        }

        if (type == TvType.TvSeries) {

            val name = datajs.name
            val episodeList = arrayListOf<Episode>()

            val episodes =
                Html.fromHtml(document.selectFirst("season-select")!!.attr("seasons")).toString()
            val jsonEpisodes = parseJson<List<Season>>(episodes)

            jsonEpisodes.map { seasons ->
                val stagione = seasons.number.toInt()
                val sid = seasons.title_id
                val episodio = seasons.episodes
                episodio.map { ep ->
                    val href = "$mainUrl/watch/$sid?e=${ep.id}"
                    val postimage = if (ep.images.isNotEmpty()) {
                        ep.images.first().originalURL
                    } else {
                        ""
                    }
                    episodeList.add(

                        newEpisode(href) {
                            this.name = ep.name
                            this.season = stagione
                            this.episode = ep.number.toInt()
                            this.description = ep.plot
                            this.posterUrl = postimage
                        }
                    )
                }
            }


            if (episodeList.isEmpty()) throw ErrorLoadingException("No Seasons Found")

            return newTvSeriesLoadResponse(name, url, type, episodeList) {
                this.posterUrl = poster
                this.year = year.filter { it.isDigit() }.toInt()
                this.plot = document.selectFirst("div.plot-wrap > p")!!.text()
                this.duration = datajs.runtime?.toInt()
                this.rating = (datajs.votes[0].average.toFloatOrNull()?.times(1000))?.toInt()
                this.tags = datajs.genres.map { it.name }
                addTrailer(trailerurl)
                this.recommendations = listacorr
            }


        } else {

            return newMovieLoadResponse(
                document.selectFirst("div > div > h1")!!.text(),
                document.select("a.play-hitzone").attr("href"),
                type,
                document.select("a.play-hitzone").attr("href")
            ) {
                posterUrl = fixUrlNull(poster)
                this.year = year.filter { it.isDigit() }.toInt()
                this.plot = document.selectFirst("p.plot")!!.text()
                this.rating = datajs.votes[0].average.toFloatOrNull()?.times(1000)?.toInt()
                this.tags = datajs.genres.map { it.name }
                this.duration = datajs.runtime?.toInt()
                addTrailer(trailerurl)
                this.recommendations = listacorr
            }

        }
    }


    private suspend fun getM3u8Qualities(
        m3u8Link: String,
        referer: String,
        qualityName: String,
    ): List<ExtractorLink> {
        return M3u8Helper.generateM3u8(
            this.name,
            m3u8Link,
            referer,
            name = "${this.name} - $qualityName"
        )
    }


    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val ip = app.get("https://api.ipify.org/").text
        val videors = app.get(data).document
        val scwsidjs = videors.select("video-player").attr("response").replace("&quot;", """"""")
        val jsn = JSONObject(scwsidjs)
        val scwsid = jsn.getString("scws_id")
        val expire = (System.currentTimeMillis() / 1000 + 172800).toString()

        val uno = "$expire$ip Yc8U6r8KjAKAepEA".toByteArray()
        val due = MessageDigest.getInstance("MD5").digest(uno)
        val tre = base64Encode(due)
        val token = tre.replace("=", "").replace("+", "-").replace("/", "_")


        val link = "https://scws.xyz/master/$scwsid?token=$token&expires=$expire&n=1&n=1"
        getM3u8Qualities(link, data, URI(link).host).forEach(callback)
        return true
    }
}
