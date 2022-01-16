package com.lagradost.cloudstream3

import android.annotation.SuppressLint
import android.content.Context
import androidx.preference.PreferenceManager
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.lagradost.cloudstream3.animeproviders.*
import com.lagradost.cloudstream3.movieproviders.*
import com.lagradost.cloudstream3.ui.player.SubtitleData
import com.lagradost.cloudstream3.utils.ExtractorLink
import java.util.*

const val USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"

//val baseHeader = mapOf("User-Agent" to USER_AGENT)
val mapper = JsonMapper.builder().addModule(KotlinModule())
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).build()!!

object APIHolder {
    val unixTime: Long
        get() = System.currentTimeMillis() / 1000L
    val unixTimeMS: Long
        get() = System.currentTimeMillis()

    private const val defProvider = 0

    val apis = arrayListOf(
        PelisplusProvider(),
        PelisplusHDProvider(),
        GogoanimeProvider(),
        AllAnimeProvider(),
        //ShiroProvider(), // v2 fucked me
        //AnimePaheProvider(), //ddos guard
        AnimeFlickProvider(),

        TenshiProvider(),
        WcoProvider(),
        // MeloMovieProvider(), // Captcha for links
        DubbedAnimeProvider(),
        IHaveNoTvProvider(), // Documentaries provider
        //LookMovieProvider(), // RECAPTCHA (Please allow up to 5 seconds...)
        VMoveeProvider(),
        WatchCartoonOnlineProvider(),
        AllMoviesForYouProvider(),

        VidEmbedProvider(),
        VfFilmProvider(),
        VfSerieProvider(),
        FrenchStreamProvider(),

        AsianLoadProvider(),

        SflixProvider("https://sflix.to", "Sflix"),
        SflixProvider("https://dopebox.to", "Dopebox"),

        //TmdbProvider(),

        FilmanProvider(),

        ZoroProvider(),
        PinoyMoviePediaProvider(),
        PinoyHDXyzProvider(),
        PinoyMoviesEsProvider(),
        TrailersTwoProvider(),
        DramaSeeProvider(),
        WatchAsianProvider(),
        KdramaHoodProvider(),
        AkwamProvider()
    )

    val restrictedApis = arrayListOf(
        // TrailersToProvider(), // be aware that this is fuckery
        // NyaaProvider(), // torrents in cs3 is wack
        // ThenosProvider(), // ddos protection and wacked links
        AsiaFlixProvider(),
    )

    private val backwardsCompatibleProviders = arrayListOf(
        KawaiifuProvider(), // removed due to cloudflare
        HDMProvider(),// removed due to cloudflare
    )

    fun getApiFromName(apiName: String?): MainAPI {
        return getApiFromNameNull(apiName) ?: apis[defProvider]
    }

    fun getApiFromNameNull(apiName: String?): MainAPI? {
        if (apiName == null) return null
        for (api in apis) {
            if (apiName == api.name)
                return api
        }
        for (api in restrictedApis) {
            if (apiName == api.name)
                return api
        }
        for (api in backwardsCompatibleProviders) {
            if (apiName == api.name)
                return api
        }
        return null
    }

    fun LoadResponse.getId(): Int {
        return url.replace(getApiFromName(apiName).mainUrl, "").replace("/", "").hashCode()
    }

    fun Context.getApiSettings(): HashSet<String> {
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)

        val hashSet = HashSet<String>()
        val activeLangs = getApiProviderLangSettings()
        hashSet.addAll(apis.filter { activeLangs.contains(it.lang) }.map { it.name })

        val set = settingsManager.getStringSet(
            this.getString(R.string.search_providers_list_key),
            hashSet
        )?.toHashSet() ?: hashSet

