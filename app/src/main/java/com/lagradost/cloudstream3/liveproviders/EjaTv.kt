package com.lagradost.cloudstream3.liveproviders

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import org.jsoup.nodes.Element

class EjaTv : MainAPI() {
    override var mainUrl = "https://eja.tv"
    override var name = "Eja.tv"

    // Universal language?
    override var lang = "en"
    override val hasDownloadSupport = false

    override val hasMainPage = true
    override val supportedTypes = setOf(
        TvType.Live
    )

    private fun Element.toSearchResponse(): LiveSearchResponse? {
        val link = this.select("div.alternative a").last() ?: return null
        val href = fixUrl(link.attr("href"))
        val img = this.selectFirst("div.thumb img")
        val lang = this.selectFirst(".card-title > a")?.attr("href")?.removePrefix("?country=")
            ?.replace("int", "eu") //international -> European Union 🇪🇺
        return LiveSearchResponse(
            // Kinda hack way to get the title
            img?.attr("alt")?.replaceFirst("Watch ", "") ?: return null,
            href,
            this@EjaTv.name,
            TvType.Live,
            fixUrl(img.attr("src")),
            lang = lang
        )
    }

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        // Maybe this based on app language or as setting?
        val language = "English"
        val dataMap = mapOf(
            "News" to mapOf("language" to language, "category" to "News"),
            "Sports" to mapOf("language" to language, "category" to "Sports"),
            "Entertainment" to mapOf("language" to language, "category" to "Entertainment")
        )
        return HomePageResponse(dataMap.apmap { (title, data) ->
            val document = app.post(mainUrl, data = data).document
            val shows = document.select("div.card-body").mapNotNull {
                it.toSearchResponse()
            }
            HomePageList(
                title,
                shows,
                isHorizontalImages = true
            )
        })
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return app.post(
            mainUrl, data = mapOf("search" to query)
        ).document.select("div.card-body").mapNotNull {
            it.toSearchResponse()
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val sections =
            doc.select("li.list-group-item.d-flex.justify-content-between.align-items-center")

        val link = fixUrl(sections.last()!!.select("a").attr("href"))

        val title = doc.select("h5.text-center").text()
        val poster = fixUrl(doc.select("p.text-center img").attr("src"))

        val summary = sections.subList(0, 3).joinToString("<br>") {
            val innerText = it.ownText().trim()
            val outerText = it.select("a").text().trim()
            "$innerText: $outerText"
        }

        return LiveStreamLoadResponse(
            title,
            url,
            this.name,
            LoadData(link, title).toJson(),
            poster,
            plot = summary
        )
    }

    data class LoadData(
        val url: String,
        val title: String
    )

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val loadData = parseJson<LoadData>(data)

        callback.invoke(
            ExtractorLink(
                this.name,
                loadData.title,
                loadData.url,
                "",
                Qualities.Unknown.value,
                isM3u8 = true
            )
        )
        return true
    }
}