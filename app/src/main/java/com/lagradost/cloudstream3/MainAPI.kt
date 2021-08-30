package com.lagradost.cloudstream3

import android.app.Activity
import androidx.preference.PreferenceManager
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.lagradost.cloudstream3.animeproviders.*
import com.lagradost.cloudstream3.movieproviders.*
import com.lagradost.cloudstream3.torrentproviders.*
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
        TrailersToProvider(),
        //ShiroProvider(), // v2 fucked me
        //AnimePaheProvider(), //ddos guard
        AnimeFlickProvider(),
        GogoanimeProvider(),
        TenshiProvider(),
        WcoProvider(),
        // MeloMovieProvider(), // Captcha for links
        DubbedAnimeProvider(),
        HDMProvider(),
        //LookMovieProvider(), // RECAPTCHA (Please allow up to 5 seconds...)
        VMoveeProvider(),
        WatchCartoonOnlineProvider(),
        AllMoviesForYouProvider(),
        AsiaFlixProvider(),
    )

    val restrictedApis = arrayListOf(
        NyaaProvider(),
    )

    fun getApiFromName(apiName: String?): MainAPI {
        for (api in apis) {
            if (apiName == api.name)
                return api
        }
        return apis[defProvider]
    }

    fun getApiFromNameNull(apiName: String?): MainAPI? {
        for (api in apis) {
            if (apiName == api.name)
                return api
        }
        return null
    }

    fun LoadResponse.getId(): Int {
        return url.replace(getApiFromName(apiName).mainUrl, "").replace("/", "").hashCode()
    }

    fun Activity.getApiSettings(): HashSet<String> {
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)

        val hashSet = HashSet<String>()
        hashSet.addAll(apis.map { it.name })

        return settingsManager.getStringSet(
            this.getString(R.string.search_providers_list_key),
            hashSet
        )?.toHashSet() ?: hashSet
    }

    fun Activity.getApiTypeSettings(): HashSet<TvType> {
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)
        val hashSet = HashSet<TvType>()
        hashSet.addAll(TvType.values())
        val list = settingsManager.getStringSet(
            this.getString(R.string.search_types_list_key),
            hashSet.map { it.name }.toMutableSet()
        )

        if(list.isNullOrEmpty()) return hashSet

        val names = TvType.values().map { it.name }.toHashSet()
        val realSet = list.filter { names.contains(it) }.map { TvType.valueOf(it) }.toHashSet()
        if(realSet.isEmpty()) return hashSet

        return realSet
    }
}

/**Every provider will **not** have try catch built in, so handle exceptions when calling these functions*/
abstract class MainAPI {
    open val name = "NONE"
    open val mainUrl = "NONE"

    /**If link is stored in the "data" string, so links can be instantly loaded*/
    open val instantLinkLoading = false

    /**Set false if links require referer or for some reason cant be played on a chromecast*/
    open val hasChromecastSupport = true

    /**If all links are m3u8 then set this to false*/
    open val hasDownloadSupport = true

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

    open fun getMainPage(): HomePageResponse? {
        throw NotImplementedError()
    }

    open fun search(query: String): List<SearchResponse>? {
        throw NotImplementedError()
    }

    open fun quickSearch(query: String): List<SearchResponse>? {
        throw NotImplementedError()
    }

    open fun load(url: String): LoadResponse? {
        throw NotImplementedError()
    }

    /**Callback is fired once a link is found, will return true if method is executed successfully*/
    open fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        throw NotImplementedError()
    }
}

class ErrorLoadingException(message: String? = null) : Exception(message)

fun parseRating(ratingString: String?): Int? {
    if (ratingString == null) return null
    val floatRating = ratingString.toFloatOrNull() ?: return null
    return (floatRating * 10).toInt()
}

