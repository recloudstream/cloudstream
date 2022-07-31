package com.lagradost.cloudstream3.animeproviders

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.nicehttp.NiceResponse
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class AnimeSailProvider : MainAPI() {
    override var mainUrl = "https://111.90.143.42"
    override var name = "AnimeSail"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true

    override val supportedTypes = setOf(
        TvType.Anime,
        TvType.AnimeMovie,
        TvType.OVA
    )

    companion object {
        fun getType(t: String): TvType {
            return if (t.contains("OVA") || t.contains("Special")) TvType.OVA
            else if (t.contains("Movie")) TvType.AnimeMovie
            else TvType.Anime
        }

        fun getStatus(t: String): ShowStatus {
            return when (t) {
                "Completed" -> ShowStatus.Completed
                "Ongoing" -> ShowStatus.Ongoing
                else -> ShowStatus.Completed
            }
        }
    }

    private suspend fun request(url: String, ref: String? = null): NiceResponse {
        return app.get(
            url,
            headers = mapOf("Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8"),
            cookies = mapOf("_as_ipin_ct" to "ID"),
            referer = ref
        )
    }

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val document = request(mainUrl).document

        val homePageList = ArrayList<HomePageList>()

        document.select(".bixbox").forEach { block ->
            val header = block.select(".releases > h3").text().trim()
            val animes = block.select("article").map {
                it.toSearchResult()
            }
            if (animes.isNotEmpty()) homePageList.add(HomePageList(header, animes))
        }

        return HomePageResponse(homePageList)
    }

    private fun getProperAnimeLink(uri: String): String {
        return if (uri.contains("/anime/")) {
            uri
        } else {
            var title = uri.substringAfter("$mainUrl/")
            title = when {
                (title.contains("-episode")) && !(title.contains("-movie")) -> title.substringBefore(
                    "-episode"
                )
                (title.contains("-movie")) -> title.substringBefore("-movie")
                else -> title
            }

            "$mainUrl/anime/$title"
        }
    }

    private fun Element.toSearchResult(): AnimeSearchResponse {
        val href = getProperAnimeLink(fixUrlNull(this.selectFirst("a")?.attr("href")).toString())
        val title = this.select(".tt > h2").text().trim()
        val posterUrl = fixUrlNull(this.selectFirst("div.limit img")?.attr("src"))
        val epNum = this.selectFirst(".tt > h2")?.text()?.let {
            Regex("Episode\\s?([0-9]+)").find(it)?.groupValues?.getOrNull(1)?.toIntOrNull()
        }
        return newAnimeSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = posterUrl
            addSub(epNum)
        }

    }

    override suspend fun search(query: String): List<SearchResponse> {
        val link = "$mainUrl/?s=$query"
        val document = request(link).document

        return document.select("div.listupd article").map {
            it.toSearchResult()
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = request(url).document

        val title = document.selectFirst("h1.entry-title")?.text().toString().trim()
        val type = getType(
            document.select("tbody th:contains(Tipe)").next().text()
        )
        val episodes = document.select("ul.daftar > li").map {
            val header = it.select("a").text().trim()
            val name =
                Regex("(Episode\\s?[0-9]+)").find(header)?.groupValues?.getOrNull(0) ?: header
            val link = fixUrl(it.select("a").attr("href"))
            Episode(link, name = name)
        }.reversed()

        return newAnimeLoadResponse(title, url, type) {
            posterUrl = document.selectFirst("div.entry-content > img")?.attr("src")
            this.year =
                document.select("tbody th:contains(Dirilis)").next().text().trim().toIntOrNull()
            addEpisodes(DubStatus.Subbed, episodes)
            showStatus =
                getStatus(document.select("tbody th:contains(Status)").next().text().trim())
            plot = document.selectFirst("div.entry-content > p")?.text()
            this.tags =
                document.select("tbody th:contains(Genre)").next().select("a").map { it.text() }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val document = request(data).document

        document.select(".mobius > .mirror > option").apmap {
            safeApiCall {
                val iframe = fixUrl(
                    Jsoup.parse(base64Decode(it.attr("data-em"))).select("iframe").attr("src")
                        ?: throw ErrorLoadingException("No iframe found")
                )

                when {
                    iframe.startsWith("$mainUrl/utils/player/arch/") || iframe.startsWith(
                        "$mainUrl/utils/player/race/"
                    ) -> request(iframe, ref = data).document.select("source").attr("src")
                        .let { link ->
                            val source =
                                when {
                                    iframe.contains("/arch/") -> "Arch"
                                    iframe.contains("/race/") -> "Race"
                                    else -> this.name
                                }
                            val quality =
                                Regex("\\.([0-9]{3,4})\\.").find(link)?.groupValues?.get(1)
                            callback.invoke(
                                ExtractorLink(
                                    source = source,
                                    name = source,
                                    url = link,
                                    referer = mainUrl,
                                    quality = quality?.toIntOrNull() ?: Qualities.Unknown.value
                                )
                            )
                        }
//                    skip for now
//                    iframe.startsWith("$mainUrl/utils/player/fichan/") -> ""
//                    iframe.startsWith("$mainUrl/utils/player/blogger/") -> ""
                    iframe.startsWith("$mainUrl/utils/player/framezilla/") || iframe.startsWith("https://uservideo.xyz") -> {
                        request(iframe, ref = data).document.select("iframe").attr("src")
                            .let { link ->
                                loadExtractor(fixUrl(link), mainUrl, subtitleCallback, callback)
                            }
                    }
                    else -> {
                        loadExtractor(iframe, mainUrl, subtitleCallback, callback)
                    }
                }
            }
        }

        return true
    }


}