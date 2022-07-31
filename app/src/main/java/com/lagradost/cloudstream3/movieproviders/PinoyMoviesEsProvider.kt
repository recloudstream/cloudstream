package com.lagradost.cloudstream3.movieproviders

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.extractors.FEmbed
import com.lagradost.cloudstream3.extractors.helper.VstreamhubHelper
import com.lagradost.cloudstream3.network.DdosGuardKiller
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.select.Elements

class PinoyMoviesEsProvider : MainAPI() {
    override var name = "Pinoy Movies"
    override var mainUrl = "https://pinoymovies.es"
    override var lang = "tl"
    override val supportedTypes = setOf(TvType.AsianDrama)
    override val hasDownloadSupport = false
    override val hasMainPage = true
    override val hasQuickSearch = false

    data class EmbedUrl(
        @JsonProperty("embed_url") val embed_url: String,
        @JsonProperty("type") val type: String
    )

    private fun getRowElements(
        mainbody: Elements,
        rows: List<Pair<String, String>>,
        sep: String
    ): MutableList<HomePageList> {
        val all = mutableListOf<HomePageList>()
        for (item in rows) {
            val title = item.first
            val elements = mainbody.select("div${sep}${item.second} > article")?.mapNotNull {
                // Get inner div from article
                var urlTitle = it?.select("div.data.dfeatur")
                if (urlTitle.isNullOrEmpty()) {
                    urlTitle = it?.select("div.data")
                }
                if (urlTitle.isNullOrEmpty()) {
                    return@mapNotNull null
                }
                // Fetch details
                val link = fixUrlNull(urlTitle.select("a")?.attr("href"))
                if (link.isNullOrBlank()) {
                    return@mapNotNull null
                }

                val image = it?.select("div.poster > img")?.attr("data-src")

                // Get Title and Year
                val name = urlTitle.select("h3")?.text()
                    ?: urlTitle.select("h2")?.text()
                    ?: urlTitle.select("h1")?.text()
                if (name.isNullOrBlank()) {
                    return@mapNotNull null
                }

                var year = urlTitle.select("span")?.text()?.toIntOrNull()

                if (year == null) {
                    // Get year from name
                    val rex = Regex("\\((\\d+)")
                    year = rex.find(name)?.value?.replace("(", "")?.toIntOrNull()
                }
                //Log.i(this.name, "ApiError -> ${it.selectFirst("span.quality")?.text()}")
                val searchQual = getQualityFromString(it.selectFirst("span.quality")?.text())

                MovieSearchResponse(
                    name = name,
                    url = link,
                    apiName = this.name,
                    type = TvType.Movie,
                    posterUrl = image,
                    year = year,
                    quality = searchQual
                )
            }?.distinctBy { c -> c.url } ?: listOf()
            //Add to list of homepages
            if (!elements.isNullOrEmpty()) {
                all.add(
                    HomePageList(
                        title, elements
                    )
                )
            }
        }
        return all
    }

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val all = ArrayList<HomePageList>()
        val document = app.get(mainUrl).document
        val mainbody = document.getElementsByTag("body")
        if (mainbody != null) {
            // All rows will be hardcoded bc of the nature of the site
            val homepage1 = getRowElements(
                mainbody, listOf(
                    Pair("Suggestion", "items.featured"),
                    Pair("All Movies", "items.full")
                ), "."
            )
            if (homepage1.isNotEmpty()) {
                all.addAll(homepage1)
            }
            //2nd rows
            val homepage2 = getRowElements(
                mainbody, listOf(
                    Pair("Action", "genre_action"),
                    Pair("Comedy", "genre_comedy"),
                    Pair("Romance", "genre_romance"),
                    Pair("Horror", "genre_horror")
                    //Pair("Rated-R", "genre_rated-r")
                ), "#"
            )
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
            // Fetch details
            val urlTitle = it?.select("div.data") ?: return@mapNotNull null
            val link = urlTitle.select("a")?.attr("href") ?: return@mapNotNull null
            val title = urlTitle.text()?.trim() ?: "<No Title>"
            val year = urlTitle.select("span.year")?.text()?.toIntOrNull()
            val image = it.select("div.poster > img")?.attr("src")
            val searchQual = getQualityFromString(it.selectFirst("span.quality")?.text())

            MovieSearchResponse(
                name = title,
                url = link,
                apiName = this.name,
                type = TvType.Movie,
                posterUrl = image,
                year = year,
                quality = searchQual
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
        val tags = data?.select("div.sgeneros > a")?.mapNotNull { tag ->
            tag?.text() ?: return@mapNotNull null
        }?.toList()
        val recList = body?.select("div#single_relacionados > article")?.mapNotNull {
            val a = it.select("a") ?: return@mapNotNull null
            val aUrl = a.attr("href") ?: return@mapNotNull null
            val aImg = a.select("img")?.attr("data-src")
            val aName = a.select("img")?.attr("alt") ?: return@mapNotNull null
            val aYear = try {
                aName.trim().takeLast(5).removeSuffix(")").toIntOrNull()
            } catch (e: Exception) {
                null
            }
            MovieSearchResponse(
                url = aUrl,
                name = aName,
                type = TvType.Movie,
                posterUrl = aImg,
                year = aYear,
                apiName = this.name
            )
        }

        // Video links
        val listOfLinks: MutableList<String> = mutableListOf()
        val postlist = body?.select("div#playeroptions > ul > li")?.mapNotNull {
            it?.attr("data-post") ?: return@mapNotNull null
        }?.filter { it.isNotBlank() }?.distinct() ?: listOf()

        postlist.apmap { datapost ->
            //Log.i(this.name, "Result => (datapost) ${datapost}")
            val content = mapOf(
                Pair("action", "doo_player_ajax"),
                Pair("post", datapost),
                Pair("nume", "1"),
                Pair("type", "movie")
            )
            val innerPage = app.post(
                "https://pinoymovies.es/wp-admin/admin-ajax.php ",
                referer = url, data = content
            ).document.select("body")?.text()?.trim()
            if (!innerPage.isNullOrBlank()) {
                tryParseJson<EmbedUrl>(innerPage)?.let {
                    listOfLinks.add(it.embed_url)
                }
            }
        }
        return MovieLoadResponse(
            name = title,
            url = url,
            apiName = this.name,
            type = TvType.Movie,
            dataUrl = listOfLinks.toJson(),
            posterUrl = poster,
            year = year,
            plot = descript,
            tags = tags,
            recommendations = recList
        )
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // parse movie servers
        var count = 0
        tryParseJson<List<String>>(data)?.forEach { link ->
            //Log.i(this.name, "Result => (link) $link")
            if (link.startsWith("https://vstreamhub.com")) {
                VstreamhubHelper.getUrls(link, subtitleCallback, callback)
                count++
            } else if (link.contains("fembed.com")) {
                val extractor = FEmbed()
                extractor.domainUrl = "diasfem.com"
                extractor.getUrl(data).forEach {
                    callback.invoke(it)
                    count++
                }
            } else {
                if (loadExtractor(link, mainUrl, subtitleCallback, callback)) {
                    count++
                }
            }
        }
        return count > 0
    }
}