fun MainAPI.fixUrl(url: String): String {
    if (url.startsWith("http")) {
        return url
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

fun sortUrls(urls: List<ExtractorLink>): List<ExtractorLink> {
    return urls.sortedBy { t -> -t.quality }
}

fun sortSubs(urls: List<SubtitleFile>): List<SubtitleFile> {
    val encounteredTimes = HashMap<String, Int>()
    return urls.sortedBy { t -> t.lang }.map {
        val times = encounteredTimes[it.lang]?.plus(1) ?: 1
        encounteredTimes[it.lang] = times

        SubtitleFile("${it.lang} ${if (times > 1) "($times)" else ""}", it.url)
    }
}

/** https://www.imdb.com/title/tt2861424/ -> tt2861424 */
fun imdbUrlToId(url: String): String {
    return url
        .removePrefix("https://www.imdb.com/title/")
        .removePrefix("https://imdb.com/title/tt2861424/")
        .replace("/", "")
}

fun imdbUrlToIdNullable(url: String?): String? {
    if (url == null) return null
    return imdbUrlToId(url)
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
}

// IN CASE OF FUTURE ANIME MOVIE OR SMTH
fun TvType.isMovieType(): Boolean {
    return this == TvType.Movie || this == TvType.AnimeMovie || this == TvType.Torrent
}

data class SubtitleFile(val lang: String, val url: String)

class HomePageResponse(
    val items: List<HomePageList>
)

class HomePageList(
    val name: String,
    val list: List<SearchResponse>
)

interface SearchResponse {
    val name: String
    val url: String
    val apiName: String
    val type: TvType
    val posterUrl: String?
    val id: Int?
}

data class AnimeSearchResponse(
    override val name: String,
    override val url: String,
    override val apiName: String,
    override val type: TvType,

    override val posterUrl: String?,
    val year: Int?,

    val otherName: String?,
    val dubStatus: EnumSet<DubStatus>?,
    val dubEpisodes: Int?,
    val subEpisodes: Int?,
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
    val duration: String?
    val trailerUrl: String?
}

fun LoadResponse?.isEpisodeBased(): Boolean {
    if (this == null) return false
    return (this is AnimeLoadResponse || this is TvSeriesLoadResponse) && (this.type == TvType.TvSeries || this.type == TvType.Anime)
}

fun LoadResponse?.isAnimeBased(): Boolean {
    if (this == null) return false
    return (this.type == TvType.Anime || this.type == TvType.ONA) // && (this is AnimeLoadResponse)
}

data class AnimeEpisode(
    val url: String,
    val name: String? = null,
    val posterUrl: String? = null,
    val date: String? = null,
    val rating: Int? = null,
    val descript: String? = null,
)

data class TorrentLoadResponse(
    override val name: String,
    override val url: String,
    override val apiName: String,
    val magnet: String?,
    val torrent: String?,
    override val plot: String?,
    override val type: TvType = TvType.Torrent,
    override val posterUrl: String? = null,
    override val year: Int? = null,
    override val rating: Int? = null,
    override val tags: List<String>? = null,
    override val duration: String? = null,
    override val trailerUrl: String? = null
) : LoadResponse

data class AnimeLoadResponse(
    val engName: String?,
    val japName: String?,
    override val name: String,
    override val url: String,
    override val apiName: String,
    override val type: TvType,

    override val posterUrl: String?,
    override val year: Int?,

    val dubEpisodes: List<AnimeEpisode>?,
    val subEpisodes: List<AnimeEpisode>?,
    val showStatus: ShowStatus?,

    override val plot: String?,
    override val tags: List<String>? = null,
    val synonyms: List<String>? = null,

    val malId: Int? = null,
    val anilistId: Int? = null,
    override val rating: Int? = null,
    override val duration: String? = null,
    override val trailerUrl: String? = null,
) : LoadResponse

data class MovieLoadResponse(
    override val name: String,
    override val url: String,
    override val apiName: String,
    override val type: TvType,
    val dataUrl: String,

    override val posterUrl: String?,
    override val year: Int?,
    override val plot: String?,

    val imdbId: String?,
    override val rating: Int? = null,
    override val tags: List<String>? = null,
    override val duration: String? = null,
    override val trailerUrl: String? = null,
) : LoadResponse

data class TvSeriesEpisode(
    val name: String?,
    val season: Int?,
    val episode: Int?,
    val data: String,
    val posterUrl: String? = null,
    val date: String? = null,
    val rating: Int? = null,
    val descript: String? = null,
)

data class TvSeriesLoadResponse(
    override val name: String,
    override val url: String,
    override val apiName: String,
    override val type: TvType,
    val episodes: List<TvSeriesEpisode>,

    override val posterUrl: String?,
    override val year: Int?,
    override val plot: String?,

    val showStatus: ShowStatus?,
    val imdbId: String?,
    override val rating: Int? = null,
    override val tags: List<String>? = null,
    override val duration: String? = null,
    override val trailerUrl: String? = null,
) : LoadResponse
