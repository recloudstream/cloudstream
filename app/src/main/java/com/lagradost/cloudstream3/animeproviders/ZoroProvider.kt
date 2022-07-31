package com.lagradost.cloudstream3.animeproviders

import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addAniListId
import com.lagradost.cloudstream3.LoadResponse.Companion.addMalId
import com.lagradost.cloudstream3.movieproviders.SflixProvider.Companion.extractRabbitStream
import com.lagradost.cloudstream3.movieproviders.SflixProvider.Companion.runSflixExtractorVerifierJob
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.nicehttp.Requests.Companion.await
import okhttp3.Interceptor
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URI

private const val OPTIONS = "OPTIONS"

class ZoroProvider : MainAPI() {
    override var mainUrl = "https://zoro.to"
    override var name = "Zoro"
    override val hasQuickSearch = false
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val usesWebView = true

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
                "Finished Airing" -> ShowStatus.Completed
                "Currently Airing" -> ShowStatus.Ongoing
                else -> ShowStatus.Completed
            }
        }
    }

    val epRegex = Regex("Ep (\\d+)/")
    private fun Element.toSearchResult(): SearchResponse? {
        val href = fixUrl(this.select("a").attr("href"))
        val title = this.select("h3.film-name").text()
        val dubSub = this.select(".film-poster > .tick.ltr").text()
        //val episodes = this.selectFirst(".film-poster > .tick-eps")?.text()?.toIntOrNull()

        val dubExist = dubSub.contains("dub", ignoreCase = true)
        val subExist = dubSub.contains("sub", ignoreCase = true)
        val episodes =
            this.selectFirst(".film-poster > .tick.rtl > .tick-eps")?.text()?.let { eps ->
                //println("REGEX:::: $eps")
                // current episode / max episode
                //Regex("Ep (\\d+)/(\\d+)")
                epRegex.find(eps)?.groupValues?.get(1)?.toIntOrNull()
            }
        if (href.contains("/news/") || title.trim().equals("News", ignoreCase = true)) return null
        val posterUrl = fixUrl(this.select("img").attr("data-src"))
        val type = getType(this.select("div.fd-infor > span.fdi-item").text())

        return newAnimeSearchResponse(title, href, type) {
            this.posterUrl = posterUrl
            addDubStatus(dubExist, subExist, episodes, episodes)
        }
    }

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val html = app.get("$mainUrl/home").text
        val document = Jsoup.parse(html)

        val homePageList = ArrayList<HomePageList>()

        document.select("div.anif-block").forEach { block ->
            val header = block.select("div.anif-block-header").text().trim()
            val animes = block.select("li").mapNotNull {
                it.toSearchResult()
            }
            if (animes.isNotEmpty()) homePageList.add(HomePageList(header, animes))
        }

        document.select("section.block_area.block_area_home").forEach { block ->
            val header = block.select("h2.cat-heading").text().trim()
            val animes = block.select("div.flw-item").mapNotNull {
                it.toSearchResult()
            }
            if (animes.isNotEmpty()) homePageList.add(HomePageList(header, animes))
        }

        return HomePageResponse(homePageList)
    }

    private data class Response(
        @JsonProperty("status") val status: Boolean,
        @JsonProperty("html") val html: String
    )

