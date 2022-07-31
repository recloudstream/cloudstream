package com.lagradost.cloudstream3.movieproviders

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.animeproviders.GogoanimeProvider.Companion.extractVidstream
import com.lagradost.cloudstream3.extractors.Vidstream
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName
import org.jsoup.Jsoup
import java.net.URI

/** Needs to inherit from MainAPI() to
 * make the app know what functions to call
 */
open class VidstreamProviderTemplate : MainAPI() {
    open val homePageUrlList = listOf<String>()
    open val vidstreamExtractorUrl: String? = null

    /**
     *  Used to generate encrypted video links.
     *  Try keys from other providers before cracking
     *  one yourself.
     * */
    // Userscript to get the keys:

    /*
    // ==UserScript==
    // @name        Easy keys
    // @namespace   Violentmonkey Scripts
    // @match       https://*/streaming.php*
    // @grant       none
    // @version     1.0
    // @author      LagradOst
    // @description 4/16/2022, 2:05:31 PM
    // ==/UserScript==

    let encrypt = CryptoJS.AES.encrypt;
    CryptoJS.AES.encrypt = (message, key, cfg) => {
        let realKey = CryptoJS.enc.Utf8.stringify(key);
        let realIv = CryptoJS.enc.Utf8.stringify(cfg.iv);

        var result = encrypt(message, key, cfg);
        let realResult = CryptoJS.enc.Utf8.stringify(result);

        popup = "Encrypt key: " + realKey + "\n\nIV: " + realIv + "\n\nMessage: " + message + "\n\nResult: " + realResult;
        alert(popup);

        return result;
    };

    let decrypt = CryptoJS.AES.decrypt;
    CryptoJS.AES.decrypt = (message, key, cfg) => {
        let realKey = CryptoJS.enc.Utf8.stringify(key);
        let realIv = CryptoJS.enc.Utf8.stringify(cfg.iv);

        let result = decrypt(message, key, cfg);
        let realResult = CryptoJS.enc.Utf8.stringify(result);

        popup = "Decrypt key: " + realKey + "\n\nIV: " + realIv + "\n\nMessage: " + message + "\n\nResult: " + realResult;
        alert(popup);

        return result;
    };

     */
     */

    open val iv: String? = null
    open val secretKey: String? = null
    open val secretDecryptKey: String? = null

    /** Generated the key from IV and ID */
    open val isUsingAdaptiveKeys: Boolean = false

    /**
     * Generate data for the encrypt-ajax automatically (only on supported sites)
     * See $("script[data-name='episode']")[0].dataset.value
     * */
    open val isUsingAdaptiveData: Boolean = false


//    // mainUrl is good to have as a holder for the url to make future changes easier.
//    override val mainUrl: String
//        get() = "https://vidembed.cc"
//
//    // name is for how the provider will be named which is visible in the UI, no real rules for this.
//    override val name: String
//        get() = "VidEmbed"

    // hasQuickSearch defines if quickSearch() should be called, this is only when typing the searchbar
    // gives results on the site instead of bringing you to another page.
    // if hasQuickSearch is true and quickSearch() hasn't been overridden you will get errors.
    // VidEmbed actually has quick search on their site, but the function wasn't implemented.
    override val hasQuickSearch = false

    // If getMainPage() is functional, used to display the homepage in app, an optional, but highly encouraged endevour.
    override val hasMainPage = true

