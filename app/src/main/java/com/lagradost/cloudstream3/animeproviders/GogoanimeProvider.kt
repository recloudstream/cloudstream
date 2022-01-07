package com.lagradost.cloudstream3.animeproviders

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.Jsoup
import java.util.*

class GogoanimeProvider : MainAPI() {
    companion object {
        fun getType(t: String): TvType {
            return if (t.contains("OVA") || t.contains("Special")) TvType.ONA
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

        val qualityRegex = Regex("(\\d+)P")
    }

    override val mainUrl = "https://gogoanime.wiki"
    override val name = "GogoAnime"
    override val hasQuickSearch = false
    override val hasMainPage = true

    override val supportedTypes = setOf(
        TvType.AnimeMovie,
        TvType.Anime,
        TvType.ONA
    )

    override fun getMainPage(): HomePageResponse {
        val headers = mapOf(
            "authority" to "ajax.gogo-load.com",
            "sec-ch-ua" to "\"Google Chrome\";v=\"89\", \"Chromium\";v=\"89\", \";Not A Brand\";v=\"99\"",
            "accept" to "text/html, */*; q=0.01",
            "dnt" to "1",
            "sec-ch-ua-mobile" to "?0",
            "user-agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/89.0.4389.90 Safari/537.36",
            "origin" to mainUrl,
            "sec-fetch-site" to "cross-site",
            "sec-fetch-mode" to "cors",
            "sec-fetch-dest" to "empty",
            "referer" to "$mainUrl/"
        )
        val parseRegex =
            Regex("""<li>\s*\n.*\n.*<a\s*href=["'](.*?-episode-(\d+))["']\s*title=["'](.*?)["']>\n.*?img src="(.*?)"""")

        val urls = listOf(
            Pair("1", "Recent Release - Sub"),
            Pair("2", "Recent Release - Dub"),
            Pair("3", "Recent Release - Chinese"),
        )

        val items = ArrayList<HomePageList>()
        for (i in urls) {
            try {
                val params = mapOf("page" to "1", "type" to i.first)
                val html = app.get(
                    "https://ajax.gogo-load.com/ajax/page-recent-release.html",
                    headers = headers,
                    params = params
                )
                items.add(HomePageList(i.second, (parseRegex.findAll(html.text).map {
                    val (link, epNum, title, poster) = it.destructured
                    val isSub = listOf(1, 3).contains(i.first.toInt())
                    AnimeSearchResponse(
                        title,
                        link,
                        this.name,
                        TvType.Anime,
                        poster,
                        null,
                        if (isSub) EnumSet.of(DubStatus.Subbed) else EnumSet.of(
                            DubStatus.Dubbed
                        ),
                        null,
                        if (!isSub) epNum.toIntOrNull() else null,
                        if (isSub) epNum.toIntOrNull() else null,
                    )
                }).toList()))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if (items.size <= 0) throw ErrorLoadingException()
        return HomePageResponse(items)
    }

    override fun search(query: String): ArrayList<SearchResponse> {
        val link = "$mainUrl/search.html?keyword=$query"
        val html = app.get(link).text
        val doc = Jsoup.parse(html)

        val episodes = doc.select(""".last_episodes li""").map {
            AnimeSearchResponse(
                it.selectFirst(".name").text().replace(" (Dub)", ""),
                fixUrl(it.selectFirst(".name > a").attr("href")),
                this.name,
                TvType.Anime,
                it.selectFirst("img").attr("src"),
                it.selectFirst(".released")?.text()?.split(":")?.getOrNull(1)?.trim()
                    ?.toIntOrNull(),
                if (it.selectFirst(".name").text()
                        .contains("Dub")
                ) EnumSet.of(DubStatus.Dubbed) else EnumSet.of(
                    DubStatus.Subbed
                ),
            )
        }

        return ArrayList(episodes)
    }

    private fun getProperAnimeLink(uri: String): String {
        if (uri.contains("-episode")) {
            val split = uri.split("/")
            val slug = split[split.size - 1].split("-episode")[0]
            return "$mainUrl/category/$slug"
        }
        return uri
    }

    override fun load(url: String): LoadResponse {
        val link = getProperAnimeLink(url)
        val episodeloadApi = "https://ajax.gogo-load.com/ajax/load-list-episode"
        val html = app.get(link).text
        val doc = Jsoup.parse(html)

        val animeBody = doc.selectFirst(".anime_info_body_bg")
        val title = animeBody.selectFirst("h1").text()
        val poster = animeBody.selectFirst("img").attr("src")
        var description: String? = null
        val genre = ArrayList<String>()
        var year: Int? = null
        var status: String? = null
        var nativeName: String? = null
        var type: String? = null

        animeBody.select("p.type").forEach { pType ->
            when (pType.selectFirst("span").text().trim()) {
                "Plot Summary:" -> {
                    description = pType.text().replace("Plot Summary:", "").trim()
                }
                "Genre:" -> {
                    genre.addAll(pType.select("a").map {
                        it.attr("title")
                    })
                }
                "Released:" -> {
                    year = pType.text().replace("Released:", "").trim().toIntOrNull()
                }
                "Status:" -> {
                    status = pType.text().replace("Status:", "").trim()
                }
                "Other name:" -> {
                    nativeName = pType.text().replace("Other name:", "").trim()
                }
                "Type:" -> {
                    type = pType.text().replace("type:", "").trim()
                }
            }
        }

        val animeId = doc.selectFirst("#movie_id").attr("value")
        val params = mapOf("ep_start" to "0", "ep_end" to "2000", "id" to animeId)
        val responseHTML = app.get(episodeloadApi, params = params).text
        val epiDoc = Jsoup.parse(responseHTML)
        val episodes = epiDoc.select("a").map {
            AnimeEpisode(
                fixUrl(it.attr("href").trim()),
                "Episode " + it.selectFirst(".name").text().replace("EP", "").trim()
            )
        }.reversed()

        return newAnimeLoadResponse(title, link, getType(type.toString())) {
            japName = nativeName
            engName = title
            posterUrl = poster
            this.year = year
            addEpisodes(DubStatus.Subbed, episodes) // TODO CHECK
            plot = description
            tags = genre

            showStatus = getStatus(status.toString())
        }
    }

    private fun extractVideos(uri: String, callback: (ExtractorLink) -> Unit) {
        val doc = app.get(uri).document

        val iframe = fixUrlNull(doc.selectFirst("div.play-video > iframe").attr("src")) ?: return

        val link = iframe.replace("streaming.php", "download")
        val page = app.get(link, headers = mapOf("Referer" to iframe))

        page.document.select(".dowload > a").pmap {
            if (it.hasAttr("download")) {
                val qual = if (it.text()
                        .contains("HDP")
                ) "1080" else qualityRegex.find(it.text())?.destructured?.component1().toString()
                callback(
                    ExtractorLink(
                        "Gogoanime",
                        if (qual == "null") "Gogoanime" else "Gogoanime - " + qual + "p",
                        it.attr("href"),
                        page.url,
                        getQualityFromName(qual),
                        it.attr("href").contains(".m3u8")
                    )
                )
            } else {
                val url = it.attr("href")
                loadExtractor(url, null, callback)
            }
        }

        val streamingResponse = app.get(iframe, headers = mapOf("Referer" to iframe))
        streamingResponse.document.select(".list-server-items > .linkserver")
            ?.forEach { element ->
                val status = element.attr("data-status") ?: return@forEach
                if (status != "1") return@forEach
                val data = element.attr("data-video") ?: return@forEach
                loadExtractor(data, streamingResponse.url, callback)
            }
    }

    override fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        extractVideos(data, callback)
        return true
    }
}
