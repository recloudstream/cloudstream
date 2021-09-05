package com.lagradost.cloudstream3.animeproviders

import android.annotation.SuppressLint
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName
import khttp.structures.cookie.CookieJar
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.*

class TenshiProvider : MainAPI() {
    companion object {
        var token: String? = null
        var cookie: CookieJar? = null

        fun getType(t: String): TvType {
            return if (t.contains("OVA") || t.contains("Special")) TvType.ONA
            else if (t.contains("Movie")) TvType.AnimeMovie
            else TvType.Anime
        }
    }

    override val mainUrl: String
        get() = "https://tenshi.moe"
    override val name: String
        get() = "Tenshi.moe"
    override val hasQuickSearch: Boolean
        get() = false
    override val hasMainPage: Boolean
        get() = true
    
    
    override val supportedTypes: Set<TvType>
        get() = setOf(TvType.Anime, TvType.AnimeMovie, TvType.ONA)

    private fun autoLoadToken(): Boolean {
        if (token != null) return true
        return loadToken()
    }

    private fun loadToken(): Boolean {
        return try {
            val response = khttp.get(mainUrl)
            cookie = response.cookies
            val document = Jsoup.parse(response.text)
            token = document.selectFirst("""meta[name="csrf-token"]""").attr("content")
            token != null
        } catch (e: Exception) {
            false
        }
    }
    
