package com.lagradost.cloudstream3.movieproviders

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.extractors.FEmbed
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor

class PinoyMoviePediaProvider : MainAPI() {
    override var name = "Pinoy Moviepedia"
    override var mainUrl = "https://pinoymoviepedia.ru"
    override var lang = "tl"
    override val supportedTypes = setOf(TvType.AsianDrama)
    override val hasDownloadSupport = true
    override val hasMainPage = true
    override val hasQuickSearch = false

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
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
                if (it == null) { return@mapNotNull null }

                // Get inner div from article
                val urlTitle = it.select("div.data") ?: return@mapNotNull null
                // Fetch details
                val link = fixUrlNull(urlTitle.select("a")?.attr("href")) ?: return@mapNotNull null
                val image = it.select("div.poster > img")?.attr("src")

                // Get Title and Year
                val titleYear = it.select("div.data.dfeatur")
                var name = titleYear?.select("h3")?.text() ?: ""
                var year = titleYear?.select("span")?.text()?.toIntOrNull()

                if (name.isEmpty()) {
                    name = urlTitle.select("h3")?.text() ?: ""
                    year = titleYear?.select("span")?.text()?.takeLast(4)?.toIntOrNull()
                }
                // Get year from name
                if (year == null) {
                    val rex = Regex("\\((\\d+)")
                    year = rex.find(name)?.value?.replace("(", "")?.toIntOrNull()
                }
                //Get quality
                val qual = getQualityFromString(it.selectFirst("span.quality")?.text())

                MovieSearchResponse(
                    name = name,
                    url = link,
                    apiName = this.name,
                    TvType.Movie,
                    posterUrl = image,
                    year = year,
                    quality = qual
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

    override suspend fun search(query: String): List<SearchResponse> {
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
            val qual = getQualityFromString(it.selectFirst("span.quality")?.text())

            MovieSearchResponse(
                name = title,
                url = link,
                apiName = this.name,
                TvType.Movie,
                posterUrl = image,
                year = year,
                quality = qual
            )
        }?.distinctBy { c -> c.url } ?: listOf()
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val body = doc.getElementsByTag("body")
        val inner = body?.select("div.sheader")
        // Identify if movie or series
        val isTvSeries = doc.select("title")?.text()?.lowercase()?.contains("full episode -") ?: false

        // Video details
        val data = inner?.select("div.data")
        val poster = inner?.select("div.poster > img")?.attr("src")
        val title = data?.select("h1")?.firstOrNull()?.text()?.trim() ?: ""
        val descript = body?.select("div#info > div.wp-content p")?.firstOrNull()?.text()
        val rex = Regex("\\((\\d+)")
        val yearRes = rex.find(title)?.value ?: ""
        //Log.i(this.name, "Result => (yearRes) ${yearRes}")
        val year = yearRes.replace("(", "").toIntOrNull()
        val tags = data?.select("div.sgeneros > a")?.mapNotNull { tag ->
            tag?.text()?.trim() ?: return@mapNotNull null
        }?.toList()
        val recList = body?.select("div#single_relacionados > article")?.mapNotNull {
            val a = it.select("a") ?: return@mapNotNull null
            val aUrl = a.attr("href") ?: return@mapNotNull null
            val aImg = a.select("img")?.attr("src")
            val aName = a.select("img")?.attr("alt") ?: return@mapNotNull null
            val aYear = try {
                aName.trim().takeLast(5).removeSuffix(")").toIntOrNull()
            } catch (e: Exception) { null }

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
        val playcontainer = body?.select("div#playcontainer")
        val listOfLinks: MutableList<String> = mutableListOf()
        playcontainer?.select("iframe")?.forEach { item ->
            val lnk = item?.attr("src")?.trim() ?: ""
            //Log.i(this.name, "Result => (lnk) $lnk")
            if (lnk.isNotEmpty()) {
                listOfLinks.add(lnk)
            }
        }

        // Parse episodes if series
        if (isTvSeries) {
            val episodeList = ArrayList<Episode>()
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
                        Episode(
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
                name = title,
                url = url,
                apiName = this.name,
                type = TvType.AsianDrama,
                episodes = episodeList,
                posterUrl = poster,
                year = year,
                plot = descript,
                tags = tags,
                recommendations = recList
            )
        }
        val streamlinks = listOfLinks.distinct().toJson()
        return MovieLoadResponse(
            name = title,
            url = url,
            apiName = this.name,
            type = TvType.Movie,
            dataUrl = streamlinks,
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
        tryParseJson<List<String>>(data)?.apmap { link ->
            count++
            if (link.contains("fembed.com")) {
                val extractor = FEmbed()
                extractor.domainUrl = "diasfem.com"
                extractor.getUrl(data).forEach {
                    callback.invoke(it)
                }
            } else {
                loadExtractor(link, mainUrl, subtitleCallback, callback)
            }
        }
        return count > 0
    }
}