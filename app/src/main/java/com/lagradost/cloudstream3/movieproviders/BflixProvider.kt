package com.lagradost.cloudstream3.movieproviders

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.animeproviders.NineAnimeProvider.Companion.decodeVrf
import com.lagradost.cloudstream3.animeproviders.NineAnimeProvider.Companion.encode
import com.lagradost.cloudstream3.animeproviders.NineAnimeProvider.Companion.encodeVrf
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.Jsoup

open class BflixProvider : MainAPI() {
    override var mainUrl = "https://bflix.ru"
    override var name = "Bflix"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
    )

    //override val uniqueId: Int by lazy { "BflixProvider".hashCode() }

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val items = ArrayList<HomePageList>()
        val soup = app.get("$mainUrl/home").document
        val testa = listOf(
            Pair("Movies", "div.tab-content[data-name=movies] div.filmlist div.item"),
            Pair("Shows", "div.tab-content[data-name=shows] div.filmlist div.item"),
            Pair("Trending", "div.tab-content[data-name=trending] div.filmlist div.item"),
            Pair(
                "Latest Movies",
                "div.container section.bl:contains(Latest Movies) div.filmlist div.item"
            ),
            Pair(
                "Latest TV-Series",
                "div.container section.bl:contains(Latest TV-Series) div.filmlist div.item"
            ),
        )
        for ((name, element) in testa) try {
            val test = soup.select(element).map {
                val title = it.selectFirst("h3 a")!!.text()
                val link = fixUrl(it.selectFirst("a")!!.attr("href"))
                val qualityInfo = it.selectFirst("div.quality")!!.text()
                val quality = getQualityFromString(qualityInfo)
                TvSeriesSearchResponse(
                    title,
                    link,
                    this.name,
                    if (link.contains("/movie/")) TvType.Movie else TvType.TvSeries,
                    it.selectFirst("a.poster img")!!.attr("src"),
                    null,
                    null,
                    quality = quality
                )
            }
            items.add(HomePageList(name, test))
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (items.size <= 0) throw ErrorLoadingException()
        return HomePageResponse(items)
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        val encodedquery = encodeVrf(query, mainKey)
        val url = "$mainUrl/search?keyword=$query&vrf=$encodedquery"
        val html = app.get(url).text
        val document = Jsoup.parse(html)

        return document.select(".filmlist div.item").map {
            val title = it.selectFirst("h3 a")!!.text()
            val href = fixUrl(it.selectFirst("a")!!.attr("href"))
            val image = it.selectFirst("a.poster img")!!.attr("src")
            val isMovie = href.contains("/movie/")
            val qualityInfo = it.selectFirst("div.quality")!!.text()
            val quality = getQualityFromString(qualityInfo)

            if (isMovie) {
                MovieSearchResponse(
                    title,
                    href,
                    this.name,
                    TvType.Movie,
                    image,
                    null,
                    quality = quality
                )
            } else {
                TvSeriesSearchResponse(
                    title,
                    href,
                    this.name,
                    TvType.TvSeries,
                    image,
                    null,
                    null,
                    quality = quality
                )
            }
        }
    }

    data class Response(
        @JsonProperty("html") val html: String
    )

    companion object {
        val mainKey = "OrAimkpzm6phmN3j"
    }

    override suspend fun load(url: String): LoadResponse? {
        val soup = app.get(url).document
        val movieid = soup.selectFirst("div#watch")!!.attr("data-id")
        val movieidencoded = encodeVrf(movieid, mainKey)
        val title = soup.selectFirst("div.info h1")!!.text()
        val description = soup.selectFirst(".info .desc")?.text()?.trim()
        val poster: String? = try {
            soup.selectFirst("img.poster")!!.attr("src")
        } catch (e: Exception) {
            soup.selectFirst(".info .poster img")!!.attr("src")
        }

        val tags = soup.select("div.info .meta div:contains(Genre) a").map { it.text() }
        val vrfUrl = "$mainUrl/ajax/film/servers?id=$movieid&vrf=$movieidencoded"
        println("VRF___ $vrfUrl")
        val episodes = Jsoup.parse(
            app.get(
                vrfUrl
            ).parsed<Response>().html
        ).select("div.episode").map {
            val a = it.selectFirst("a")
            val href = fixUrl(a!!.attr("href"))
            val extraData = a.attr("data-kname").let { str ->
                str.split("-").mapNotNull { subStr -> subStr.toIntOrNull() }
            }
            val isValid = extraData.size == 2
            val episode = if (isValid) extraData.getOrNull(1) else null
            val season = if (isValid) extraData.getOrNull(0) else null

            val eptitle = it.selectFirst(".episode a span.name")!!.text()
            val secondtitle = it.selectFirst(".episode a span")!!.text()
                .replace(Regex("(Episode (\\d+):|Episode (\\d+)-|Episode (\\d+))"), "") ?: ""
            Episode(
                href,
                secondtitle + eptitle,
                season,
                episode,
            )
        }
        val tvType =
            if (url.contains("/movie/") && episodes.size == 1) TvType.Movie else TvType.TvSeries
        val recommendations =
            soup.select("div.bl-2 section.bl div.content div.filmlist div.item")
                .mapNotNull { element ->
                    val recTitle = element.select("h3 a").text() ?: return@mapNotNull null
                    val image = element.select("a.poster img")?.attr("src")
                    val recUrl = fixUrl(element.select("a").attr("href"))
                    MovieSearchResponse(
                        recTitle,
                        recUrl,
                        this.name,
                        if (recUrl.contains("/movie/")) TvType.Movie else TvType.TvSeries,
                        image,
                        year = null
                    )
                }
        val rating = soup.selectFirst(".info span.imdb")?.text()?.toRatingInt()
        val durationdoc = soup.selectFirst("div.info div.meta").toString()
        val durationregex = Regex("((\\d+) min)")
        val yearegex = Regex("<span>(\\d+)</span>")
        val duration = if (durationdoc.contains("na min")) null
        else durationregex.find(durationdoc)?.destructured?.component1()?.replace(" min", "")
            ?.toIntOrNull()
        val year = if (mainUrl == "https://bflix.ru") {
            yearegex.find(durationdoc)?.destructured?.component1()
                ?.replace(Regex("<span>|</span>"), "")
        } else null
        return when (tvType) {
            TvType.TvSeries -> {
                TvSeriesLoadResponse(
                    title,
                    url,
                    this.name,
                    tvType,
                    episodes,
                    poster,
                    year?.toIntOrNull(),
                    description,
                    null,
                    rating,
                    tags,
                    recommendations = recommendations,
                    duration = duration,
                )
            }
            TvType.Movie -> {
                MovieLoadResponse(
                    title,
                    url,
                    this.name,
                    tvType,
                    url,
                    poster,
                    year?.toIntOrNull(),
                    description,
                    rating,
                    tags,
                    recommendations = recommendations,
                    duration = duration
                )
            }
            else -> null
        }
    }


    data class Subtitles(
        @JsonProperty("file") val file: String,
        @JsonProperty("label") val label: String,
        @JsonProperty("kind") val kind: String
    )

    data class Links(
        @JsonProperty("url") val url: String
    )

    data class Servers(
        @JsonProperty("28") val mcloud: String?,
        @JsonProperty("35") val mp4upload: String?,
        @JsonProperty("40") val streamtape: String?,
        @JsonProperty("41") val vidstream: String?,
        @JsonProperty("43") val videovard: String?
    )

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val soup = app.get(data).document

        val movieid = encode(soup.selectFirst("div#watch")?.attr("data-id") ?: return false)
        val movieidencoded = encodeVrf(movieid, mainKey)
        Jsoup.parse(
            parseJson<Response>(
                app.get(
                    "$mainUrl/ajax/film/servers?id=$movieid&vrf=$movieidencoded"
                ).text
            ).html
        )
            .select("html body #episodes").map {
                val cleandata = data.replace(mainUrl, "")
                val a = it.select("a").map {
                    it.attr("data-kname")
                }
                val tvType =
                    if (data.contains("movie/") && a.size == 1) TvType.Movie else TvType.TvSeries
                val servers = if (tvType == TvType.Movie) it.select(".episode a").attr("data-ep")
                else
                    it.select(".episode a[href=$cleandata]").attr("data-ep")
                        ?: it.select(".episode a[href=${cleandata.replace("/1-full", "")}]")
                            .attr("data-ep")
                val jsonservers = parseJson<Servers?>(servers) ?: return@map
                listOfNotNull(
                    jsonservers.vidstream,
                    jsonservers.mcloud,
                    jsonservers.mp4upload,
                    jsonservers.streamtape,
                    jsonservers.videovard,
                ).mapNotNull {
                    val epserver = app.get("$mainUrl/ajax/episode/info?id=$it").text
                    (if (epserver.contains("url")) {
                        parseJson<Links>(epserver)
                    } else null)?.url?.let {
                        decodeVrf(it, mainKey)
                    }
                }.apmap { url ->
                    loadExtractor(
                        url, data, subtitleCallback, callback
                    )
                }
                //Apparently any server works, I haven't found any diference
                val sublink =
                    app.get("$mainUrl/ajax/episode/subtitles/${jsonservers.mcloud}").text
                val jsonsub = parseJson<List<Subtitles>>(sublink)
                jsonsub.forEach { subtitle ->
                    subtitleCallback(
                        SubtitleFile(subtitle.label, subtitle.file)
                    )
                }
            }

        return true
    }
}
