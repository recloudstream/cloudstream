package com.lagradost.cloudstream3.animeproviders

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.APIHolder.getCaptchaToken
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.nicehttp.NiceResponse
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class AnimeIndoProvider : MainAPI() {
    override var mainUrl = "https://animeindo.sbs"
    override var name = "AnimeIndo"
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
                "Finished Airing" -> ShowStatus.Completed
                "Currently Airing" -> ShowStatus.Ongoing
                else -> ShowStatus.Completed
            }
        }

        private suspend fun request(url: String): NiceResponse {
            val req = app.get(
                url,
                cookies = mapOf("recaptcha_cookie" to "#Asia/Jakarta#-420#win32#Windows#0,false,false#Google Inc. (Intel)~ANGLE (Intel, Intel(R) HD Graphics 400 Direct3D11 vs_5_0 ps_5_0)")
            )
            if (req.isSuccessful) {
                return req
            } else {
                val document = app.get(url).document
                val captchaKey =
                    document.select("script[src*=https://www.google.com/recaptcha/api.js?render=]")
                        .attr("src").substringAfter("render=").substringBefore("&amp")
                val token = getCaptchaToken(url, captchaKey)
                return app.post(
                    url,
                    data = mapOf(
                        "action" to "recaptcha_for_all",
                        "token" to "$token",
                        "sitekey" to captchaKey
                    )
                )
            }
        }
    }

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val document = request(mainUrl).document

        val homePageList = ArrayList<HomePageList>()

        document.select("div.widget_senction").forEach { block ->
            val header = block.selectFirst("div.widget-title > h3")!!.text().trim()
            val items = block.select("div.post-show > article").map {
                it.toSearchResult()
            }
            if (items.isNotEmpty()) homePageList.add(HomePageList(header, items))
        }

        return HomePageResponse(homePageList)
    }

    private fun getProperAnimeLink(uri: String): String {
        return if (uri.contains("/anime/")) {
            uri
        } else {
            var title = uri.substringAfter("$mainUrl/")
            title = when {
                (title.contains("-episode")) && !(title.contains("-movie")) -> Regex("(.+)-episode").find(
                    title
                )?.groupValues?.get(1).toString()
                (title.contains("-movie")) -> Regex("(.+)-movie").find(title)?.groupValues?.get(
                    1
                ).toString()
                else -> title
            }
            "$mainUrl/anime/$title"
        }
    }

    private fun Element.toSearchResult(): AnimeSearchResponse {
        val title = this.selectFirst("div.title")!!.text().trim()
        val href = getProperAnimeLink(this.selectFirst("a")!!.attr("href"))
        val posterUrl = this.select("img[itemprop=image]").attr("src").toString()
        val type = getType(this.select("div.type").text().trim())
        val epNum =
            this.selectFirst("span.episode")?.ownText()?.replace(Regex("[^0-9]"), "")?.trim()
                ?.toIntOrNull()
        return newAnimeSearchResponse(title, href, type) {
            this.posterUrl = posterUrl
            addSub(epNum)
        }

    }

    override suspend fun search(query: String): List<SearchResponse> {
        val link = "$mainUrl/?s=$query"
        val document = request(link).document

        return document.select(".site-main.relat > article").map {
            val title = it.selectFirst("div.title > h2")!!.ownText().trim()
            val href = it.selectFirst("a")!!.attr("href")
            val posterUrl = it.selectFirst("img")!!.attr("src").toString()
            val type = getType(it.select("div.type").text().trim())
            newAnimeSearchResponse(title, href, type) {
                this.posterUrl = posterUrl
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = request(url).document

        val title = document.selectFirst("h1.entry-title")?.text().toString().trim()
        val poster = document.selectFirst("div.thumb > img[itemprop=image]")?.attr("src")
        val tags = document.select("div.genxed > a").map { it.text() }
        val type = getType(
            document.selectFirst("div.info-content > div.spe > span:nth-child(6)")?.ownText()
                .toString()
        )
        val year = Regex("\\d, ([0-9]*)").find(
            document.select("div.info-content > div.spe > span:nth-child(9) > time").text()
        )?.groupValues?.get(1)?.toIntOrNull()
        val status = getStatus(
            document.selectFirst("div.info-content > div.spe > span:nth-child(1)")!!.ownText()
                .trim()
        )
        val description = document.select("div[itemprop=description] > p").text()
        val trailer = document.selectFirst("div.player-embed iframe")?.attr("src")
        val episodes = document.select("div.lstepsiode.listeps ul li").mapNotNull {
            val header = it.selectFirst("span.lchx > a") ?: return@mapNotNull null
            val name = header.text().trim()
            val episode = header.text().trim().replace("Episode", "").trim().toIntOrNull()
            val link = fixUrl(header.attr("href"))
            Episode(link, name = name, episode = episode)
        }.reversed()

        return newAnimeLoadResponse(title, url, type) {
            engName = title
            posterUrl = poster
            this.year = year
            addEpisodes(DubStatus.Subbed, episodes)
            showStatus = status
            plot = description
            this.tags = tags
            addTrailer(trailer)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val document = request(data).document
        document.select("div.itemleft > .mirror > option").mapNotNull {
            fixUrl(Jsoup.parse(base64Decode(it.attr("value"))).select("iframe").attr("src"))
        }.apmap {
            if (it.startsWith("https://uservideo.xyz")) {
                app.get(it, referer = "$mainUrl/").document.select("iframe").attr("src")
            } else {
                it
            }
        }.apmap {
            loadExtractor(it, data, subtitleCallback, callback)
        }

        return true
    }


}