        val list = HashSet<String>()
        for (name in set) {
            val api = getApiFromNameNull(name) ?: continue
            if (activeLangs.contains(api.lang)) {
                list.add(name)
            }
        }
        if (list.isEmpty()) return hashSet
        return list
    }

    fun Context.getApiDubstatusSettings(): HashSet<DubStatus> {
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)
        val hashSet = HashSet<DubStatus>()
        hashSet.addAll(DubStatus.values())
        val list = settingsManager.getStringSet(
            this.getString(R.string.display_sub_key),
            hashSet.map { it.name }.toMutableSet()
        ) ?: return hashSet

        val names = DubStatus.values().map { it.name }.toHashSet()
        //if(realSet.isEmpty()) return hashSet

        return list.filter { names.contains(it) }.map { DubStatus.valueOf(it) }.toHashSet()
    }

    fun Context.getApiProviderLangSettings(): HashSet<String> {
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)
        val hashSet = HashSet<String>()
        hashSet.add("en") // def is only en
        val list = settingsManager.getStringSet(
            this.getString(R.string.provider_lang_key),
            hashSet.toMutableSet()
        )

        if (list.isNullOrEmpty()) return hashSet
        return list.toHashSet()
    }

    fun Context.getApiTypeSettings(): HashSet<TvType> {
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)
        val hashSet = HashSet<TvType>()
        hashSet.addAll(TvType.values())
        val list = settingsManager.getStringSet(
            this.getString(R.string.search_types_list_key),
            hashSet.map { it.name }.toMutableSet()
        )

        if (list.isNullOrEmpty()) return hashSet

        val names = TvType.values().map { it.name }.toHashSet()
        val realSet = list.filter { names.contains(it) }.map { TvType.valueOf(it) }.toHashSet()
        if (realSet.isEmpty()) return hashSet

        return realSet
    }

    fun Context.filterProviderByPreferredMedia(): List<MainAPI> {
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)
        val currentPrefMedia =
            settingsManager.getInt(this.getString(R.string.prefer_media_type_key), 0)
        val langs = this.getApiProviderLangSettings()
        val allApis = apis.filter { langs.contains(it.lang) }.filter { api -> api.hasMainPage }
        return if (currentPrefMedia < 1) {
            allApis
        } else {
            // Filter API depending on preferred media type
            val listEnumAnime = listOf(TvType.Anime, TvType.AnimeMovie, TvType.ONA)
            val listEnumMovieTv = listOf(TvType.Movie, TvType.TvSeries, TvType.Cartoon)
            val mediaTypeList = if (currentPrefMedia == 1) listEnumMovieTv else listEnumAnime

            val filteredAPI =
                allApis.filter { api -> api.supportedTypes.any { it in mediaTypeList } }
            filteredAPI
        }
    }
}

/**Every provider will **not** have try catch built in, so handle exceptions when calling these functions*/
abstract class MainAPI {
    open val name = "NONE"
    open val mainUrl = "NONE"

    open val lang = "en" // ISO_639_1 check SubtitleHelper

    /**If link is stored in the "data" string, so links can be instantly loaded*/
    open val instantLinkLoading = false

    /**Set false if links require referer or for some reason cant be played on a chromecast*/
    open val hasChromecastSupport = true

    /**If all links are encrypted then set this to false*/
    open val hasDownloadSupport = true

    /**Used for testing and can be used to disable the providers if WebView is not available*/
    open val usesWebView = false

    open val hasMainPage = false
    open val hasQuickSearch = false

    open val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Cartoon,
        TvType.Anime,
        TvType.ONA,
    )

    open val vpnStatus = VPNStatus.None
    open val providerType = ProviderType.DirectProvider

    suspend open fun getMainPage(): HomePageResponse? {
        throw NotImplementedError()
    }

    suspend open fun search(query: String): List<SearchResponse>? {
        throw NotImplementedError()
    }

    suspend open fun quickSearch(query: String): List<SearchResponse>? {
        throw NotImplementedError()
    }

    suspend open fun load(url: String): LoadResponse? {
        throw NotImplementedError()
    }

    /**Callback is fired once a link is found, will return true if method is executed successfully*/
    suspend open fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        throw NotImplementedError()
    }
}

/** Might need a different implementation for desktop*/
@SuppressLint("NewApi")
fun base64Decode(string: String): String {
    return try {
        String(android.util.Base64.decode(string, android.util.Base64.DEFAULT), Charsets.ISO_8859_1)
    } catch (e: Exception) {
        String(Base64.getDecoder().decode(string))
    }
}

class ErrorLoadingException(message: String? = null) : Exception(message)

fun parseRating(ratingString: String?): Int? {
    if (ratingString == null) return null
    val floatRating = ratingString.toFloatOrNull() ?: return null
    return (floatRating * 10).toInt()
}

fun MainAPI.fixUrlNull(url: String?): String? {
    if (url.isNullOrEmpty()) {
        return null
    }
    return fixUrl(url)
}

fun MainAPI.fixUrl(url: String): String {
    if (url.startsWith("http")) {
        return url
    }
    if (url.isEmpty()) {
        return ""
    }

    val startsWithNoHttp = url.startsWith("//")
    if (startsWithNoHttp) {
        return "https:$url"
    } else {
        if (url.startsWith('/')) {
            return mainUrl + url
        }
        return "$mainUrl/$url"
    }
}