    override fun getMainPage(): HomePageResponse {
        val items = ArrayList<HomePageList>()
        val soup = Jsoup.parse(khttp.get(mainUrl).text)
        for (section in soup.select("#content > section")) {
            try {
                if (section.attr("id") == "toplist-tabs") {
                    for (top in section.select(".tab-content > [role=\"tabpanel\"]")) {
                        val title = "Top of the " + top.attr("id").split("-")[1].capitalize(Locale.UK)
                        val anime = top.select("li > a").map {
                            AnimeSearchResponse(
                                it.selectFirst(".thumb-title").text(),
                                fixUrl(it.attr("href")),
                                this.name,
                                TvType.Anime,
                                it.selectFirst("img").attr("src"),
                                null,
                                null,
                                EnumSet.of(DubStatus.Subbed),
                                null,
                                null
                            )
                        }
                        items.add(HomePageList(title, anime))
                    }
                } else {
                    val title = section.selectFirst("h2").text()
                    val anime = section.select("li > a").map {
                        AnimeSearchResponse(
                            it.selectFirst(".thumb-title").text(),
                            fixUrl(it.attr("href")),
                            this.name,
                            TvType.Anime,
                            it.selectFirst("img").attr("src"),
                            null,
                            null,
                            EnumSet.of(DubStatus.Subbed),
                            null,
                            null
                        )
                    }
                    items.add(HomePageList(title, anime))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if(items.size <= 0) throw ErrorLoadingException()
        return HomePageResponse(items)
    }

    private fun getIsMovie(type: String, id: Boolean = false): Boolean {
        if (!id) return type == "Movie"

        val movies = listOf("rrso24fa", "e4hqvtym", "bl5jdbqn", "u4vtznut", "37t6h2r4", "cq4azcrj")
        val aniId = type.replace("$mainUrl/anime/", "")
        return movies.contains(aniId)
    }

    private fun parseSearchPage(soup: Document): ArrayList<SearchResponse> {
        val items = soup.select("ul.thumb > li > a")
        if (items.isEmpty()) return ArrayList()
        val returnValue = ArrayList<SearchResponse>()
        for (i in items) {
            val href = fixUrl(i.attr("href"))
            val img = fixUrl(i.selectFirst("img").attr("src"))
            val title = i.attr("title")

            returnValue.add(
                if (getIsMovie(href, true)) {
                    MovieSearchResponse(
                        title, href, this.name, TvType.Movie, img, null
                    )
                } else {
                    AnimeSearchResponse(
                        title,
                        href,
                        this.name,
                        TvType.Anime,
                        img,
                        null,
                        null,
                        EnumSet.of(DubStatus.Subbed),
                        null,
                        null
                    )
                }
            )
        }
        return returnValue
    }

    @SuppressLint("SimpleDateFormat")
    private fun dateParser(dateString: String): String? {
        val format = SimpleDateFormat("dd 'of' MMM',' yyyy")
        val newFormat = SimpleDateFormat("dd-MM-yyyy")
        return newFormat.format(
            format.parse(
                dateString.replace("th ", " ").replace("st ", " ").replace("nd ", " ").replace("rd ", " ")
            )
        )
    }

//    data class TenshiSearchResponse(
//        @JsonProperty("url") var url : String,
//        @JsonProperty("title") var title : String,
//        @JsonProperty("cover") var cover : String,
//        @JsonProperty("genre") var genre : String,
//        @JsonProperty("year") var year : Int,
//        @JsonProperty("type") var type : String,
//        @JsonProperty("eps") var eps : String,
//        @JsonProperty("cen") var cen : String
//    )

//    override fun quickSearch(query: String): ArrayList<SearchResponse>? {
//        if (!autoLoadToken()) return quickSearch(query)
//        val url = "$mainUrl/anime/search"
//        val response = khttp.post(
//            url,
//            data=mapOf("q" to query),
//            headers=mapOf("x-csrf-token" to token, "x-requested-with" to "XMLHttpRequest"),
//            cookies = cookie
//
//        )
//
//        val items = mapper.readValue<List<TenshiSearchResponse>>(response.text)
//
//        if (items.isEmpty()) return ArrayList()
//
//        val returnValue = ArrayList<SearchResponse>()
//        for (i in items) {
//            val href = fixUrl(i.url)
//            val title = i.title
//            val img = fixUrl(i.cover)
//            val year = i.year
//
//            returnValue.add(
//                if (getIsMovie(i.type)) {
//                    MovieSearchResponse(
//                        title, href, getSlug(href), this.name, TvType.Movie, img, year
//                    )
//                } else {
//                    AnimeSearchResponse(
//                        title, href, getSlug(href), this.name,
//                        TvType.Anime, img,  year, null,
//                        EnumSet.of(DubStatus.Subbed),
//                        null, null
//                    )
//                }
//            )
//        }
//        return returnValue
//    }

    override fun search(query: String): ArrayList<SearchResponse> {
        val url = "$mainUrl/anime"
        var response = khttp.get(url, params = mapOf("q" to query), cookies = mapOf("loop-view" to "thumb"))
        var document = Jsoup.parse(response.text)
        val returnValue = parseSearchPage(document)

        while (!document.select("""a.page-link[rel="next"]""").isEmpty()) {
            val link = document.select("""a.page-link[rel="next"]""")
            if (link != null && !link.isEmpty()) {
                response = khttp.get(link[0].attr("href"), cookies = mapOf("loop-view" to "thumb"))
                document = Jsoup.parse(response.text)
                returnValue.addAll(parseSearchPage(document))
            } else {
                break
            }
        }

        return returnValue
    }

    override fun load(url: String): LoadResponse {
        val response = khttp.get(url, timeout = 120.0, cookies = mapOf("loop-view" to "thumb"))
        val document = Jsoup.parse(response.text)

        val englishTitle = document.selectFirst("span.value > span[title=\"English\"]")?.parent()?.text()?.trim()
        val japaneseTitle = document.selectFirst("span.value > span[title=\"Japanese\"]")?.parent()?.text()?.trim()
        val canonicalTitle = document.selectFirst("header.entry-header > h1.mb-3").text().trim()

        val episodeNodes = document.select("li[class*=\"episode\"] > a")

        val episodes = ArrayList<AnimeEpisode>(episodeNodes?.map {
            AnimeEpisode(
                it.attr("href"),
                it.selectFirst(".episode-title")?.text()?.trim(),
                it.selectFirst("img")?.attr("src"),
                dateParser(it.selectFirst(".episode-date").text().trim()).toString(),
                null,
                it.attr("data-content").trim(),
            )
        }
            ?: ArrayList<AnimeEpisode>())
        val status = when (document.selectFirst("li.status > .value")?.text()?.trim()) {
            "Ongoing" -> ShowStatus.Ongoing
            "Completed" -> ShowStatus.Completed
            else -> null
        }
        val yearText = document.selectFirst("li.release-date .value").text()
        val pattern = "(\\d{4})".toRegex()
        val (year) = pattern.find(yearText)!!.destructured

        val poster = document.selectFirst("img.cover-image")?.attr("src")
        val type = document.selectFirst("a[href*=\"$mainUrl/type/\"]")?.text()?.trim()

        val synopsis = document.selectFirst(".entry-description > .card-body")?.text()?.trim()
        val genre = document.select("li.genre.meta-data > span.value").map { it?.text()?.trim().toString() }

        val synonyms =
            document.select("li.synonym.meta-data > div.info-box > span.value").map { it?.text()?.trim().toString() }

        return AnimeLoadResponse(
            englishTitle,
            japaneseTitle,
            canonicalTitle,
            url,
            this.name,
            getType(type ?: ""),
            poster,
            year.toIntOrNull(),
            null,
            episodes,
            status,
            synopsis,
            ArrayList(genre),
            ArrayList(synonyms),
            null,
            null,
        )
    }


    override fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val response = khttp.get(data)
        val src = Jsoup.parse(response.text).selectFirst(".embed-responsive > iframe").attr("src")
        val mp4moe = Jsoup.parse(khttp.get(src, headers = mapOf("Referer" to data)).text).selectFirst("video#player")

        val sources = mp4moe.select("source").map {
            ExtractorLink(
                this.name,
                "${this.name} - ${it.attr("title")}" + if (it.attr("title").endsWith('p')) "" else 'p',
                fixUrl(it.attr("src")),
                this.mainUrl,
                getQualityFromName(it.attr("title"))
            )
        }

        for (source in sources) {
            callback.invoke(source)
        }
        return true
    }
}
