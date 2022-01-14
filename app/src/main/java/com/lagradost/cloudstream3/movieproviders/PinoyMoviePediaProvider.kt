package com.lagradost.cloudstream3.movieproviders

import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.extractors.FEmbed
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor

class PinoyMoviePediaProvider : MainAPI() {
    override val name = "Pinoy Moviepedia"
    override val mainUrl = "https://pinoymoviepedia.ru"
    override val lang = "tl"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override val hasDownloadSupport = true
    override val hasMainPage = true
    override val hasQuickSearch = false

    override fun getMainPage(): HomePageResponse {
        val all = ArrayList<HomePageList>()
        val document = app.get(mainUrl).document
        val mainbody = document.getElementsByTag("body")
        // All rows will be hardcoded bc of the nature of the site
        val rows: List<Pair<String, String>> = listOf(
            Pair("Latest Movies", "featured-titles"),
            Pair("Movies", "dt-movies"),
            Pair("Digitally Restored", "genre_digitally-restored"),
            Pair("Action", "genre_action"),
            Pair("Romance", "genre_romance"),
            Pair("Comedy", "genre_comedy"),
            Pair("Family", "genre_family")
            //Pair("Adult +18", "genre_pinay-sexy-movies")
        )
        rows.forEach { item ->
            val title = item.first
            val inner = mainbody?.select("div#${item.second} > article")

            val elements: List<SearchResponse> = inner?.mapNotNull {
                if (it == null) {
                    return@mapNotNull null
                }
                // Get inner div from article
                val urlTitle = it.select("div.data") ?: return@mapNotNull null
                // Fetch details
                val link = fixUrlNull(urlTitle.select("a")?.attr("href")) ?: return@mapNotNull null
                val name = urlTitle.text() ?: ""
                val image = it.select("div.poster > img")?.attr("src")
                // Get Year from Title
                val year = try {
                    val rex = Regex("\\((\\d+)")
                    rex.find(name)?.value?.replace("(", "")?.toIntOrNull()
                } catch (e: Exception) { null }

                val tvType = TvType.Movie
                MovieSearchResponse(
                    name,
                    link,
                    this.name,
                    tvType,
                    image,
                    year,
                    null,
                )
            }?.distinctBy { c -> c.url } ?: listOf()
            // Add
            all.add(
                HomePageList(
                    title, elements
                )
            )
        }
        return HomePageResponse(all.filter { a -> a.list.isNotEmpty() })
    }

    override fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=${query}"
        val document = app.get(url).document.selectFirst("div.search-page")
            ?.select("div.result-item")

        return document?.mapNotNull {
            val inner = it.select("article") ?: return@mapNotNull null
            val details = inner.select("div.details") ?: return@mapNotNull null
            val link = fixUrlNull(details.select("div.title > a")?.attr("href")) ?: return@mapNotNull null

            val title = details.select("div.title")?.text() ?: ""
            val year = details.select("div.meta > span.year")?.text()?.toIntOrNull()
            val image = inner.select("div.image > div > a > img")?.attr("src")

            MovieSearchResponse(
                title,
                link,
                this.name,
                TvType.Movie,
                image,
                year
            )
        }?.distinctBy { c -> c.url } ?: listOf()
    }

    override fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val body = doc.getElementsByTag("body")
        val inner = body?.select("div.sheader")
        // Identify if movie or series
        val isTvSeries = doc.select("title")?.text()?.lowercase()?.contains("full episode -") ?: false

        // Video details
        val poster = inner?.select("div.poster > img")?.attr("src")
        val title = inner?.select("div.data > h1")?.firstOrNull()?.text() ?: ""
        val descript = body?.select("div#info > div.wp-content")?.text()
        val rex = Regex("\\((\\d+)")
        val yearRes = rex.find(title)?.value ?: ""
        //Log.i(this.name, "Result => (yearRes) ${yearRes}")
        val year = yearRes.replace("(", "").toIntOrNull()

        // Video links
        val playcontainer = body?.select("div#playcontainer")
        val listOfLinks: MutableList<String> = mutableListOf()
        playcontainer?.select("iframe")?.forEach { item ->
            val lnk = item?.attr("src")?.trim()
            //Log.i(this.name, "Result => (lnk) $lnk")
            if (!lnk.isNullOrEmpty()) {
                listOfLinks.add(lnk)
            }
        }

        // Parse episodes if series
        if (isTvSeries) {
            val episodeList = ArrayList<TvSeriesEpisode>()
            val epLinks = playcontainer?.select("div > div > div.source-box")
            //Log.i(this.name, "Result => (epList) ${epList}")
            body?.select("div#playeroptions > ul > li")?.forEach { ep ->
                val epTitle = ep.select("span.title")?.text()
                if (!epTitle.isNullOrEmpty()) {
                    val epNum = epTitle.lowercase().replace("episode", "").trim().toIntOrNull()
                    //Log.i(this.name, "Result => (epNum) ${epNum}")
                    val href = when (epNum != null && !epLinks.isNullOrEmpty()) {
                        true -> epLinks.select("div#source-player-$epNum")
                            ?.select("iframe")?.attr("src") ?: ""
                        false -> ""
                    }
                    val streamEpLink = listOf(href.trim()).toJson()
                    //Log.i(this.name, "Result => (streamEpLink $epNum) $streamEpLink")
                    episodeList.add(
                        TvSeriesEpisode(
                            name = null,
                            season = null,
                            episode = epNum,
                            data = streamEpLink,
                            posterUrl = poster,
                            date = null
                        )
                    )
                }
            }
            return TvSeriesLoadResponse(
                title,
                url,
                this.name,
                TvType.TvSeries,
                episodeList,
                poster,
                year,
                descript,
                null,
                null,
                null
            )
        }
        val streamlinks = listOfLinks.distinct().toJson()
        return MovieLoadResponse(title, url, this.name, TvType.Movie, streamlinks, poster, year, descript, null, null)
    }

    override fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // parse movie servers
        mapper.readValue<List<String>>(data).forEach { link ->
            if (link.contains("fembed.com")) {
                val extractor = FEmbed()
                extractor.domainUrl = "diasfem.com"
                extractor.getUrl(data).forEach {
                    callback.invoke(it)
                }
            } else {
                loadExtractor(link, mainUrl, callback)
            }
        }
        return true
    }
}