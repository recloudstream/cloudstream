package com.lagradost.cloudstream3.movieproviders

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.network.WebViewResolver
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

    override fun getMainPage(): HomePageResponse {
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

    override val vpnStatus: VPNStatus
        get() = VPNStatus.None

    override fun search(query: String): List<SearchResponse> {
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

    override fun load(url: String): LoadResponse {
        val html = app.get(url).text
        val document = Jsoup.parse(html)

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
            idRegex.find(url)?.groupValues?.get(1) ?: throw RuntimeException("Unable to get id from '$url'")
        else dataId

        if (isMovie) {
            // Movies
            val episodesUrl = "$mainUrl/ajax/movie/episodes/$id"
            val episodes = app.get(episodesUrl).text

            // Supported streams, they're identical
            val sourceId = Jsoup.parse(episodes).select("a").firstOrNull {
                it.select("span").text().trim().equals("RapidStream", ignoreCase = true)
                        || it.select("span").text().trim().equals("Vidcloud", ignoreCase = true)
            }?.attr("data-id")

            val webViewUrl = "$url${sourceId?.let { ".$it" } ?: ""}".replace("/movie/", "/watch-movie/")

            return newMovieLoadResponse(title, url, TvType.Movie, webViewUrl) {
                this.year = year
                this.posterUrl = posterUrl
                this.plot = plot
                setDuration(duration)
            }
        } else {
            val seasonsHtml = app.get("$mainUrl/ajax/v2/tv/seasons/$id").text
            val seasonsDocument = Jsoup.parse(seasonsHtml)
            val episodes = arrayListOf<TvSeriesEpisode>()

            seasonsDocument.select("div.dropdown-menu.dropdown-menu-model > a").forEachIndexed { season, element ->
                val seasonId = element.attr("data-id")
                if (seasonId.isNullOrBlank()) return@forEachIndexed

                val seasonHtml = app.get("$mainUrl/ajax/v2/season/episodes/$seasonId").text
                val seasonDocument = Jsoup.parse(seasonHtml)
                seasonDocument.select("div.flw-item.film_single-item.episode-item.eps-item")
                    .forEachIndexed { _, it ->
                        val episodeImg = it.select("img")
                        val episodeTitle = episodeImg.attr("title")
                        val episodePosterUrl = episodeImg.attr("src")
                        val episodeData = it.attr("data-id")

//                            val episodeNum =
//                                Regex("""\d+""").find(it.select("div.episode-number").text())?.groupValues?.get(1)
//                                    ?.toIntOrNull()

                        episodes.add(
                            TvSeriesEpisode(
                                episodeTitle,
                                season + 1,
                                null,
                                "$url:::$episodeData",
                                fixUrl(episodePosterUrl)
                            )
                        )
                    }

            }
            return newTvSeriesLoadResponse(title,url,TvType.TvSeries,episodes) {
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

    override fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        // To transfer url:::id
        val split = data.split(":::")
        // Only used for tv series
        val url = if (split.size == 2) {
            val episodesUrl = "$mainUrl/ajax/v2/episode/servers/${split[1]}"
            val episodes = app.get(episodesUrl).text

            // Supported streams, they're identical
            val sourceId = Jsoup.parse(episodes).select("a").firstOrNull {
                it.select("span").text().trim().equals("RapidStream", ignoreCase = true)
                        || it.select("span").text().trim().equals("Vidcloud", ignoreCase = true)
            }?.attr("data-id")

            "${split[0]}${sourceId?.let { ".$it" } ?: ""}".replace("/tv/", "/watch-tv/")
        } else {
            data
        }

        val sources = app.get(
            url,
            interceptor = WebViewResolver(
                Regex("""/getSources""")
            )
        ).text

        val mapped = mapper.readValue<SourceObject>(sources)
        mapped.tracks?.forEach {
            it?.toSubtitleFile()?.let { subtitleFile ->
                subtitleCallback.invoke(subtitleFile)
            }
        }
        val list = listOf(
            mapped.sources to "source 1",
            mapped.sources1 to "source 2",
            mapped.sources2 to "source 3",
            mapped.sourcesBackup to "source backup"
        )
        list.forEach { subList ->
            subList.first?.forEach {
                it?.toExtractorLink(this, subList.second)?.forEach(callback)
            }
        }
        return true
    }

    companion object {
        // For re-use in Zoro

        fun Sources.toExtractorLink(caller: MainAPI, name: String): List<ExtractorLink>? {
            return this.file?.let { file ->
                val isM3u8 = URI(this.file).path.endsWith(".m3u8") || this.type.equals("hls", ignoreCase = true)
                if (isM3u8) {
                    M3u8Helper().m3u8Generation(M3u8Helper.M3u8Stream(this.file, null), true).map { stream ->
                        val qualityString = if ((stream.quality ?: 0) == 0) label ?: "" else "${stream.quality}p"
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

