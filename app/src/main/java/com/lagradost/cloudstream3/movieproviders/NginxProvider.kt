package com.lagradost.cloudstream3.movieproviders

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities

class NginxProvider : MainAPI() {
    override var name = "Nginx"
    override val hasQuickSearch = false
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.AnimeMovie, TvType.TvSeries, TvType.Movie)

    companion object {
        var loginCredentials: String? = null
        var overrideUrl: String? = null
        const val ERROR_STRING = "No nginx url specified in the settings"
    }

    private fun getAuthHeader(): Map<String, String> {
        val url = overrideUrl ?: throw ErrorLoadingException(ERROR_STRING)
        mainUrl = url
        println("OVERRIDING URL TO $overrideUrl")
        if (mainUrl == "NONE" || mainUrl.isBlank()) {
            throw ErrorLoadingException(ERROR_STRING)
        }

        val localCredentials = loginCredentials
        if (localCredentials == null || localCredentials.trim() == ":") {
            return mapOf("Authorization" to "Basic ")  // no Authorization headers
        }

        val basicAuthToken =
            base64Encode(localCredentials.toByteArray())  // will this be loaded when not using the provider ??? can increase load

        return mapOf("Authorization" to "Basic $basicAuthToken")
    }

    override suspend fun load(url: String): LoadResponse {
        val authHeader =
            getAuthHeader()  // call again because it isn't reloaded if in main class and storedCredentials loads after
        // url can be tvshow.nfo for series or mediaRootUrl for movies

        val mainRootDocument = app.get(url, authHeader).document

        val nfoUrl = url + mainRootDocument.getElementsByAttributeValueContaining("href", ".nfo")
            .attr("href")  // metadata url file

        val metadataDocument = app.get(nfoUrl, authHeader).document  // get the metadata nfo file

        val isMovie = !nfoUrl.contains("tvshow.nfo")

        val title = metadataDocument.selectFirst("title")!!.text()

        val description = metadataDocument.selectFirst("plot")!!.text()

        if (isMovie) {
            val poster = metadataDocument.selectFirst("thumb")!!.text()
            val trailer = metadataDocument.select("trailer").mapNotNull {
                it?.text()?.replace(
                    "plugin://plugin.video.youtube/play/?video_id=",
                    "https://www.youtube.com/watch?v="
                )
            }
            val partialUrl =
                mainRootDocument.getElementsByAttributeValueContaining("href", ".nfo").attr("href")
                    .replace(".nfo", ".")
            val date = metadataDocument.selectFirst("year")?.text()?.toIntOrNull()
            val ratingAverage = metadataDocument.selectFirst("value")?.text()?.toIntOrNull()
            val tagsList = metadataDocument.select("genre")
                .mapNotNull {   // all the tags like action, thriller ...
                    it?.text()

                }


            val dataList =
                mainRootDocument.getElementsByAttributeValueContaining(  // list of all urls of the webpage
                    "href",
                    partialUrl
                )

            val data = url + dataList.firstNotNullOf { item ->
                item.takeIf {
                    (!it.attr("href").contains(".nfo") && !it.attr("href").contains(".jpg"))
                }
            }.attr("href").toString()  // exclude poster and nfo (metadata) file

            return newMovieLoadResponse(
                title,
                url,
                TvType.Movie,
                data
            ) {
                this.year = date
                this.plot = description
                this.rating = ratingAverage
                this.tags = tagsList
                addTrailer(trailer)
                addPoster(poster, authHeader)
            }
        } else  // a tv serie
        {
            val list = ArrayList<Pair<Int, String>>()
            val mediaRootUrl = url.replace("tvshow.nfo", "")
            val posterUrl = mediaRootUrl + "poster.jpg"
            val mediaRootDocument = app.get(mediaRootUrl, authHeader).document
            val seasons =
                mediaRootDocument.getElementsByAttributeValueContaining("href", "Season%20")


            val tagsList = metadataDocument.select("genre")
                .mapNotNull {   // all the tags like action, thriller ...; unused variable
                    it?.text()
                }

            //val actorsList = document.select("actor")
            //    ?.mapNotNull {   // all the tags like action, thriller ...; unused variable
            //        it?.text()
            //    }

            seasons.forEach { element ->
                val season =
                    element.attr("href").replace("Season%20", "").replace("/", "").toIntOrNull()
                val href = mediaRootUrl + element.attr("href")
                if (season != null && season > 0 && href.isNotBlank()) {
                    list.add(Pair(season, href))
                }
            }

            if (list.isEmpty()) throw ErrorLoadingException("No Seasons Found")

            val episodeList = ArrayList<Episode>()


            list.apmap { (seasonInt, seasonString) ->
                val seasonDocument = app.get(seasonString, authHeader).document
                val episodes = seasonDocument.getElementsByAttributeValueContaining(
                    "href",
                    ".nfo"
                ) // get metadata
                episodes.forEach { episode ->
                    val nfoDocument = app.get(
                        seasonString + episode.attr("href"),
                        authHeader
                    ).document // get episode metadata file
                    val epNum = nfoDocument.selectFirst("episode")?.text()?.toIntOrNull()
                    val poster =
                        seasonString + episode.attr("href").replace(".nfo", "-thumb.jpg")
                    val name = nfoDocument.selectFirst("title")!!.text()
                    // val seasonInt = nfoDocument.selectFirst("season").text().toIntOrNull()
                    val date = nfoDocument.selectFirst("aired")?.text()
                    val plot = nfoDocument.selectFirst("plot")?.text()

                    val dataList = seasonDocument.getElementsByAttributeValueContaining(
                        "href",
                        episode.attr("href").replace(".nfo", "")
                    )
                    val data = seasonString + dataList.firstNotNullOf { item ->
                        item.takeIf {
                            (!it.attr("href").contains(".nfo") && !it.attr("href").contains(".jpg"))
                        }
                    }.attr("href").toString()  // exclude poster and nfo (metadata) file

                    episodeList.add(
                        newEpisode(data) {
                            this.name = name
                            this.season = seasonInt
                            this.episode = epNum
                            this.posterUrl = poster  // will require headers too
                            this.description = plot
                            addDate(date)
                        }
                    )
                }
            }
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodeList) {
                this.name = title
                this.url = url
                this.episodes = episodeList
                this.plot = description
                this.tags = tagsList
                addPoster(posterUrl, authHeader)
            }
        }

    }


    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // loadExtractor(data, null) { callback(it.copy(headers=authHeader)) }
        val authHeader =
            getAuthHeader()  // call again because it isn't reloaded if in main class and storedCredentials loads after
        callback.invoke(
            ExtractorLink(
                name,
                name,
                data,
                data,  // referer not needed
                Qualities.Unknown.value,
                false,
                authHeader,
            )
        )

        return true
    }


    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val authHeader =
            getAuthHeader()  // call again because it isn't reloaded if in main class and storedCredentials loads after

        val document = app.get(mainUrl, authHeader).document
        val categories = document.select("a")
        val returnList = categories.mapNotNull {
            val categoryTitle = it.text()  // get the category title like Movies or Series
            if (categoryTitle != "../" && categoryTitle != "Music/") {  // exclude parent dir and Music dir
                val href = it?.attr("href")
                val categoryPath = fixUrlNull(href?.trim())
                    ?: return@mapNotNull null // get the url of the category; like http://192.168.1.10/media/Movies/

                val categoryDocument = app.get(
                    categoryPath,
                    authHeader
                ).document // queries the page http://192.168.1.10/media/Movies/
                val contentLinks = categoryDocument.select("a")
                val currentList = contentLinks.mapNotNull { head ->
                    if (head.attr("href") != "../") {
                        try {
                            val mediaRootUrl =
                                categoryPath + head.attr("href")// like http://192.168.1.10/media/Series/Chernobyl/
                            val mediaDocument = app.get(mediaRootUrl, authHeader).document
                            val nfoFilename = mediaDocument.getElementsByAttributeValueContaining(
                                "href",
                                ".nfo"
                            )[0].attr("href")
                            val isMovieType = nfoFilename != "tvshow.nfo"
                            val nfoPath =
                                mediaRootUrl + nfoFilename // must exist or will raise errors, only the first one is taken
                            val nfoContent =
                                app.get(nfoPath, authHeader).document  // all the metadata

                            if (isMovieType) {
                                val movieName = nfoContent.select("title").text()
                                val posterUrl = mediaRootUrl + "poster.jpg"
                                return@mapNotNull newMovieSearchResponse(
                                    movieName,
                                    mediaRootUrl,
                                    TvType.Movie,
                                ) {
                                    addPoster(posterUrl, authHeader)
                                }
                            } else {  // tv serie
                                val serieName = nfoContent.select("title").text()

                                val posterUrl = mediaRootUrl + "poster.jpg"

                                newTvSeriesSearchResponse(
                                    serieName,
                                    nfoPath,
                                    TvType.TvSeries,
                                ) {
                                    addPoster(posterUrl, authHeader)
                                }
                            }
                        } catch (e: Exception) {  // can cause issues invisible errors
                            null
                            //logError(e) // not working because it changes the return type of currentList to Any
                        }
                    } else null
                }
                if (currentList.isNotEmpty() && categoryTitle != "../") {  // exclude upper dir
                    HomePageList(categoryTitle, currentList)
                } else null
            } else null  // the path is ../ which is parent directory
        }
        // if (returnList.isEmpty()) return null // maybe doing nothing idk
        return HomePageResponse(returnList)
    }
}
