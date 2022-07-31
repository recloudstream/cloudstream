package com.lagradost.cloudstream3.movieproviders

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import org.jsoup.nodes.Element
import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable

class XcineProvider : MainAPI() {
    override var lang = "de"
    override var mainUrl = "https://xcine.me"
    override var name = "Xcine"
    override val usesWebView = false
    override val hasMainPage = true
    override val supportedTypes =
        setOf(TvType.TvSeries, TvType.Movie, TvType.Anime, TvType.AnimeMovie)

    var cookies: MutableMap<String, String> = mutableMapOf()

    private fun Element.toSearchResponse(): SearchResponse? {
        val url = this.attr("href") ?: return null
        val poster = select("div.poster-film-small").attr("data-src")

        val title = this.attr("title")
        val yearRegex = Regex("""\((\d{4})\)\s*stream$""")
        val year = yearRegex.find(title)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val fixedTitle = title.replace(yearRegex, "")

        // If you need to differentiate use the url.
        return MovieSearchResponse(
            fixedTitle,
            url,
            this@XcineProvider.name,
            TvType.TvSeries,
            poster,
            year,
            null,
        )
    }

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val document = app.get(mainUrl).document
        val sections = document.select("div.group-film")
        return HomePageResponse(sections.mapNotNull { section ->
            val title = section.select("a.more").attr("title")
            val searchResponses =
                section.select("a.film-small").mapNotNull { it.toSearchResponse() }
            if (searchResponses.isEmpty()) return@mapNotNull null
            HomePageList(title, searchResponses)
        })
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search?key=$query"
        val document = app.get(url).document
        return document.select("a.film-small").mapNotNull {
            it.toSearchResponse()
        }
    }

    private fun String.getIntFromText(): Int? {
        return Regex("""\d+""").find(this)?.groupValues?.firstOrNull()?.toIntOrNull()
    }

    data class EpisodeData(
        val episodeId: Int,
        val showId: Int,
        val episodeUrl: String,
        val key: String,
        val keyValue: String,
        val cookies: Map<String, String>
    )

    private fun Element.toEpisode(
        showId: Int,
        key: String,
        keyValue: String,
        cookies: Map<String, String>
    ): Episode? {
        val id = this.attr("data-episode-id").toIntOrNull() ?: return null
        val title = this.attr("title")
        val titleRegex = Regex("""Staffel\s*(\d*).*folge\s*(\d*)""")
        val url = this.attr("href")
        val found = titleRegex.find(title)

        return newEpisode(
            EpisodeData(id, showId, url, key, keyValue, cookies)
        ) {
            season = found?.groupValues?.getOrNull(1)?.toIntOrNull()
            episode = found?.groupValues?.getOrNull(2)?.toIntOrNull()
        }
    }


    override suspend fun load(url: String): LoadResponse {
        val (document, text) = app.get(url).let {
            it.document to it.text
        }

        val title = document.select("h1.title-film-detail-1").text()
        val posterRegex = Regex("""ImageObject.*url":"(.*?)"""")
        val posterUrl = posterRegex.find(text)?.groupValues?.getOrNull(1)?.replace("\\", "")

        val information = document.select("ul.infomation-film > li").associate {
            it.text().substringBefore(":") to it.select("span").text()
        }

        val year = information["Erscheinungsjahr"]?.toIntOrNull()
        val duration = information["Laufzeit"]?.getIntFromText()
        val tags = information["Genre"]?.split(",")

        val recommendations =
            document.select("a.film-small").mapNotNull {
                it.toSearchResponse()
            }

        val playUrl = document.select("a.play-film").attr("href")
        val (isSeries, episodes) = if (playUrl.isNotEmpty()) {
            val keyRegex = Regex("""loadStreamSV.*\n\s*(.*?)\s*:\s*(.*?)\s*\}""")
            val (playDoc, playText) = app.get(playUrl).let {
                val responseCookies = it.okhttpResponse.headers.filter { header ->
                    header.first.equals(
                        "set-cookie",
                        ignoreCase = true
                    )
                }
                responseCookies.forEach {
                    val (cookieKey, cookieValue) = it.second.split("=")
                    cookies[cookieKey] = cookieValue.substringBefore(";")
                }
                it.document to it.text
            }

            val rhino = Context.enter()
            rhino.optimizationLevel = -1
            val scope: Scriptable = rhino.initSafeStandardObjects()
            val decodeBase64 = "atob = function(s) {\n" +
                    "    var e={},i,b=0,c,x,l=0,a,r='',w=String.fromCharCode,L=s.length;\n" +
                    "    var A=\"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/\";\n" +
                    "    for(i=0;i<64;i++){e[A.charAt(i)]=i;}\n" +
                    "    for(x=0;x<L;x++){\n" +
                    "        c=e[s.charAt(x)];b=(b<<6)+c;l+=6;\n" +
                    "        while(l>=8){((a=(b>>>(l-=8))&0xff)||(x<(L-2)))&&(r+=w(a));}\n" +
                    "    }\n" +
                    "    return r;\n" +
                    "};"
            val doc = """
                var document = {};
                var window = {
                    document: document,
                    eval: eval,
                    atob: atob
                };
            """.trimMargin()
            val jsRegex = Regex("""@context.*\n([\W\w]*?)</script""")
            val js = jsRegex.findAll(playText).last().groupValues[1].replace("let ", "")
            rhino.evaluateString(scope, decodeBase64 + doc + js, "JavaScript", 1, null)
            val jsEval = scope.get("document", scope) as? Scriptable
            val cookies = (jsEval?.get("cookie", jsEval) as String)
            val (cookieKey, cookieValue) = cookies.split(";")[0].split("=")

            val (key, keyValue) = keyRegex.find(playText)?.destructured
                ?: throw RuntimeException("No keys found")

            val playTitle = playDoc.select("p.title-film-detail-1")
            val showId = playDoc.select("input[name=movie_id]").`val`().toIntOrNull()
            if (showId != null) {
                val isSeries = playTitle.text().contains("staffel", ignoreCase = true)
                val episodes = playDoc.select("div.movie_episode_wrapper li > a").mapNotNull {
                    it.toEpisode(showId, key, keyValue, mapOf(cookieKey to cookieValue))
                }
                isSeries to episodes
            } else {
                false to emptyList()
            }
        } else {
            false to emptyList()
        }

        return if (isSeries || episodes.size > 1) newTvSeriesLoadResponse(
            title,
            url,
            TvType.TvSeries,
            episodes
        ) {
            this.comingSoon = playUrl.isEmpty()
        } else
            newMovieLoadResponse(
                title,
                url,
                TvType.Movie,
                episodes.firstOrNull()?.data ?: ""
            ) {
                this.posterUrl = posterUrl
                this.year = year
                this.tags = tags

                if (duration != 0)
                    this.duration = duration

                this.recommendations = recommendations
                this.comingSoon = playUrl.isEmpty() || episodes.isEmpty()
            }
    }


    data class File(
        val file: String? = null,
        val type: String? = null,
        val label: String? = null
    )

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val parsed = parseJson<EpisodeData>(data)
        val url = "$mainUrl/movie/load-stream/${parsed.showId}/${parsed.episodeId}"
        val response = app.post(
            url,
            referer = parsed.episodeUrl,
            headers = mapOf(
                "X-Requested-With" to "XMLHttpRequest",
            ),
            data = mapOf(parsed.key to parsed.keyValue),
            cookies = parsed.cookies + cookies,
        ).text

        val urlRegex = Regex("""file['"].*?['"]([^'"]*)""")
        val link = urlRegex.find(response)?.groupValues!![1]

//        files.forEach { (isVip, list) ->
//            list.forEach file@{ file ->
//                if (file.file == null) return@file
        callback.invoke(
            ExtractorLink(
                this.name,
                this.name,
                link.replace("\\", ""),
                this.mainUrl,
//                        file.label?.getIntFromText() ?:
                Qualities.Unknown.value,
                true
//                        file.type?.contains("hls", ignoreCase = true) == true,
            )
        )
//            }
//        }

        return true // files.sumOf { it.second.size } > 0
    }
}
