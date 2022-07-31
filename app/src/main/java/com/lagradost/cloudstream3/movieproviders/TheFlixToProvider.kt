package com.lagradost.cloudstream3.movieproviders

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.network.cookies
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName


class TheFlixToProvider : MainAPI() {
    companion object {
        var latestCookies: Map<String, String> = emptyMap()
    }

    override var name = "TheFlix.to"
    override var mainUrl = "https://theflix.to"
    override val instantLinkLoading = false
    override val hasMainPage = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
    )



    data class HomeJson(
        @JsonProperty("props") val props: HomeProps = HomeProps(),
    )

    data class HomeProps(
        @JsonProperty("pageProps") val pageProps: PageProps = PageProps(),
    )

    data class PageProps(
        @JsonProperty("moviesListTrending") val moviesListTrending: MoviesListTrending = MoviesListTrending(),
        @JsonProperty("moviesListNewArrivals") val moviesListNewArrivals: MoviesListNewArrivals = MoviesListNewArrivals(),
        @JsonProperty("tvsListTrending") val tvsListTrending: TvsListTrending = TvsListTrending(),
        @JsonProperty("tvsListNewEpisodes") val tvsListNewEpisodes: TvsListNewEpisodes = TvsListNewEpisodes(),
    )


    data class MoviesListTrending(
        @JsonProperty("docs") val docs: ArrayList<Docs> = arrayListOf(),
        @JsonProperty("total") val total: Int? = null,
        @JsonProperty("page") val page: Int? = null,
        @JsonProperty("limit") val limit: Int? = null,
        @JsonProperty("pages") val pages: Int? = null,
        @JsonProperty("type") val type: String? = null,
    )

    data class MoviesListNewArrivals(
        @JsonProperty("docs") val docs: ArrayList<Docs> = arrayListOf(),
        @JsonProperty("total") val total: Int? = null,
        @JsonProperty("page") val page: Int? = null,
        @JsonProperty("limit") val limit: Int? = null,
        @JsonProperty("pages") val pages: Int? = null,
        @JsonProperty("type") val type: String? = null,
    )

    data class TvsListTrending(
        @JsonProperty("docs") val docs: ArrayList<Docs> = arrayListOf(),
        @JsonProperty("total") val total: Int? = null,
        @JsonProperty("page") val page: Int? = null,
        @JsonProperty("limit") val limit: Int? = null,
        @JsonProperty("pages") val pages: Int? = null,
        @JsonProperty("type") val type: String? = null,
    )

    data class TvsListNewEpisodes(
        @JsonProperty("docs") val docs: ArrayList<Docs> = arrayListOf(),
        @JsonProperty("total") val total: Int? = null,
        @JsonProperty("page") val page: Int? = null,
        @JsonProperty("limit") val limit: Int? = null,
        @JsonProperty("pages") val pages: Int? = null,
        @JsonProperty("type") val type: String? = null,
    )

    data class Docs(
        @JsonProperty("name") val name: String = String(),
        @JsonProperty("originalLanguage") val originalLanguage: String? = null,
        @JsonProperty("popularity") val popularity: Double? = null,
        @JsonProperty("runtime") val runtime: Int? = null,
        @JsonProperty("status") val status: String? = null,
        @JsonProperty("voteAverage") val voteAverage: Double? = null,
        @JsonProperty("voteCount") val voteCount: Int? = null,
        @JsonProperty("cast") val cast: String? = null,
        @JsonProperty("director") val director: String? = null,
        @JsonProperty("overview") val overview: String? = null,
        @JsonProperty("posterUrl") val posterUrl: String? = null,
        @JsonProperty("releaseDate") val releaseDate: String? = null,
        @JsonProperty("createdAt") val createdAt: String? = null,
        @JsonProperty("updatedAt") val updatedAt: String? = null,
        @JsonProperty("conversionDate") val conversionDate: String? = null,
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("available") val available: Boolean? = null,
        @JsonProperty("videos"           ) val videos           : ArrayList<String>? = arrayListOf(),
    )


    private suspend fun getCookies(): Map<String, String> {
        //  val cookieResponse = app.post(
        //      "https://theflix.to:5679/authorization/session/continue?contentUsageType=Viewing",
        //    headers = mapOf(
        //          "Host" to "theflix.to:5679",
        //          "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; rv:91.0) Gecko/20100101 Firefox/91.0",
        //          "Accept" to "application/json, text/plain,"
        //          "Accept-Language" to "en-US,en;q=0.5",
        //          "Content-Type" to "application/json;charset=utf-8",
        //          "Content-Length" to "35",
        //          "Origin" to "https://theflix.to",
        //          "DNT" to "1",
        //          "Connection" to "keep-alive",
        //          "Referer" to "https://theflix.to/",
        //          "Sec-Fetch-Dest" to "empty",
        //          "Sec-Fetch-Mode" to "cors",
        //          "Sec-Fetch-Site" to "same-site",)).okhttpResponse.headers.values("Set-Cookie")

        val cookies = app.post(
            "$mainUrl:5679/authorization/session/continue?contentUsageType=Viewing",
            headers = mapOf(
                "Host" to "theflix.to:5679",
                "User-Agent" to USER_AGENT,
                "Accept" to "application/json, text/plain, */*",
                "Accept-Language" to "en-US,en;q=0.5",
                "Content-Type" to "application/json;charset=utf-8",
                "Content-Length" to "35",
                "Origin" to mainUrl,
                "DNT" to "1",
                "Connection" to "keep-alive",
                "Referer" to mainUrl,
                "Sec-Fetch-Dest" to "empty",
                "Sec-Fetch-Mode" to "cors",
                "Sec-Fetch-Site" to "same-site",)
        ).cookies
        /* val cookieRegex = Regex("(theflix\\..*?id\\=[a-zA-Z0-9]{0,8}[a-zA-Z0-9_-]+)")
       val findcookie = cookieRegex.findAll(cookieResponse.toString()).map { it.value }.toList()
       val cookiesstring = findcookie.toString().replace(", ","; ").replace("[","").replace("]","")
       val cookiesmap = mapOf("Cookie" to cookiesstring) */
        latestCookies = cookies
        return latestCookies
    }


    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val items = ArrayList<HomePageList>()
        val doc = app.get(mainUrl).document
        val scriptText = doc.selectFirst("script[type=application/json]")!!.data()
        if (scriptText.contains("moviesListTrending")) {
            val json = parseJson<HomeJson>(scriptText)
            val homePageProps = json.props.pageProps
            listOf(
                Triple(
                    homePageProps.moviesListNewArrivals.docs,
                    homePageProps.moviesListNewArrivals.type,
                    "New Movie arrivals"
                ),
                Triple(
                    homePageProps.moviesListTrending.docs,
                    homePageProps.moviesListTrending.type,
                    "Trending Movies"
                ),
                Triple(
                    homePageProps.tvsListTrending.docs,
                    homePageProps.tvsListTrending.type,
                    "Trending TV Series"
                ),
                Triple(
                    homePageProps.tvsListNewEpisodes.docs,
                    homePageProps.tvsListNewEpisodes.type,
                    "New Episodes"
                )
            ).map { (docs, type, homename) ->
                val home = docs.map { info ->
                    val title = info.name
                    val poster = info.posterUrl
                    val typeinfo =
                        if (type?.contains("TV") == true) TvType.TvSeries else TvType.Movie
                    val link =
                        if (typeinfo == TvType.Movie) "$mainUrl/movie/${info.id}-${cleanTitle(title)}"
                        else "$mainUrl/tv-show/${info.id}-${cleanTitle(title).replace("?","")}/season-1/episode-1"
                    TvSeriesSearchResponse(
                        title,
                        link,
                        this.name,
                        typeinfo,
                        poster,
                        null,
                        null,
                    )
                }
                items.add(HomePageList(homename, home))
            }

        }

        if (items.size <= 0) throw ErrorLoadingException()
        return HomePageResponse(items)
    }

    data class SearchJson(
        @JsonProperty("props") val props: SearchProps = SearchProps(),
    )

    data class SearchProps(
        @JsonProperty("pageProps") val pageProps: SearchPageProps = SearchPageProps(),
    )

    data class SearchPageProps(
        @JsonProperty("mainList") val mainList: SearchMainList = SearchMainList(),
    )

    data class SearchMainList(
        @JsonProperty("docs") val docs: ArrayList<Docs> = arrayListOf(),
        @JsonProperty("total") val total: Int? = null,
        @JsonProperty("page") val page: Int? = null,
        @JsonProperty("limit") val limit: Int? = null,
        @JsonProperty("pages") val pages: Int? = null,
        @JsonProperty("type") val type: String? = null,
    )


    override suspend fun search(query: String): List<SearchResponse> {
        val search = ArrayList<SearchResponse>()
        val urls = listOf(
            "$mainUrl/movies/trending?search=$query",
            "$mainUrl/tv-shows/trending?search=$query"
        )
        urls.apmap { url ->
            val doc = app.get(url).document
            val scriptText = doc.selectFirst("script[type=application/json]")!!.data()
            if (scriptText.contains("pageProps")) {
                val json = parseJson<SearchJson>(scriptText)
                val searchPageProps = json.props.pageProps.mainList
                val pair = listOf(Pair(searchPageProps.docs, searchPageProps.type))
                pair.map { (docs, type) ->
                    docs.map { info ->
                        val title = info.name
                        val poster = info.posterUrl
                        val typeinfo =
                            if (type?.contains("TV") == true) TvType.TvSeries else TvType.Movie
                        val link = if (typeinfo == TvType.Movie) "$mainUrl/movie/${info.id}-${
                            cleanTitle(title)
                        }"
                        else "$mainUrl/tv-show/${info.id}-${cleanTitle(title)}/season-1/episode-1"
                        if (typeinfo == TvType.Movie) {
                            search.add(
                                MovieSearchResponse(
                                    title,
                                    link,
                                    this.name,
                                    TvType.Movie,
                                    poster,
                                    null
                                )
                            )
                        } else {
                            search.add(
                                TvSeriesSearchResponse(
                                    title,
                                    link,
                                    this.name,
                                    TvType.TvSeries,
                                    poster,
                                    null,
                                    null
                                )
                            )
                        }
                    }
                }
            }
        }
        return search
    }
    data class LoadMain (
        @JsonProperty("props"         ) val props         : LoadProps?         = LoadProps(),
        @JsonProperty("page"          ) val page          : String?        = null,
        @JsonProperty("buildId"       ) val buildId       : String?        = null,
        @JsonProperty("runtimeConfig" ) val runtimeConfig : RuntimeConfig? = RuntimeConfig(),
        @JsonProperty("isFallback"    ) val isFallback    : Boolean?       = null,
        @JsonProperty("gssp"          ) val gssp          : Boolean?       = null,
        @JsonProperty("customServer"  ) val customServer  : Boolean?       = null,
        @JsonProperty("appGip"        ) val appGip        : Boolean?       = null
    )

    data class LoadProps (
        @JsonProperty("pageProps" ) val pageProps : LoadPageProps? = LoadPageProps(),
        @JsonProperty("__N_SSP"   ) val _NSSP     : Boolean?   = null
    )

    data class LoadPageProps (
        @JsonProperty("selectedTv"          ) val selectedTv          : TheFlixMetadata?          = TheFlixMetadata(),
        @JsonProperty("movie") val movie: TheFlixMetadata? = TheFlixMetadata(),
        @JsonProperty("recommendationsList" ) val recommendationsList : RecommendationsList? = RecommendationsList(),
        @JsonProperty("basePageSegments"    ) val basePageSegments    : ArrayList<String>?    = arrayListOf()
    )

    data class TheFlixMetadata (
        @JsonProperty("episodeRuntime"   ) val episodeRuntime   : Int?               = null,
        @JsonProperty("name"             ) val name             : String?            = null,
        @JsonProperty("numberOfSeasons"  ) val numberOfSeasons  : Int?               = null,
        @JsonProperty("numberOfEpisodes" ) val numberOfEpisodes : Int?               = null,
        @JsonProperty("originalLanguage" ) val originalLanguage : String?            = null,
        @JsonProperty("popularity"       ) val popularity       : Double?            = null,
        @JsonProperty("status"           ) val status           : String?            = null,
        @JsonProperty("voteAverage"      ) val voteAverage      : Double?            = null,
        @JsonProperty("voteCount"        ) val voteCount        : Int?               = null,
        @JsonProperty("cast"             ) val cast             : String?            = null,
        @JsonProperty("director"         ) val director         : String?            = null,
        @JsonProperty("overview"         ) val overview         : String?            = null,
        @JsonProperty("posterUrl"        ) val posterUrl        : String?            = null,
        @JsonProperty("releaseDate"      ) val releaseDate      : String?            = null,
        @JsonProperty("createdAt"        ) val createdAt        : String?            = null,
        @JsonProperty("updatedAt"        ) val updatedAt        : String?            = null,
        @JsonProperty("id"               ) val id               : Int?               = null,
        @JsonProperty("available"        ) val available        : Boolean?           = null,
        @JsonProperty("genres"           ) val genres           : ArrayList<Genres>?  = arrayListOf(),
        @JsonProperty("seasons"          ) val seasons          : ArrayList<Seasons>? = arrayListOf(),
        @JsonProperty("videos"           ) val videos           : ArrayList<String>? = arrayListOf(),
        @JsonProperty("runtime"          ) val runtime          : Int?              = null,
    )
    data class Seasons(
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("numberOfEpisodes") val numberOfEpisodes: Int? = null,
        @JsonProperty("seasonNumber") val seasonNumber: Int? = null,
        @JsonProperty("overview") val overview: String? = null,
        @JsonProperty("posterUrl") val posterUrl: String? = null,
        @JsonProperty("releaseDate") val releaseDate: String? = null,
        @JsonProperty("createdAt") val createdAt: String? = null,
        @JsonProperty("updatedAt") val updatedAt: String? = null,
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("episodes") val episodes: ArrayList<Episodes>? = arrayListOf()
    )

    data class Episodes(
        @JsonProperty("episodeNumber") val episodeNumber: Int? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("seasonNumber") val seasonNumber: Int? = null,
        @JsonProperty("voteAverage") val voteAverage: Double? = null,
        @JsonProperty("voteCount") val voteCount: Int? = null,
        @JsonProperty("overview") val overview: String? = null,
        @JsonProperty("releaseDate") val releaseDate: String? = null,
        @JsonProperty("createdAt") val createdAt: String? = null,
        @JsonProperty("updatedAt") val updatedAt: String? = null,
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("videos") val videos: ArrayList<String>? = arrayListOf()
    )


    data class Genres (
        @JsonProperty("name" ) val name : String? = null,
        @JsonProperty("id"   ) val id   : Int?    = null
    )

    data class RuntimeConfig (
        @JsonProperty("AddThisService" ) val AddThisService : RuntimeConfigData? = RuntimeConfigData(),
        @JsonProperty("Application"    ) val Application    : RuntimeConfigData?    = RuntimeConfigData(),
        @JsonProperty("GtmService"     ) val GtmService     : RuntimeConfigData?     = RuntimeConfigData(),
        @JsonProperty("Services"       ) val Services       : RuntimeConfigData?       = RuntimeConfigData(),
    )

    data class RuntimeConfigData(
        @JsonProperty("PublicId" ) val PublicId : String? = null,
        @JsonProperty("ContentUsageType"              ) val ContentUsageType              : String?  = null,
        @JsonProperty("IsDevelopmentMode"             ) val IsDevelopmentMode             : Boolean? = null,
        @JsonProperty("IsDevelopmentOrProductionMode" ) val IsDevelopmentOrProductionMode : Boolean? = null,
        @JsonProperty("IsProductionMode"              ) val IsProductionMode              : Boolean? = null,
        @JsonProperty("IsStagingMode"                 ) val IsStagingMode                 : Boolean? = null,
        @JsonProperty("IsTestMode"                    ) val IsTestMode                    : Boolean? = null,
        @JsonProperty("Mode"                          ) val Mode                          : String?  = null,
        @JsonProperty("Name"                          ) val Name                          : String?  = null,
        @JsonProperty("Url"                           ) val Url                           : String?  = null,
        @JsonProperty("UseFilterInfoInUrl"            ) val UseFilterInfoInUrl            : Boolean? = null,
        @JsonProperty("TrackingId" ) val TrackingId : String? = null,
        @JsonProperty("Server"     ) val Server     : Server?     = Server(),
        @JsonProperty("TmdbServer" ) val TmdbServer : TmdbServer? = TmdbServer(),
    )

    data class TmdbServer (
        @JsonProperty("Url" ) val Url : String? = null
    )


    data class Server (
        @JsonProperty("Url" ) val Url : String? = null
    )

    data class RecommendationsList (
        @JsonProperty("docs"  ) val docs  : ArrayList<Docs> = arrayListOf(),
        @JsonProperty("total" ) val total : Int?            = null,
        @JsonProperty("page"  ) val page  : Int?            = null,
        @JsonProperty("limit" ) val limit : Int?            = null,
        @JsonProperty("pages" ) val pages : Int?            = null,
        @JsonProperty("type"  ) val type  : String?         = null,
    )

    private fun cleanTitle(title: String): String {
        val dotTitle = title.substringBefore("/season")
        if (dotTitle.contains(Regex("\\..\\."))) { //For titles containing more than two dots (S.W.A.T.)
            return (dotTitle.removeSuffix(".")
                .replace(" - ", "-")
                .replace(".", "-").replace(" ", "-")
                .replace("-&", "")
                .replace(Regex("(:|-&)"), "")
                .replace("'", "-")).lowercase()
        }
        return (title
            .replace(" - ", "-")
            .replace(" ", "-")
            .replace("-&", "")
            .replace("/", "-")
            .replace(Regex("(:|-&|\\.)"), "")
            .replace("'", "-")).lowercase()
    }

    private suspend fun getLoadMan(url: String): LoadMain {
        getCookies()
        val og = app.get(url, headers = latestCookies)
        val soup = og.document
        val script = soup.selectFirst("script[type=application/json]")!!.data()
        return parseJson(script)
    }

    override suspend fun load(url: String): LoadResponse? {
        val tvtype = if (url.contains("movie")) TvType.Movie else TvType.TvSeries
        val json = getLoadMan(url)
        val episodes = ArrayList<Episode>()
        val isMovie = tvtype == TvType.Movie
        val pageMain = json.props?.pageProps

        val metadata: TheFlixMetadata? = if (isMovie) pageMain?.movie else pageMain?.selectedTv

        val available = metadata?.available

        val comingsoon = !available!!

        val movieId = metadata.id

        val movietitle = metadata.name

        val poster = metadata.posterUrl

        val description = metadata.overview

        if (!isMovie) {
            metadata.seasons?.map { seasons ->
                val seasonPoster = seasons.posterUrl ?: metadata.posterUrl
                seasons.episodes?.forEach { epi ->
                    val episodenu = epi.episodeNumber
                    val seasonum = epi.seasonNumber
                    val title = epi.name
                    val epDesc = epi.overview
                    val test = epi.videos
                    val ratinginfo = (epi.voteAverage)?.times(10)?.toInt()
                    val rating = if (ratinginfo?.equals(0) == true) null else ratinginfo
                    val eps = Episode(
                        "$mainUrl/tv-show/$movieId-${cleanTitle(movietitle!!)}/season-$seasonum/episode-$episodenu",
                        title,
                        seasonum,
                        episodenu,
                        description = epDesc!!,
                        posterUrl = seasonPoster,
                        rating = rating,
                    )
                    if (test!!.isNotEmpty()) {
                        episodes.add(eps)
                    } else {
                        //Nothing, will prevent seasons/episodes with no videos to be added
                    }
                }
            }
        }
        val rating = metadata.voteAverage?.toFloat()?.times(1000)?.toInt()

        val tags = metadata.genres?.mapNotNull { it.name }

        val recommendationsitem = pageMain?.recommendationsList?.docs?.map { loadDocs ->
            val title = loadDocs.name
            val posterrec = loadDocs.posterUrl
            val link = if (isMovie) "$mainUrl/movie/${loadDocs.id}-${cleanTitle(title)}"
            else "$mainUrl/tv-show/${loadDocs.id}-${cleanTitle(title)}/season-1/episode-1"
            MovieSearchResponse(
                title,
                link,
                this.name,
                tvtype,
                posterrec,
                year = null
            )
        }

        val year = metadata.releaseDate?.substringBefore("-")

        val runtime = metadata.runtime?.div(60) ?: metadata.episodeRuntime?.div(60)
        val cast = metadata.cast?.split(",")

        return when (tvtype) {
            TvType.TvSeries -> {
                return newTvSeriesLoadResponse(movietitle!!, url, TvType.TvSeries, episodes) {
                    this.posterUrl = poster
                    this.year = year?.toIntOrNull()
                    this.plot = description
                    this.duration = runtime
                    addActors(cast)
                    this.tags = tags
                    this.recommendations = recommendationsitem
                    this.comingSoon = comingsoon
                    this.rating = rating
                }
            }
            TvType.Movie -> {
                newMovieLoadResponse(movietitle!!, url, TvType.Movie, url) {
                    this.year = year?.toIntOrNull()
                    this.posterUrl = poster
                    this.plot = description
                    this.duration = runtime
                    addActors(cast)
                    this.tags = tags
                    this.recommendations = recommendationsitem
                    this.comingSoon = comingsoon
                    this.rating = rating
                }
            }
            else -> null
        }
    }


    data class VideoData (
        @JsonProperty("url"           ) val url           : String?        = null,
        @JsonProperty("id"            ) val id            : String?        = null,
        @JsonProperty("type"          ) val type          : String?        = null,
    )


    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val json = getLoadMan(data)
        val authhost = json.runtimeConfig?.Services?.Server?.Url
        val isMovie = data.contains("/movie/")
        val qualityReg = Regex("(\\d+p)")
        if (isMovie){
            json.props?.pageProps?.movie?.videos?.apmap { id ->
                val jsonmovie = app.get("$authhost/movies/videos/$id/request-access?contentUsageType=Viewing",
                    headers = latestCookies).parsedSafe<VideoData>() ?: return@apmap false
                val extractedlink = jsonmovie.url
                if (!extractedlink.isNullOrEmpty()) {
                    val quality = qualityReg.find(extractedlink)?.value ?: ""
                    callback(
                        ExtractorLink(
                            name,
                            name,
                            extractedlink,
                            "",
                            getQualityFromName(quality),
                            false
                        )
                    )
                } else null
            }
        }
        else
        {
            val dataRegex = Regex("(season-(\\d+)\\/episode-(\\d+))")
            val cleandatainfo = dataRegex.find(data)?.value?.replace(Regex("(season-|episode-)"),"")?.replace("/","x")
            val tesatt = cleandatainfo.let { str ->
                str?.split("x")?.mapNotNull { subStr -> subStr.toIntOrNull() }
            }
            val epID = tesatt?.getOrNull(1)
            val seasonid = tesatt?.getOrNull(0)
            json.props?.pageProps?.selectedTv?.seasons?.map {
                it.episodes?.map {
                    val epsInfo = Triple(it.seasonNumber, it.episodeNumber, it.videos)
                    if (epsInfo.first == seasonid && epsInfo.second == epID) {
                        epsInfo.third?.apmap { id ->
                            val jsonserie = app.get("$authhost/tv/videos/$id/request-access?contentUsageType=Viewing", headers = latestCookies).parsedSafe<VideoData>() ?: return@apmap false
                            val extractedlink = jsonserie.url
                            if (!extractedlink.isNullOrEmpty()) {
                                val quality = qualityReg.find(extractedlink)?.value ?: ""
                                callback(
                                    ExtractorLink(
                                        name,
                                        name,
                                        extractedlink,
                                        "",
                                        getQualityFromName(quality),
                                        false
                                    )
                                )
                            } else null
                        }
                    }
                }
            }
        }
        return true
    }
}