//    override suspend fun quickSearch(query: String): List<SearchResponse> {
//        val url = "$mainUrl/ajax/search/suggest?keyword=${query}"
//        val html = mapper.readValue<Response>(khttp.get(url).text).html
//        val document = Jsoup.parse(html)
//
//        return document.select("a.nav-item").map {
//            val title = it.selectFirst(".film-name")?.text().toString()
//            val href = fixUrl(it.attr("href"))
//            val year = it.selectFirst(".film-infor > span")?.text()?.split(",")?.get(1)?.trim()?.toIntOrNull()
//            val image = it.select("img").attr("data-src")
//
//            AnimeSearchResponse(
//                title,
//                href,
//                this.name,
//                TvType.TvSeries,
//                image,
//                year,
//                null,
//                EnumSet.of(DubStatus.Subbed),
//                null,
//                null
//            )
//
//        }
//    }

    override suspend fun search(query: String): List<SearchResponse> {
        val link = "$mainUrl/search?keyword=$query"
        val html = app.get(link).text
        val document = Jsoup.parse(html)

        return document.select(".flw-item").map {
            val title = it.selectFirst(".film-detail > .film-name > a")?.attr("title").toString()
            val filmPoster = it.selectFirst(".film-poster")
            val poster = filmPoster!!.selectFirst("img")?.attr("data-src")

            val episodes = filmPoster.selectFirst("div.rtl > div.tick-eps")?.text()?.let { eps ->
                // current episode / max episode
                val epRegex = Regex("Ep (\\d+)/")//Regex("Ep (\\d+)/(\\d+)")
                epRegex.find(eps)?.groupValues?.get(1)?.toIntOrNull()
            }
            val dubsub = filmPoster.selectFirst("div.ltr")?.text()
            val dubExist = dubsub?.contains("DUB") ?: false
            val subExist = dubsub?.contains("SUB") ?: false || dubsub?.contains("RAW") ?: false

            val tvType =
                getType(it.selectFirst(".film-detail > .fd-infor > .fdi-item")?.text().toString())
            val href = fixUrl(it.selectFirst(".film-name a")!!.attr("href"))

            newAnimeSearchResponse(title, href, tvType) {
                this.posterUrl = poster
                addDubStatus(dubExist, subExist, episodes, episodes)
            }
        }
    }

    private fun Element?.getActor(): Actor? {
        val image =
            fixUrlNull(this?.selectFirst(".pi-avatar > img")?.attr("data-src")) ?: return null
        val name = this?.selectFirst(".pi-detail > .pi-name")?.text() ?: return null
        return Actor(name = name, image = image)
    }

    data class ZoroSyncData(
        @JsonProperty("mal_id") val malId: String?,
        @JsonProperty("anilist_id") val aniListId: String?,
    )

    override suspend fun load(url: String): LoadResponse {
        val html = app.get(url).text
        val document = Jsoup.parse(html)

        val syncData = tryParseJson<ZoroSyncData>(document.selectFirst("#syncData")?.data())

        val title = document.selectFirst(".anisc-detail > .film-name")?.text().toString()
        val poster = document.selectFirst(".anisc-poster img")?.attr("src")
        val tags = document.select(".anisc-info a[href*=\"/genre/\"]").map { it.text() }

        var year: Int? = null
        var japaneseTitle: String? = null
        var status: ShowStatus? = null

        for (info in document.select(".anisc-info > .item.item-title")) {
            val text = info?.text().toString()
            when {
                (year != null && japaneseTitle != null && status != null) -> break
                text.contains("Premiered") && year == null ->
                    year =
                        info.selectFirst(".name")?.text().toString().split(" ").last().toIntOrNull()

                text.contains("Japanese") && japaneseTitle == null ->
                    japaneseTitle = info.selectFirst(".name")?.text().toString()

                text.contains("Status") && status == null ->
                    status = getStatus(info.selectFirst(".name")?.text().toString())
            }
        }

        val description = document.selectFirst(".film-description.m-hide > .text")?.text()
        val animeId = URI(url).path.split("-").last()

        val episodes = Jsoup.parse(
            parseJson<Response>(
                app.get(
                    "$mainUrl/ajax/v2/episode/list/$animeId"
                ).text
            ).html
        ).select(".ss-list > a[href].ssl-item.ep-item").map {
            newEpisode(it.attr("href")) {
                this.name = it?.attr("title")
                this.episode = it.selectFirst(".ssli-order")?.text()?.toIntOrNull()
            }
        }

        val actors = document.select("div.block-actors-content > div.bac-list-wrap > div.bac-item")
            .mapNotNull { head ->
                val subItems = head.select(".per-info") ?: return@mapNotNull null
                if (subItems.isEmpty()) return@mapNotNull null
                var role: ActorRole? = null
                val mainActor = subItems.first()?.let {
                    role = when (it.selectFirst(".pi-detail > .pi-cast")?.text()?.trim()) {
                        "Supporting" -> ActorRole.Supporting
                        "Main" -> ActorRole.Main
                        else -> null
                    }
                    it.getActor()
                } ?: return@mapNotNull null
                val voiceActor = if (subItems.size >= 2) subItems[1]?.getActor() else null
                ActorData(actor = mainActor, role = role, voiceActor = voiceActor)
            }

        val recommendations =
            document.select("#main-content > section > .tab-content > div > .film_list-wrap > .flw-item")
                .mapNotNull { head ->
                    val filmPoster = head?.selectFirst(".film-poster")
                    val epPoster = filmPoster?.selectFirst("img")?.attr("data-src")
                    val a = head?.selectFirst(".film-detail > .film-name > a")
                    val epHref = a?.attr("href")
                    val epTitle = a?.attr("title")
                    if (epHref == null || epTitle == null || epPoster == null) {
                        null
                    } else {
                        AnimeSearchResponse(
                            epTitle,
                            fixUrl(epHref),
                            this.name,
                            TvType.Anime,
                            epPoster,
                            dubStatus = null
                        )
                    }
                }

        return newAnimeLoadResponse(title, url, TvType.Anime) {
            japName = japaneseTitle
            engName = title
            posterUrl = poster
            this.year = year
            addEpisodes(DubStatus.Subbed, episodes)
            showStatus = status
            plot = description
            this.tags = tags
            this.recommendations = recommendations
            this.actors = actors
            addMalId(syncData?.malId?.toIntOrNull())
            addAniListId(syncData?.aniListId?.toIntOrNull())
        }
    }

    private data class RapidCloudResponse(
        @JsonProperty("link") val link: String
    )

    override suspend fun extractorVerifierJob(extractorData: String?) {
        Log.d(this.name, "Starting ${this.name} job!")
        runSflixExtractorVerifierJob(this, extractorData, "https://rapid-cloud.ru/")
    }

    /** Url hashcode to sid */
    var sid: HashMap<Int, String?> = hashMapOf()

    /**
     * Makes an identical Options request before .ts request
     * Adds an SID header to the .ts request.
     * */
    override fun getVideoInterceptor(extractorLink: ExtractorLink): Interceptor {
        return Interceptor { chain ->
            val request = chain.request()
            if (request.url.toString().endsWith(".ts")
                && request.method != OPTIONS
                // No option requests on VidCloud
                && !request.url.toString().contains("betterstream")
            ) {
                val newRequest =
                    chain.request()
                        .newBuilder().apply {
                            sid[extractorLink.url.hashCode()]?.let { sid ->
                                addHeader("SID", sid)
                            }
                        }
                        .build()
                val options = request.newBuilder().method(OPTIONS, request.body).build()
                ioSafe { app.baseClient.newCall(options).await() }

                return@Interceptor chain.proceed(newRequest)
            } else {
                return@Interceptor chain.proceed(chain.request())
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val servers: List<Pair<DubStatus, String>> = Jsoup.parse(
            app.get("$mainUrl/ajax/v2/episode/servers?episodeId=" + data.split("=")[1])
                .parsed<Response>().html
        ).select(".server-item[data-type][data-id]").map {
            Pair(
                if (it.attr("data-type") == "sub") DubStatus.Subbed else DubStatus.Dubbed,
                it.attr("data-id")
            )
        }

        val extractorData =
            "https://ws1.rapid-cloud.ru/socket.io/?EIO=4&transport=polling"

        // Prevent duplicates
        servers.distinctBy { it.second }.apmap {
            val link =
                "$mainUrl/ajax/v2/episode/sources?id=${it.second}"
            val extractorLink = app.get(
                link,
            ).parsed<RapidCloudResponse>().link
            val hasLoadedExtractorLink =
                loadExtractor(extractorLink, "https://rapid-cloud.ru/", subtitleCallback, callback)

            if (!hasLoadedExtractorLink) {
                extractRabbitStream(
                    extractorLink,
                    subtitleCallback,
                    // Blacklist VidCloud for now
                    { videoLink -> if (!videoLink.url.contains("betterstream")) callback(videoLink) },
                    true,
                    extractorData
                ) { sourceName ->
                    sourceName + " - ${it.first}"
                }
            }
        }

        return true
    }
}
