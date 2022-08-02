package com.lagradost.cloudstream3.movieproviders

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class DramaidProvider : MainAPI() {
    override var mainUrl = "https://185.224.83.103"
    override var name = "DramaId"
    override val hasQuickSearch = false
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    override val hasChromecastSupport = false
    override val supportedTypes = setOf(TvType.AsianDrama)

    companion object {
        fun getStatus(t: String): ShowStatus {
            return when (t) {
                "Completed" -> ShowStatus.Completed
                "Ongoing" -> ShowStatus.Ongoing
                else -> ShowStatus.Completed
            }
        }
    }

    override val mainPage = mainPageOf(
        "&status=&type=&order=update" to "Drama Terbaru",
        "&order=latest" to "Baru Ditambahkan",
        "&status=&type=&order=popular" to "Drama Popular",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/series/?page=$page${request.data}").document
        val home = document.select("article[itemscope=itemscope]").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(request.name, home)
    }

    private fun getProperDramaLink(uri: String): String {
        return if (uri.contains("/series/")) {
            uri
        } else {
            "$mainUrl/series/" + Regex("$mainUrl/(.+)-ep.+").find(uri)?.groupValues?.get(1)
                .toString()
        }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val href = getProperDramaLink(this.selectFirst("a.tip")!!.attr("href"))
        val title = this.selectFirst("h2[itemprop=headline]")?.text()?.trim() ?: return null
        val posterUrl = fixUrlNull(this.selectFirst(".limit > noscript > img")?.attr("src"))

        return newTvSeriesSearchResponse(title, href, TvType.AsianDrama) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val link = "$mainUrl/?s=$query"
        val document = app.get(link).document

        return document.select("article[itemscope=itemscope]").map {
            val title = it.selectFirst("h2[itemprop=headline]")!!.text().trim()
            val poster = it.selectFirst(".limit > noscript > img")!!.attr("src")
            val href = it.selectFirst("a.tip")!!.attr("href")

            newTvSeriesSearchResponse(title, href, TvType.AsianDrama) {
                this.posterUrl = poster
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title = document.selectFirst("h1.entry-title")!!.text().trim()
        val poster = document.select(".thumb > noscript > img").attr("src")
        val tags = document.select(".genxed > a").map { it.text() }

        val year = Regex("\\d, ([0-9]*)").find(
            document.selectFirst(".info-content > .spe > span > time")!!.text().trim()
        )?.groupValues?.get(1).toString().toIntOrNull()
        val status = getStatus(
            document.select(".info-content > .spe > span:nth-child(1)")
                .text().trim().replace("Status: ", "")
        )
        val description = document.select(".entry-content > p").text().trim()

        val episodes = document.select(".eplister > ul > li").map {
            val name = it.selectFirst("a > .epl-title")!!.text().trim()
            val link = it.select("a").attr("href")
            val epNum = it.selectFirst("a > .epl-num")!!.text().trim().toIntOrNull()
            newEpisode(link) {
                this.name = name
                this.episode = epNum
            }
        }.reversed()

        val recommendations =
            document.select(".listupd > article[itemscope=itemscope]").map { rec ->
                val epTitle = rec.selectFirst("h2[itemprop=headline]")!!.text().trim()
                val epPoster = rec.selectFirst(".limit > noscript > img")!!.attr("src")
                val epHref = fixUrl(rec.selectFirst("a.tip")!!.attr("href"))

                newTvSeriesSearchResponse(epTitle, epHref, TvType.AsianDrama) {
                    this.posterUrl = epPoster
                }
            }

        if (episodes.size == 1) {
            return newMovieLoadResponse(title, url, TvType.Movie, episodes[0].data) {
                posterUrl = poster
                this.year = year
                plot = description
                this.tags = tags
                this.recommendations = recommendations
            }
        } else {
            return newTvSeriesLoadResponse(title, url, TvType.AsianDrama, episodes = episodes) {
                posterUrl = poster
                this.year = year
                showStatus = status
                plot = description
                this.tags = tags
                this.recommendations = recommendations
            }
        }

    }

    private data class Sources(
        @JsonProperty("file") val file: String,
        @JsonProperty("label") val label: String,
        @JsonProperty("type") val type: String,
        @JsonProperty("default") val default: Boolean?
    )

    private data class Tracks(
        @JsonProperty("file") val file: String,
        @JsonProperty("label") val label: String,
        @JsonProperty("kind") val type: String,
        @JsonProperty("default") val default: Boolean?
    )

    private suspend fun invokeDriveSource(
        url: String,
        name: String,
        subCallback: (SubtitleFile) -> Unit,
        sourceCallback: (ExtractorLink) -> Unit
    ) {
        val server = app.get(url).document.selectFirst(".picasa")?.nextElementSibling()?.data()

        val source = "[${server!!.substringAfter("sources: [").substringBefore("],")}]".trimIndent()
        val trackers = server.substringAfter("tracks:[").substringBefore("],")
            .replace("//language", "")
            .replace("file", "\"file\"")
            .replace("label", "\"label\"")
            .replace("kind", "\"kind\"").trimIndent()

        tryParseJson<List<Sources>>(source)?.map {
            sourceCallback(
                ExtractorLink(
                    name,
                    "Drive",
                    fixUrl(it.file),
                    referer = "https://motonews.club/",
                    quality = getQualityFromName(it.label)
                )
            )
        }

        tryParseJson<Tracks>(trackers)?.let {
            subCallback.invoke(
                SubtitleFile(
                    if (it.label.contains("Indonesia")) "${it.label}n" else it.label,
                    it.file
                )
            )
        }

    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        val sources = document.select(".mobius > .mirror > option").mapNotNull {
            fixUrl(Jsoup.parse(base64Decode(it.attr("value"))).select("iframe").attr("src"))
        }

        sources.map {
            it.replace("https://ndrama.xyz", "https://www.fembed.com")
        }.apmap {
            when {
                it.contains("motonews.club") -> invokeDriveSource(it, this.name, subtitleCallback, callback)
                else -> loadExtractor(it, data, subtitleCallback, callback)
            }
        }

        return true
    }

}
