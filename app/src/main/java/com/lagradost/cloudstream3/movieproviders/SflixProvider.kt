package com.lagradost.cloudstream3.movieproviders

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.getQualityFromName
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URI

class SflixProvider(providerUrl: String, providerName: String) : MainAPI() {
    override val mainUrl = providerUrl
    override val name = providerName

    override val hasQuickSearch = false
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val usesWebView = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
    )
    override val vpnStatus = VPNStatus.None

    override suspend fun getMainPage(): HomePageResponse {
        val html = app.get("$mainUrl/home").text
        val document = Jsoup.parse(html)

        val all = ArrayList<HomePageList>()

        val map = mapOf(
            "Trending Movies" to "div#trending-movies",
            "Trending TV Shows" to "div#trending-tv",
        )
        map.forEach {
            all.add(HomePageList(
                it.key,
                document.select(it.value).select("div.film-poster").map { element ->
                    element.toSearchResult()
                }
            ))
        }

        document.select("section.block_area.block_area_home.section-id-02").forEach {
            val title = it.select("h2.cat-heading").text().trim()
            val elements = it.select("div.film-poster").map { element ->
                element.toSearchResult()
            }
            all.add(HomePageList(title, elements))
        }

        return HomePageResponse(all)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search/${query.replace(" ", "-")}"
        val html = app.get(url).text
        val document = Jsoup.parse(html)

        return document.select("div.flw-item").map {
            val title = it.select("h2.film-name").text()
            val href = fixUrl(it.select("a").attr("href"))
            val year = it.select("span.fdi-item").text().toIntOrNull()
            val image = it.select("img").attr("data-src")
            val isMovie = href.contains("/movie/")

            if (isMovie) {
                MovieSearchResponse(
                    title,
                    href,
                    this.name,
                    TvType.Movie,
                    image,
                    year
                )
            } else {
                TvSeriesSearchResponse(
                    title,
                    href,
                    this.name,
                    TvType.TvSeries,
                    image,
                    year,
                    null
                )
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val details = document.select("div.detail_page-watch")
        val img = details.select("img.film-poster-img")
        val posterUrl = img.attr("src")
        val title = img.attr("title")
        val year = Regex("""[Rr]eleased:\s*(\d{4})""").find(
            document.select("div.elements").text()
        )?.groupValues?.get(1)?.toIntOrNull()
        val duration = Regex("""[Dd]uration:\s*(\d*)""").find(
            document.select("div.elements").text()
        )?.groupValues?.get(1)?.trim()?.plus(" min")

        val plot = details.select("div.description").text().replace("Overview:", "").trim()

        val isMovie = url.contains("/movie/")

        // https://sflix.to/movie/free-never-say-never-again-hd-18317 -> 18317
        val idRegex = Regex(""".*-(\d+)""")
        val dataId = details.attr("data-id")
        val id = if (dataId.isNullOrEmpty())
            idRegex.find(url)?.groupValues?.get(1)
                ?: throw RuntimeException("Unable to get id from '$url'")
        else dataId

        if (isMovie) {
            // Movies
            val episodesUrl = "$mainUrl/ajax/movie/episodes/$id"
            val episodes = app.get(episodesUrl).text

            // Supported streams, they're identical
            val sourceIds = Jsoup.parse(episodes).select("a").mapNotNull { element ->
                val sourceId = element.attr("data-id") ?: return@mapNotNull null
                if(element.select("span")?.text()?.trim()?.isValidServer() == true) {
                    "$url.$sourceId".replace("/movie/", "/watch-movie/")
                } else {
                    null
                }
            }

            return newMovieLoadResponse(title, url, TvType.Movie, sourceIds.toJson()) {
                this.year = year
                this.posterUrl = posterUrl
                this.plot = plot
                setDuration(duration)
            }
        } else {
            val seasonsDocument = app.get("$mainUrl/ajax/v2/tv/seasons/$id").document
            val episodes = arrayListOf<TvSeriesEpisode>()

            seasonsDocument.select("div.dropdown-menu.dropdown-menu-model > a")
                .forEachIndexed { season, element ->
                    val seasonId = element.attr("data-id")
                    if (seasonId.isNullOrBlank()) return@forEachIndexed

                    var episode = 0
                    app.get("$mainUrl/ajax/v2/season/episodes/$seasonId").document
                        .select("div.flw-item.film_single-item.episode-item.eps-item")
                        .forEach {
                            val episodeImg = it.select("img") ?: return@forEach
                            val episodeTitle = episodeImg.attr("title") ?: return@forEach
                            val episodePosterUrl = episodeImg.attr("src") ?: return@forEach
                            val episodeData = it.attr("data-id") ?: return@forEach

                            episode++

                            val episodeNum =
                                (it.select("div.episode-number")?.text()
                                    ?: episodeTitle).let { str ->
                                    Regex("""\d+""").find(str)?.groupValues?.firstOrNull()
                                        ?.toIntOrNull()
                                } ?: episode

                            episodes.add(
                                TvSeriesEpisode(
                                    episodeTitle.removePrefix("Episode $episodeNum: "),
                                    season + 1,
                                    episodeNum,
                                    "$url:::$episodeData",
                                    fixUrl(episodePosterUrl)
                                )
                            )
                        }
                }
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = posterUrl
                this.year = year
                this.plot = plot
                setDuration(duration)
            }
        }
    }

    data class Tracks(
        @JsonProperty("file") val file: String?,
        @JsonProperty("label") val label: String?,
        @JsonProperty("kind") val kind: String?
    )

    data class Sources(
        @JsonProperty("file") val file: String?,
        @JsonProperty("type") val type: String?,
        @JsonProperty("label") val label: String?
    )

    data class SourceObject(
        @JsonProperty("sources") val sources: List<Sources?>?,
        @JsonProperty("sources_1") val sources1: List<Sources?>?,
        @JsonProperty("sources_2") val sources2: List<Sources?>?,
        @JsonProperty("sourcesBackup") val sourcesBackup: List<Sources?>?,
        @JsonProperty("tracks") val tracks: List<Tracks?>?
    )

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val urls = (tryParseJson<Pair<String, String>>(data)?.let { (prefix, server) ->
            val episodesUrl = "$mainUrl/ajax/v2/episode/servers/$server"
            val episodes = app.get(episodesUrl).text

            // Supported streams, they're identical
            Jsoup.parse(episodes).select("a").mapNotNull { element ->
                val id = element?.attr("data-id") ?: return@mapNotNull null
                if(element.select("span")?.text()?.trim()?.isValidServer() == true) {
                    "$prefix.$id".replace("/tv/", "/watch-tv/")
                } else {
                    null
                }
            }
        } ?: tryParseJson<List<String>>(data))?.distinct()

        urls?.pmap { url ->
            val sources = app.get(
                url,
                interceptor = WebViewResolver(
                    Regex("""/getSources"""),
                )
            ).text

            val mapped = parseJson<SourceObject>(sources)

            mapped.tracks?.forEach {
                it?.toSubtitleFile()?.let { subtitleFile ->
                    subtitleCallback.invoke(subtitleFile)
                }
            }

            listOf(
                mapped.sources to "",
                mapped.sources1 to "source 2",
                mapped.sources2 to "source 3",
                mapped.sourcesBackup to "source backup"
            ).forEach { subList ->
                subList.first?.forEach {
                    it?.toExtractorLink(this, subList.second)?.forEach(callback)
                }
            }
        }

        return !urls.isNullOrEmpty()
    }

    private fun Element.toSearchResult(): SearchResponse {
        val img = this.select("img")
        val title = img.attr("title")
        val posterUrl = img.attr("data-src")
        val href = fixUrl(this.select("a").attr("href"))
        val isMovie = href.contains("/movie/")
        return if (isMovie) {
            MovieSearchResponse(
                title,
                href,
                this@SflixProvider.name,
                TvType.Movie,
                posterUrl,
                null
            )
        } else {
            TvSeriesSearchResponse(
                title,
                href,
                this@SflixProvider.name,
                TvType.Movie,
                posterUrl,
                null,
                null
            )
        }
    }

    companion object {
        fun String?.isValidServer(): Boolean {
            if (this.isNullOrEmpty()) return false
            if (this.equals("UpCloud", ignoreCase = true) || this.equals(
                    "Vidcloud",
                    ignoreCase = true
                ) || this.equals("RapidStream", ignoreCase = true)
            ) return true
            return true
        }

        // For re-use in Zoro
        fun Sources.toExtractorLink(caller: MainAPI, name: String): List<ExtractorLink>? {
            return this.file?.let { file ->
                //println("FILE::: $file")
                val isM3u8 = URI(this.file).path.endsWith(".m3u8") || this.type.equals(
                    "hls",
                    ignoreCase = true
                )
                if (isM3u8) {
                    M3u8Helper().m3u8Generation(M3u8Helper.M3u8Stream(this.file, null), true)
                        .map { stream ->
                            //println("stream: ${stream.quality} at ${stream.streamUrl}")
                            val qualityString = if ((stream.quality ?: 0) == 0) label
                                ?: "" else "${stream.quality}p"
                            ExtractorLink(
                                caller.name,
                                "${caller.name} $qualityString $name",
                                stream.streamUrl,
                                caller.mainUrl,
                                getQualityFromName(stream.quality.toString()),
                                true
                            )
                        }
                } else {
                    listOf(ExtractorLink(
                        caller.name,
                        this.label?.let { "${caller.name} - $it" } ?: caller.name,
                        file,
                        caller.mainUrl,
                        getQualityFromName(this.type ?: ""),
                        false,
                    ))
                }
            }
        }

        fun Tracks.toSubtitleFile(): SubtitleFile? {
            return this.file?.let {
                SubtitleFile(
                    this.label ?: "Unknown",
                    it
                )
            }
        }
    }
}

