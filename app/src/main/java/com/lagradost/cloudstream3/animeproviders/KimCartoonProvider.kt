package com.lagradost.cloudstream3.animeproviders

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor

class KimCartoonProvider : MainAPI() {

    override var mainUrl = "https://kimcartoon.li"
    override var name = "Kim Cartoon"
    override val hasQuickSearch = true
    override val hasMainPage = true

    override val supportedTypes = setOf(TvType.Cartoon)

    private fun fixUrl(url: String): String {
        return if (url.startsWith("/")) mainUrl + url else url
    }

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val doc = app.get(mainUrl).document.select("#container")
        val response = mutableListOf(
            HomePageList(
                "Latest Update",
                doc.select("div.bigBarContainer div.items > div > a").map {
                    AnimeSearchResponse(
                        it.select(".item-title").let { div ->
                            //Because it doesn't contain Title separately
                            div.text().replace(div.select("span").text(), "")
                        },
                        mainUrl + it.attr("href"),
                        mainUrl,
                        TvType.Cartoon,
                        fixUrl(it.select("img").let { img ->
                            img.attr("src").let { src ->
                                src.ifEmpty { img.attr("srctemp") }
                            }
                        })
                    )
                }
            )
        )
        val list = mapOf(
            "Top Day" to "tab-top-day",
            "Top Week" to "tab-top-week",
            "Top Month" to "tab-top-month",
            "New Cartoons" to "tab-newest-series"
        )
        response.addAll(list.map { item ->
            HomePageList(
                item.key,
                doc.select("#${item.value} > div").map {
                    AnimeSearchResponse(
                        it.select("span.title").text(),
                        mainUrl + it.select("a")[0].attr("href"),
                        mainUrl,
                        TvType.Cartoon,
                        fixUrl(it.select("a > img").attr("src"))
                    )
                }
            )
        })
        return HomePageResponse(response)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return app.post(
            "$mainUrl/Search/Cartoon",
            data = mapOf("keyword" to query)
        ).document
            .select("#leftside > div.bigBarContainer div.list-cartoon > div.item > a")
            .map {
                AnimeSearchResponse(
                    it.select("span").text(),
                    mainUrl + it.attr("href"),
                    mainUrl,
                    TvType.Cartoon,
                    fixUrl(it.select("img").attr("src"))
                )
            }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> {
        return app.post(
            "$mainUrl/Ajax/SearchSuggest",
            data = mapOf("keyword" to query)
        ).document.select("a").map {
            AnimeSearchResponse(
                it.text(),
                it.attr("href"),
                mainUrl,
                TvType.Cartoon,
            )
        }
    }


    private fun getStatus(from: String?): ShowStatus? {
        return when {
            from?.contains("Completed") == true -> ShowStatus.Completed
            from?.contains("Ongoing") == true -> ShowStatus.Ongoing
            else -> null
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document.select("#leftside")
        val info = doc.select("div.barContent")
        val name = info.select("a.bigChar").text()
        val eps = doc.select("table.listing > tbody > tr a").reversed().map {
            Episode(
                fixUrl(it.attr("href")),
                it.text().replace(name, "").trim()
            )
        }
        val infoText = info.text()
        fun getData(after: String, before: String): String? {
            return if (infoText.contains(after))
                infoText
                    .substringAfter("$after:")
                    .substringBefore(before)
                    .trim()
            else null
        }

        return newTvSeriesLoadResponse(name, url, TvType.Cartoon, eps) {
            posterUrl = fixUrl(info.select("div > img").attr("src"))
            showStatus = getStatus(getData("Status", "Views"))
            plot = getData("Summary", "Tags:")
            tags = getData("Genres", "Date aired")?.split(",")
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val servers =
            app.get(data).document.select("#selectServer > option").map { fixUrl(it.attr("value")) }
        servers.apmap {
            app.get(it).document.select("#my_video_1").attr("src").let { iframe ->
                if (iframe.isNotEmpty()) {
                    loadExtractor(iframe, "$mainUrl/", subtitleCallback, callback)
                }
                //There are other servers, but they require some work to do
            }
        }
        return true
    }
}