    // Searching returns a SearchResponse, which can be one of the following: AnimeSearchResponse, MovieSearchResponse, TorrentSearchResponse, TvSeriesSearchResponse
    // Each of the classes requires some different data, but always has some critical things like name, poster and url.
    override suspend fun search(query: String): ArrayList<SearchResponse> {
        // Simply looking at devtools network is enough to spot a request like:
        // https://vidembed.cc/search.html?keyword=neverland where neverland is the query, can be written as below.
        val link = "$mainUrl/search.html?keyword=$query"
        val html = app.get(link).text
        val soup = Jsoup.parse(html)

        return ArrayList(soup.select(".listing.items > .video-block").map { li ->
            // Selects the href in <a href="...">
            val href = fixUrl(li.selectFirst("a")!!.attr("href"))
            val poster = li.selectFirst("img")?.attr("src")

            // .text() selects all the text in the element, be careful about doing this while too high up in the html hierarchy
            val title = li.selectFirst(".name")!!.text()
            // Use get(0) and toIntOrNull() to prevent any possible crashes, [0] or toInt() will error the search on unexpected values.
            val year = li.selectFirst(".date")?.text()?.split("-")?.get(0)?.toIntOrNull()

            TvSeriesSearchResponse(
                // .trim() removes unwanted spaces in the start and end.
                if (!title.contains("Episode")) title else title.split("Episode")[0].trim(),
                href,
                this.name,
                TvType.TvSeries,
                poster, year,
                // You can't get the episodes from the search bar.
                null
            )
        })
    }


    // Load, like the name suggests loads the info page, where all the episodes and data usually is.
    // Like search you should return either of: AnimeLoadResponse, MovieLoadResponse, TorrentLoadResponse, TvSeriesLoadResponse.
    override suspend fun load(url: String): LoadResponse? {
        // Gets the url returned from searching.
        val html = app.get(url).text
        val soup = Jsoup.parse(html)

        var title = soup.selectFirst("h1,h2,h3")!!.text()
        title = if (!title.contains("Episode")) title else title.split("Episode")[0].trim()

        val description = soup.selectFirst(".post-entry")?.text()?.trim()
        var poster: String? = null
        var year: Int? = null

        val episodes =
            soup.select(".listing.items.lists > .video-block").withIndex().map { (_, li) ->
                val epTitle = if (li.selectFirst(".name") != null)
                    if (li.selectFirst(".name")!!.text().contains("Episode"))
                        "Episode " + li.selectFirst(".name")!!.text().split("Episode")[1].trim()
                    else
                        li.selectFirst(".name")!!.text()
                else ""
                val epThumb = li.selectFirst("img")?.attr("src")
                val epDate = li.selectFirst(".meta > .date")!!.text()

                if (poster == null) {
                    poster = li.selectFirst("img")?.attr("onerror")?.split("=")?.get(1)
                        ?.replace(Regex("[';]"), "")
                }

                val epNum = Regex("""Episode (\d+)""").find(epTitle)?.destructured?.component1()
                    ?.toIntOrNull()
                if (year == null) {
                    year = epDate.split("-")[0].toIntOrNull()
                }
                newEpisode(li.selectFirst("a")!!.attr("href")) {
                    this.episode = epNum
                    this.posterUrl = epThumb
                    addDate(epDate)
                }
            }.reversed()

        // Make sure to get the type right to display the correct UI.
        val tvType =
            if (episodes.size == 1 && episodes[0].name == title) TvType.Movie else TvType.TvSeries

        return when (tvType) {
            TvType.TvSeries -> {
                TvSeriesLoadResponse(
                    title,
                    url,
                    this.name,
                    tvType,
                    episodes,
                    poster,
                    year,
                    description,
                    ShowStatus.Ongoing,
                    null,
                    null
                )
            }
            TvType.Movie -> {
                MovieLoadResponse(
                    title,
                    url,
                    this.name,
                    tvType,
                    episodes[0].data,
                    poster,
                    year,
                    description,
                    null,
                    null
                )
            }
            else -> null
        }
    }

