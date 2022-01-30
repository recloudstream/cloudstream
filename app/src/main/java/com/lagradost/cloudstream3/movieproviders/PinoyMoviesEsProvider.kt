package com.lagradost.cloudstream3.movieproviders

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.extractors.FEmbed
import com.lagradost.cloudstream3.extractors.helper.VstreamhubHelper
import com.lagradost.cloudstream3.network.DdosGuardKiller
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.select.Elements

class PinoyMoviesEsProvider : MainAPI() {
    override val name = "Pinoy Movies"
    override val mainUrl = "https://pinoymovies.es"
    override val lang = "tl"
    override val supportedTypes = setOf(TvType.Movie)
    override val hasDownloadSupport = false
    override val hasMainPage = true
    override val hasQuickSearch = false

    data class EmbedUrl(
        @JsonProperty("embed_url") val embed_url: String,
        @JsonProperty("type") val type: String
    )

    private fun getRowElements(mainbody: Elements, rows: List<Pair<String, String>>, sep: String): MutableList<HomePageList> {
        val all = mutableListOf<HomePageList>()
        for (item in rows) {
            val title = item.first
            val inner = mainbody.select("div${sep}${item.second} > article")
            if (inner != null) {
                val elements: List<SearchResponse> = inner.map {
                    // Get inner div from article
                    var urlTitle = it?.select("div.data.dfeatur")
                    if (urlTitle.isNullOrEmpty()) {
                        urlTitle = it?.select("div.data")
                    }
                    // Fetch details
                    val link = urlTitle?.select("a")?.attr("href") ?: ""
                    val name = urlTitle?.text() ?: ""
                    val year = urlTitle?.select("span")?.text()?.toIntOrNull()
                    //Log.i(this.name, "Result => (link) ${link}")
                    val image = it?.select("div.poster > img")?.attr("data-src")

                    MovieSearchResponse(
                        name,
                        link,
                        this.name,
                        TvType.Movie,
                        image,
                        year,
                        null,
                    )
                }.filter { a -> a.url.isNotEmpty() }
                        .filter { b -> b.name.isNotEmpty() }
                        .distinctBy { c -> c.url }
                if (!elements.isNullOrEmpty()) {
                    all.add(HomePageList(
                            title, elements
                    ))
                }
            }
        }
        return all
    }
    override suspend fun getMainPage(): HomePageResponse {
        val all = ArrayList<HomePageList>()
        val document = app.get(mainUrl).document
        val mainbody = document.getElementsByTag("body")
        if (mainbody != null) {
            // All rows will be hardcoded bc of the nature of the site
            val homepage1 = getRowElements(mainbody, listOf(
                Pair("Suggestion", "items.featured"),
                Pair("All Movies", "items.full")
            ), ".")
            if (homepage1.isNotEmpty()) {
                all.addAll(homepage1)
            }
            //2nd rows
            val homepage2 = getRowElements(mainbody, listOf(
                Pair("Action", "genre_action"),
                Pair("Comedy", "genre_comedy"),
                Pair("Romance", "genre_romance"),
                Pair("Horror", "genre_horror")
                //Pair("Rated-R", "genre_rated-r")
            ), "#")
            if (homepage2.isNotEmpty()) {
                all.addAll(homepage2)
            }
        }
        return HomePageResponse(all)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=${query.replace(" ", "+")}"
        val document = app.get(url, interceptor = DdosGuardKiller(true))
            .document.select("div#archive-content > article")

        return document?.mapNotNull {
            val urlTitle = it?.select("div.data") ?: return@mapNotNull null
            // Fetch details
            val link = urlTitle.select("a")?.attr("href") ?: return@mapNotNull null
            val title = urlTitle.text() ?: "<No Title>"
            val year = urlTitle.select("span.year")?.text()?.toIntOrNull()
            val image = it.select("div.poster > img")?.attr("src")

            MovieSearchResponse(
                title,
                link,
                this.name,
                TvType.Movie,
                image,
                year
            )
        }?.distinctBy { it.url } ?: listOf()
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val body = doc.getElementsByTag("body")
        val inner = body?.select("div.sheader")

        // Video details
        val data = inner?.select("div.sheader > div.data")
        val title = data?.select("h1")?.firstOrNull()?.text() ?: "<Untitled>"
        val year = data?.select("span.date")?.text()?.takeLast(4)?.toIntOrNull()

        val descript = body?.select("div#info > div.wp-content")?.text()
        val poster = body?.select("div.poster > img")?.attr("src")

        // Video links
        val listOfLinks: MutableList<String> = mutableListOf()
        val postlist = body?.select("div#playeroptions > ul > li")?.mapNotNull {
            it?.attr("data-post") ?: return@mapNotNull null
        }?.filter { it.isNotEmpty() }?.distinct() ?: listOf()

        postlist.apmap { datapost ->
            //Log.i(this.name, "Result => (datapost) ${datapost}")
            val content = mapOf(
                Pair("action", "doo_player_ajax"),
                Pair("post", datapost),
                Pair("nume", "1"),
                Pair("type", "movie")
            )
            val innerPage = app.post("https://pinoymovies.es/wp-admin/admin-ajax.php ",
                referer = url, data = content).document.select("body")?.text()?.trim()
            if (!innerPage.isNullOrEmpty()) {
                val embedData = mapper.readValue<EmbedUrl>(innerPage)
                //Log.i(this.name, "Result => (embed_url) ${embedData.embed_url}")
                listOfLinks.add(embedData.embed_url)
            }
        }
        return MovieLoadResponse(title, url, this.name, TvType.Movie, listOfLinks.toJson(), poster, year, descript, null, null)
    }

    override suspend fun loadLinks(
            data: String,
            isCasting: Boolean,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
    ): Boolean {
        // parse movie servers
        var count = 0
        mapper.readValue<List<String>>(data).apmap { link ->
            count++
            //Log.i(this.name, "Result => (link) $link")
            if (link.startsWith("https://vstreamhub.com")) {
                VstreamhubHelper.getUrls(link, callback)
            } else if (link.contains("fembed.com")) {
                val extractor = FEmbed()
                extractor.domainUrl = "diasfem.com"
                extractor.getUrl(data).forEach {
                    callback.invoke(it)
                }
            } else {
                loadExtractor(link, mainUrl, callback)
            }
        }
        return count > 0
    }
}