fun sortUrls(urls: Set<ExtractorLink>): List<ExtractorLink> {
    return urls.sortedBy { t -> -t.quality }
}

fun sortSubs(subs: Set<SubtitleData>): List<SubtitleData> {
    return subs.sortedBy { it.name }
}

fun capitalizeString(str: String): String {
    return capitalizeStringNullable(str) ?: str
}

fun capitalizeStringNullable(str: String?): String? {
    if (str == null)
        return null
    return try {
        str.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    } catch (e: Exception) {
        str
    }
}

/** https://www.imdb.com/title/tt2861424/ -> tt2861424 */
fun imdbUrlToId(url: String): String? {
    return Regex("/title/(tt[0-9]*)").find(url)?.groupValues?.get(1)
        ?: Regex("tt[0-9]{5,}").find(url)?.groupValues?.get(0)
}

fun imdbUrlToIdNullable(url: String?): String? {
    if (url == null) return null
    return imdbUrlToId(url)
}

enum class ProviderType {
    // When data is fetched from a 3rd party site like imdb
    MetaProvider,

    // When all data is from the site
    DirectProvider,
}

enum class VPNStatus {
    None,
    MightBeNeeded,
    Torrent,
}

enum class ShowStatus {
    Completed,
    Ongoing,
}

enum class DubStatus {
    Dubbed,
    Subbed,
}

enum class TvType {
    Movie,
    AnimeMovie,
    TvSeries,
    Cartoon,
    Anime,
    ONA,
    Torrent,
    Documentary,
}

// IN CASE OF FUTURE ANIME MOVIE OR SMTH
fun TvType.isMovieType(): Boolean {
    return this == TvType.Movie || this == TvType.AnimeMovie || this == TvType.Torrent
}

// returns if the type has an anime opening
fun TvType.isAnimeOp(): Boolean {
    return this == TvType.Anime || this == TvType.ONA
}

data class SubtitleFile(val lang: String, val url: String)

class HomePageResponse(
    val items: List<HomePageList>
)

class HomePageList(
    val name: String,
    var list: List<SearchResponse>
)

interface SearchResponse {
    val name: String
    val url: String
    val apiName: String
    val type: TvType?
    val posterUrl: String?
    val id: Int?
}

data class AnimeSearchResponse(
    override val name: String,
    override val url: String,
    override val apiName: String,
    override val type: TvType,

    override val posterUrl: String?,
    val year: Int? = null,
    val dubStatus: EnumSet<DubStatus>? = null,

    val otherName: String? = null,
    val dubEpisodes: Int? = null,
    val subEpisodes: Int? = null,
    override val id: Int? = null,
) : SearchResponse

data class TorrentSearchResponse(
    override val name: String,
    override val url: String,
    override val apiName: String,
    override val type: TvType,

    override val posterUrl: String?,
    override val id: Int? = null,
) : SearchResponse

data class MovieSearchResponse(
    override val name: String,
    override val url: String,
    override val apiName: String,
    override val type: TvType,

    override val posterUrl: String?,
    val year: Int?,
    override val id: Int? = null,
) : SearchResponse

data class TvSeriesSearchResponse(
    override val name: String,
    override val url: String,
    override val apiName: String,
    override val type: TvType,

    override val posterUrl: String?,
    val year: Int?,
    val episodes: Int?,
    override val id: Int? = null,
) : SearchResponse

interface LoadResponse {
    val name: String
    val url: String
    val apiName: String
    val type: TvType
    val posterUrl: String?
    val year: Int?
    val plot: String?
    val rating: Int? // 0-100
    val tags: List<String>?
    var duration: Int? // in minutes
    val trailerUrl: String?
    val recommendations: List<SearchResponse>?
}

fun LoadResponse?.isEpisodeBased(): Boolean {
    if (this == null) return false
    return (this is AnimeLoadResponse || this is TvSeriesLoadResponse) && this.type.isEpisodeBased()
}

fun LoadResponse?.isAnimeBased(): Boolean {
    if (this == null) return false
    return (this.type == TvType.Anime || this.type == TvType.ONA) // && (this is AnimeLoadResponse)
}

fun TvType?.isEpisodeBased(): Boolean {
    if (this == null) return false
    return (this == TvType.TvSeries || this == TvType.Anime)
}

data class AnimeEpisode(
    val url: String,
    var name: String? = null,
    var posterUrl: String? = null,
    var date: String? = null,
    var rating: Int? = null,
    var description: String? = null,
    var episode: Int? = null,
)