    // This loads the homepage, which is basically a collection of search results with labels.
    // Optional function, but make sure to enable hasMainPage if you program this.
    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val urls = homePageUrlList
        val homePageList = ArrayList<HomePageList>()
        // .pmap {} is used to fetch the different pages in parallel
        urls.apmap { url ->
            val response = app.get(url, timeout = 20).text
            val document = Jsoup.parse(response)
            document.select("div.main-inner").forEach { inner ->
                // Always trim your text unless you want the risk of spaces at the start or end.
                val title = inner.select(".widget-title").text().trim()
                val elements = inner.select(".video-block").map {
                    val link = fixUrl(it.select("a").attr("href"))
                    val image = it.select(".picture > img").attr("src")
                    val name =
                        it.select("div.name").text().trim().replace(Regex("""[Ee]pisode \d+"""), "")
                    val isSeries = (name.contains("Season") || name.contains("Episode"))

                    if (isSeries) {
                        newTvSeriesSearchResponse(name, link) {
                            posterUrl = image
                        }
                    } else {
                        newMovieSearchResponse(name, link) {
                            posterUrl = image
                        }
                    }
                }

                homePageList.add(
                    HomePageList(
                        title, elements
                    )
                )
            }
        }
        return HomePageResponse(homePageList)
    }

    // loadLinks gets the raw .mp4 or .m3u8 urls from the data parameter in the episodes class generated in load()
    // See Episode(...) in this provider.
    // The data are usually links, but can be any other string to help aid loading the links.
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        // These callbacks are functions you should call when you get a link to a subtitle file or media file.
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // "?: return" is a very useful statement which returns if the iframe link isn't found.
        val iframeLink =
            Jsoup.parse(app.get(data).text).selectFirst("iframe")?.attr("src") ?: return false

        extractVidstream(
            iframeLink,
            this.name,
            callback,
            iv,
            secretKey,
            secretDecryptKey,
            isUsingAdaptiveKeys,
            isUsingAdaptiveData
        )
        // In this case the video player is a vidstream clone and can be handled by the vidstream extractor.
        // This case is a both unorthodox and you normally do not call extractors as they detect the url returned and does the rest.
        val vidstreamObject = Vidstream(vidstreamExtractorUrl ?: mainUrl)
        // https://vidembed.cc/streaming.php?id=MzUwNTY2&... -> MzUwNTY2
        val id = Regex("""id=([^&]*)""").find(iframeLink)?.groupValues?.get(1)

        if (id != null) {
            vidstreamObject.getUrl(id, isCasting, subtitleCallback, callback)
        }

        val html = app.get(fixUrl(iframeLink)).text
        val soup = Jsoup.parse(html)

        val servers = soup.select(".list-server-items > .linkserver").mapNotNull { li ->
            if (!li?.attr("data-video").isNullOrEmpty()) {
                Pair(li.text(), fixUrl(li.attr("data-video")))
            } else {
                null
            }
        }
        servers.apmap {
            // When checking strings make sure to make them lowercase and trimmed because edgecases like "beta server " wouldn't work otherwise.
            if (it.first.trim().equals("beta server", ignoreCase = true)) {
                // Group 1: link, Group 2: Label
                // Regex can be used to effectively parse small amounts of json without bothering with writing a json class.
                val sourceRegex =
                    Regex("""sources:[\W\w]*?file:\s*["'](.*?)["'][\W\w]*?label:\s*["'](.*?)["']""")
                val trackRegex =
                    Regex("""tracks:[\W\w]*?file:\s*["'](.*?)["'][\W\w]*?label:\s*["'](.*?)["']""")

                // Having a referer is often required. It's a basic security check most providers have.
                // Try to replicate what your browser does.
                val serverHtml = app.get(it.second, headers = mapOf("referer" to iframeLink)).text
                sourceRegex.findAll(serverHtml).forEach { match ->
                    callback.invoke(
                        ExtractorLink(
                            this.name,
                            match.groupValues.getOrNull(2)?.let { "${this.name} $it" } ?: this.name,
                            match.groupValues[1],
                            it.second,
                            // Useful function to turn something like "1080p" to an app quality.
                            getQualityFromName(match.groupValues.getOrNull(2) ?: ""),
                            // Kinda risky
                            // isM3u8 makes the player pick the correct extractor for the source.
                            // If isM3u8 is wrong the player will error on that source.
                            URI(match.groupValues[1]).path.endsWith(".m3u8"),
                        )
                    )
                }
                trackRegex.findAll(serverHtml).forEach { match ->
                    subtitleCallback.invoke(
                        SubtitleFile(
                            match.groupValues.getOrNull(2) ?: "Unknown",
                            match.groupValues[1]
                        )
                    )
                }
            }
        }

        return true
    }
}