data class TorrentLoadResponse(
    override var name: String,
    override var url: String,
    override var apiName: String,
    var magnet: String?,
    var torrent: String?,
    override var plot: String?,
    override var type: TvType = TvType.Torrent,
    override var posterUrl: String? = null,
    override var year: Int? = null,
    override var rating: Int? = null,
    override var tags: List<String>? = null,
    override var duration: Int? = null,
    override var trailerUrl: String? = null,
    override var recommendations: List<SearchResponse>? = null,
) : LoadResponse

data class AnimeLoadResponse(
    var engName: String? = null,
    var japName: String? = null,
    override var name: String,
    override var url: String,
    override var apiName: String,
    override var type: TvType,

    override var posterUrl: String? = null,
    override var year: Int? = null,

    var episodes: HashMap<DubStatus, List<AnimeEpisode>> = hashMapOf(),
    var showStatus: ShowStatus? = null,

    override var plot: String? = null,
    override var tags: List<String>? = null,
    var synonyms: List<String>? = null,

    var malId: Int? = null,
    var anilistId: Int? = null,
    override var rating: Int? = null,
    override var duration: Int? = null,
    override var trailerUrl: String? = null,
    override var recommendations: List<SearchResponse>? = null,
) : LoadResponse

fun AnimeLoadResponse.addEpisodes(status: DubStatus, episodes: List<AnimeEpisode>?) {
    if (episodes == null) return
    this.episodes[status] = episodes
}

fun MainAPI.newAnimeLoadResponse(
    name: String,
    url: String,
    type: TvType,
    initializer: AnimeLoadResponse.() -> Unit = { }
): AnimeLoadResponse {
    val builder = AnimeLoadResponse(name = name, url = url, apiName = this.name, type = type)
    builder.initializer()
    return builder
}

data class MovieLoadResponse(
    override var name: String,
    override var url: String,
    override var apiName: String,
    override var type: TvType,
    var dataUrl: String,

    override var posterUrl: String? = null,
    override var year: Int? = null,
    override var plot: String? = null,

    var imdbId: String? = null,
    override var rating: Int? = null,
    override var tags: List<String>? = null,
    override var duration: Int? = null,
    override var trailerUrl: String? = null,
    override var recommendations: List<SearchResponse>? = null,
) : LoadResponse

fun MainAPI.newMovieLoadResponse(
    name: String,
    url: String,
    type: TvType,
    dataUrl: String,
    initializer: MovieLoadResponse.() -> Unit = { }
): MovieLoadResponse {
    val builder = MovieLoadResponse(
        name = name,
        url = url,
        apiName = this.name,
        type = type,
        dataUrl = dataUrl
    )
    builder.initializer()
    return builder
}

fun LoadResponse.setDuration(input: String?) {
    if (input == null) return
    Regex("([0-9]*)h.*?([0-9]*)m").matchEntire(input)?.groupValues?.let { values ->
        if (values.size == 3) {
            val hours = values[1].toIntOrNull()
            val minutes = values[2].toIntOrNull()
            this.duration = if (minutes != null && hours != null) {
                hours * 60 + minutes
            } else null
        }
    }
    Regex("([0-9]*)m").matchEntire(input)?.groupValues?.let { values ->
        if (values.size == 2) {
            this.duration = values[1].toIntOrNull()
        }
    }
}

data class TvSeriesEpisode(
    val name: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val data: String,
    val posterUrl: String? = null,
    val date: String? = null,
    val rating: Int? = null,
    val description: String? = null,
)

data class TvSeriesLoadResponse(
    override var name: String,
    override var url: String,
    override var apiName: String,
    override var type: TvType,
    var episodes: List<TvSeriesEpisode>,

    override var posterUrl: String? = null,
    override var year: Int? = null,
    override var plot: String? = null,

    var showStatus: ShowStatus? = null,
    var imdbId: String? = null,
    override var rating: Int? = null,
    override var tags: List<String>? = null,
    override var duration: Int? = null,
    override var trailerUrl: String? = null,
    override var recommendations: List<SearchResponse>? = null,
) : LoadResponse

fun MainAPI.newTvSeriesLoadResponse(
    name: String,
    url: String,
    type: TvType,
    episodes: List<TvSeriesEpisode>,
    initializer: TvSeriesLoadResponse.() -> Unit = { }
): TvSeriesLoadResponse {
    val builder = TvSeriesLoadResponse(
        name = name,
        url = url,
        apiName = this.name,
        type = type,
        episodes = episodes
    )
    builder.initializer()
    return builder
}
