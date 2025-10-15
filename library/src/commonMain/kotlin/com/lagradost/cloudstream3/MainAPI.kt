@file:Suppress(
    "UNUSED",
    "UnusedReceiverParameter",
    "MemberVisibilityCanBePrivate"
)

package com.lagradost.cloudstream3

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.safe
import com.lagradost.cloudstream3.syncproviders.SyncIdName
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.Coroutines.mainWork
import com.lagradost.cloudstream3.utils.Coroutines.threadSafeListOf
import com.lagradost.cloudstream3.utils.SubtitleHelper.fromCodeToLangTagIETF
import com.lagradost.cloudstream3.utils.SubtitleHelper.fromLanguageToTagIETF
import com.lagradost.nicehttp.RequestBodyTypes
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URI
import java.text.SimpleDateFormat
import java.util.*
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

/**
 * API available only on prerelease builds.
 * Using it will cause stable to crash with `NoSuchMethodException`.
 */
@MustBeDocumented // Same as java.lang.annotation.Documented
@Retention(AnnotationRetention.BINARY) // This is only an IDE hint, and will not be used in the runtime
@RequiresOptIn(
    message = "This API is only available on prerelease builds. " +
              "Using it will cause CloudStream stable to crash.",
    level = RequiresOptIn.Level.ERROR
)
annotation class Prerelease

/**
 * Defines the constant for the all languages preference, if this is set then it is
 * the equivalent of all languages being set
 **/
const val AllLanguagesName = "universal"

const val USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Safari/537.36"

class ErrorLoadingException(message: String? = null) : Exception(message)

//val baseHeader = mapOf("User-Agent" to USER_AGENT)
val mapper = JsonMapper.builder().addModule(kotlinModule())
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).build()!!

object APIHolder {
    val unixTime: Long
        get() = System.currentTimeMillis() / 1000L
    val unixTimeMS: Long
        get() = System.currentTimeMillis()

    // ConcurrentModificationException is possible!!!
    val allProviders = threadSafeListOf<MainAPI>()

    fun initAll() {
        synchronized(allProviders) {
            for (api in allProviders) {
                api.init()
            }
        }
        apiMap = null
    }

    /** String extension function to Capitalize first char of string.*/
    fun String.capitalize(): String {
        return this.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }

    var apis: List<MainAPI> = threadSafeListOf()
    var apiMap: Map<String, Int>? = null

    fun addPluginMapping(plugin: MainAPI) {
        synchronized(apis) {
            apis = apis + plugin
        }
        initMap(true)
    }

    fun removePluginMapping(plugin: MainAPI) {
        synchronized(apis) {
            apis = apis.filter { it != plugin }
        }
        initMap(true)
    }

    private fun initMap(forcedUpdate: Boolean = false) {
        synchronized(apis) {
            if (apiMap == null || forcedUpdate)
                apiMap = apis.mapIndexed { index, api -> api.name to index }.toMap()
        }
    }

    fun getApiFromNameNull(apiName: String?): MainAPI? {
        if (apiName == null) return null
        synchronized(allProviders) {
            initMap()
            synchronized(apis) {
                return apiMap?.get(apiName)?.let { apis.getOrNull(it) }
                // Leave the ?. null check, it can crash regardless
                    ?: allProviders.firstOrNull { it.name == apiName }
            }
        }
    }

    fun getApiFromUrlNull(url: String?): MainAPI? {
        if (url == null) return null
        synchronized(allProviders) {
            allProviders.forEach { api ->
                if (url.startsWith(api.mainUrl)) return api
            }
        }
        return null
    }

    /**
     * Gets the website captcha token
     * discovered originally by https://github.com/ahmedgamal17
     * optimized by https://github.com/justfoolingaround
     *
     * @param url the main url, likely the same website you found the key from.
     * @param key used to fill https://www.google.com/recaptcha/api.js?render=....
     *
     * @param referer the referer for the google.com/recaptcha/api.js... request, optional.
     * */

    // Try document.select("script[src*=https://www.google.com/recaptcha/api.js?render=]").attr("src").substringAfter("render=")
    // To get the key
    suspend fun getCaptchaToken(url: String, key: String, referer: String? = null): String? {
        try {
            val uri = URI.create(url)
            val domain = base64Encode(
                (uri.scheme + "://" + uri.host + ":443").encodeToByteArray(),
            ).replace("\n", "").replace("=", ".")

            val vToken =
                app.get(
                    "https://www.google.com/recaptcha/api.js?render=$key",
                    referer = referer,
                    cacheTime = 0
                )
                    .text
                    .substringAfter("releases/")
                    .substringBefore("/")
            val recapToken =
                app.get("https://www.google.com/recaptcha/api2/anchor?ar=1&hl=en&size=invisible&cb=cs3&k=$key&co=$domain&v=$vToken")
                    .document
                    .selectFirst("#recaptcha-token")?.attr("value")
            if (recapToken != null) {
                return app.post(
                    "https://www.google.com/recaptcha/api2/reload?k=$key",
                    data = mapOf(
                        "v" to vToken,
                        "k" to key,
                        "c" to recapToken,
                        "co" to domain,
                        "sa" to "",
                        "reason" to "q"
                    ), cacheTime = 0
                ).text
                    .substringAfter("rresp\",\"")
                    .substringBefore("\"")
            }
        } catch (e: Exception) {
            logError(e)
        }
        return null
    }

    private var trackerCache: HashMap<String, AniSearch> = hashMapOf()

    /** backwards compatibility, use getTracker4 instead */
    suspend fun getTracker(
        titles: List<String>,
        types: Set<TrackerType>?,
        year: Int?,
    ): Tracker? = getTracker(titles, types, year, false)

    /**
     * Get anime tracker information based on title, year and type.
     * Both titles are attempted to be matched with both Romaji and English title.
     * Uses the anilist api.
     *
     * @param titles uses first index to search, but if you have multiple titles and want extra guarantee to match you can also have that
     * @param types Optional parameter to narrow down the scope to Movies, TV, etc. See TrackerType.getTypes()
     * @param year Optional parameter to only get anime with a specific year
     **/
    suspend fun getTracker(
        titles: List<String>,
        types: Set<TrackerType>?,
        year: Int?,
        lessAccurate: Boolean
    ): Tracker? {
        return try {
            require(titles.isNotEmpty()) { "titles must no be empty when calling getTracker" }

            val mainTitle = titles[0]
            val search =
                trackerCache[mainTitle]
                    ?: searchAnilist(mainTitle)?.also {
                        trackerCache[mainTitle] = it
                    } ?: return null

            val res = search.data?.page?.media?.find { media ->
                val matchingYears = year == null || media.seasonYear == year
                val matchingTitles = media.title?.let { title ->
                    titles.any { userTitle ->
                        title.isMatchingTitles(userTitle)
                    }
                } ?: false

                val matchingTypes = types?.any { it.name.equals(media.format, true) } == true
                if (lessAccurate) matchingTitles || matchingTypes && matchingYears else matchingTitles && matchingTypes && matchingYears
            } ?: return null

            Tracker(
                res.idMal,
                res.id.toString(),
                res.coverImage?.extraLarge ?: res.coverImage?.large,
                res.bannerImage
            )
        } catch (t: Throwable) {
            logError(t)
            null
        }
    }

    /** Searches Anilist using title.
     * @param title Title string of the show
     * @return [AniSearch] data class holds search info.
     * */
    private suspend fun searchAnilist(
        title: String?,
    ): AniSearch? {
        val query = """
        query (
          ${'$'}page: Int = 1
          ${'$'}search: String
          ${'$'}sort: [MediaSort] = [POPULARITY_DESC, SCORE_DESC]
          ${'$'}type: MediaType
        ) {
          Page(page: ${'$'}page, perPage: 20) {
            media(
              search: ${'$'}search
              sort: ${'$'}sort
              type: ${'$'}type
            ) {
              id
              idMal
              title { romaji english }
              coverImage { extraLarge large }
              bannerImage
              seasonYear
              format
            }
          }
        }
    """.trimIndent().trim()

        val data = mapOf(
            "query" to query,
            "variables" to mapOf(
                "search" to title,
                "sort" to "SEARCH_MATCH",
                "type" to "ANIME",
            )
        ).toJson().toRequestBody(RequestBodyTypes.JSON.toMediaTypeOrNull())

        return app.post("https://graphql.anilist.co", requestBody = data)
            .parsedSafe()
    }
}

/*
// THIS IS WORK IN PROGRESS API
interface ITag {
    val name: UiText
}

data class SimpleTag(override val name: UiText, val data: String) : ITag

enum class SelectType {
    SingleSelect,
    MultiSelect,
    MultiSelectAndExclude,
}

enum class SelectValue {
    Selected,
    Excluded,
}

interface GenreSelector {
    val title: UiText
    val id : Int
}

data class TagSelector(
    override val title: UiText,
    override val id : Int,
    val tags: Set<ITag>,
    val defaultTags : Set<ITag> = setOf(),
    val selectType: SelectType = SelectType.SingleSelect,
) : GenreSelector

data class BoolSelector(
    override val title: UiText,
    override val id : Int,

    val defaultValue : Boolean = false,
) : GenreSelector

data class InputField(
    override val title: UiText,
    override val id : Int,

    val hint : UiText? = null,
) : GenreSelector

// This response describes how a user might filter the homepage or search results
data class GenreResponse(
    val searchSelectors : List<GenreSelector>,
    val filterSelectors: List<GenreSelector> = searchSelectors
) */

/*
0 = Site not good
1 = All good
2 = Slow, heavy traffic
3 = restricted, must donate 30 benenes to use
 */
const val PROVIDER_STATUS_KEY = "PROVIDER_STATUS_KEY"
const val PROVIDER_STATUS_BETA_ONLY = 3
const val PROVIDER_STATUS_SLOW = 2
const val PROVIDER_STATUS_OK = 1
const val PROVIDER_STATUS_DOWN = 0

data class ProvidersInfoJson(
    @JsonProperty("name") var name: String,
    @JsonProperty("url") var url: String,
    @JsonProperty("credentials") var credentials: String? = null,
    @JsonProperty("status") var status: Int,
)

data class SettingsJson(
    @JsonProperty("enableAdult") var enableAdult: Boolean = false,
)


data class MainPageData(
    val name: String,
    val data: String,
    val horizontalImages: Boolean = false
)

data class MainPageRequest(
    val name: String,
    val data: String,
    val horizontalImages: Boolean,
    //TODO genre selection or smth
)

/** Create [MainPageData] from url, name Strings & layout (Horizontal/Vertical) Boolean.
 * @param url page Url string.
 * @param name page Name string.
 * @param horizontalImages Boolean to control item card layout.
 * */
fun mainPage(url: String, name: String, horizontalImages: Boolean = false): MainPageData {
    return MainPageData(name = name, data = url, horizontalImages = horizontalImages)
}

/** return list of MainPageData with url to name, make for more readable code
 * @param elements parameter of [MainPageData] class of data*/
fun mainPageOf(vararg elements: MainPageData): List<MainPageData> {
    return elements.toList()
}

/** return list of MainPageData with url to name, make for more readable code
 * @param elements parameter of <String, String> map of url and name */
fun mainPageOf(vararg elements: Pair<String, String>): List<MainPageData> {
    return elements.map { (url, name) -> MainPageData(name = name, data = url) }
}

fun newHomePageResponse(
    name: String,
    list: List<SearchResponse>,
    hasNext: Boolean? = null,
): HomePageResponse {
    @Suppress("DEPRECATION")
    return HomePageResponse(
        listOf(HomePageList(name, list)),
        hasNext = hasNext ?: list.isNotEmpty()
    )
}

fun newHomePageResponse(
    data: MainPageRequest,
    list: List<SearchResponse>,
    hasNext: Boolean? = null,
): HomePageResponse {
    @Suppress("DEPRECATION")
    return HomePageResponse(
        listOf(HomePageList(data.name, list, data.horizontalImages)),
        hasNext = hasNext ?: list.isNotEmpty()
    )
}

fun newHomePageResponse(list: HomePageList, hasNext: Boolean? = null): HomePageResponse {
    @Suppress("DEPRECATION")
    return HomePageResponse(listOf(list), hasNext = hasNext ?: list.list.isNotEmpty())
}

fun newHomePageResponse(list: List<HomePageList>, hasNext: Boolean? = null): HomePageResponse {
    @Suppress("DEPRECATION")
    return HomePageResponse(list, hasNext = hasNext ?: list.any { it.list.isNotEmpty() })
}

fun newSearchResponseList(
    list: List<SearchResponse>,
    hasNext: Boolean? = null,
): SearchResponseList {
    @Suppress("DEPRECATION_ERROR")
    return SearchResponseList(
        list,
        hasNext = hasNext ?: list.isNotEmpty()
    )
}

fun List<SearchResponse>.toNewSearchResponseList(hasNext: Boolean? = null) : SearchResponseList {
    return newSearchResponseList(this, hasNext)
}

/**Every provider will **not** have try catch built in, so handle exceptions when calling these functions*/
abstract class MainAPI {
    companion object {
        var overrideData: HashMap<String, ProvidersInfoJson>? = null
        var settingsForProvider: SettingsJson = SettingsJson()
    }

    fun init() {
        overrideData?.get(this.javaClass.simpleName)?.let { data ->
            overrideWithNewData(data)
        }
    }

    fun overrideWithNewData(data: ProvidersInfoJson) {
        if (!canBeOverridden) return
        this.name = data.name
        if (data.url.isNotBlank() && data.url != "NONE")
            this.mainUrl = data.url
        this.storedCredentials = data.credentials
    }

    /** Name of the plugin that will used in UI */
    open var name = "NONE"

    /** Main Url of the plugin that can be used directly in code or to be replaced using Clone site feature in settings */
    open var mainUrl = "NONE"
    open var storedCredentials: String? = null
    open var canBeOverridden: Boolean = true

    /** if this is turned on then it will request the homepage one after the other,
    used to delay if they block many request at the same time*/
    open var sequentialMainPage: Boolean = false

    /** in milliseconds, this can be used to add more delay between homepage requests
     *  on first load if sequentialMainPage is turned on */
    open var sequentialMainPageDelay: Long = 0L

    /** in milliseconds, this can be used to add more delay between homepage requests when scrolling */
    open var sequentialMainPageScrollDelay: Long = 0L

    /** used to keep track when last homepage request was in unixtime ms */
    var lastHomepageRequest: Long = 0L

    /**
     * The language as an IETF BCP 47 conformant tag.
     * Check [com.lagradost.cloudstream3.utils.SubtitleHelper].
     *
     * See locales on:
     * https://github.com/unicode-org/cldr-json/blob/main/cldr-json/cldr-core/availableLocales.json
     * https://www.iana.org/assignments/language-subtag-registry/language-subtag-registry
     * https://android.googlesource.com/platform/frameworks/base/+/android-16.0.0_r2/core/res/res/values/locale_config.xml
     * https://iso639-3.sil.org/code_tables/639/data/all
    */
    open var lang = "en"

    /**If link is stored in the "data" string, so links can be instantly loaded*/
    open val instantLinkLoading = false

    /**Set false if links require referer or for some reason cant be played on a chromecast*/
    open val hasChromecastSupport = true

    /**If all links are encrypted then set this to false*/
    open val hasDownloadSupport = true

    /**Used for testing and can be used to disable the providers if WebView is not available*/
    open val usesWebView = false

    /** Determines which plugin a given provider is from. This is the full path to the plugin. */
    var sourcePlugin: String? = null

    open val hasMainPage = false
    open val hasQuickSearch = false

    /**
     * The timeout on the `loadLinks` functions in milliseconds,
     * By default this should be around a few minutes to prevent any unexpected recursive call/extraction to drain resources,
     * however if you need very long extraction times, you can can request it by changing this variable.
     *
     * Note that this is only a hint, and may not get respected if you request something too long.
     * */
    open val loadLinksTimeoutMs: Long? = null

    /**
     * The timeout on the `getMainPage` functions in milliseconds.
     *
     * Note that this is only a hint, and may not get respected if you request something too long.
     * */
    open val getMainPageTimeoutMs: Long? = null

    /**
     * The timeout on the `search` functions in milliseconds.
     *
     * Note that this is only a hint, and may not get respected if you request something too long.
     * */
    open val searchTimeoutMs: Long? = null

    /**
     * The timeout on the `quickSearch` functions in milliseconds.
     *
     * Note that this is only a hint, and may not get respected if you request something too long.
     * */
    open val quickSearchTimeoutMs: Long? = null

    /**
     * The timeout on the `loadSearch` functions in milliseconds.
     *
     * Note that this is only a hint, and may not get respected if you request something too long.
     * */
    open val loadTimeoutMs: Long? = null


    /**
     * A set of which ids the provider can open with getLoadUrl()
     * If the set contains SyncIdName.Imdb then getLoadUrl() can be started with
     * an Imdb class which inherits from SyncId.
     *
     * getLoadUrl() is then used to get page url based on that ID.
     *
     * Example:
     * "tt6723592" -> getLoadUrl(ImdbSyncId("tt6723592")) -> "mainUrl/imdb/tt6723592" -> load("mainUrl/imdb/tt6723592")
     *
     * This is used to launch pages from personal lists or recommendations using IDs.
     **/
    open val supportedSyncNames = setOf<SyncIdName>()

    open val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Cartoon,
        TvType.Anime,
        TvType.OVA,
    )

    open val vpnStatus = VPNStatus.None
    open val providerType = ProviderType.DirectProvider

    //emptyList<MainPageData>() //
    open val mainPage = listOf(MainPageData("", "", false))

    // @WorkerThread
    open suspend fun getMainPage(
        page: Int,
        request: MainPageRequest,
    ): HomePageResponse? {
        throw NotImplementedError()
    }

    /** Paginated search, starts with page: 1 */
    open suspend fun search(query: String, page: Int): SearchResponseList? {
        val searchResults = search(query) ?: return null

        return newSearchResponseList(
            searchResults,
            false
        )
    }

    // @WorkerThread
    open suspend fun search(query: String): List<SearchResponse>? {
        throw NotImplementedError()
    }

    // @WorkerThread
    open suspend fun quickSearch(query: String): List<SearchResponse>? {
        throw NotImplementedError()
    }

    // @WorkerThread
    /**
     * Based on data from search() or getMainPage() it generates a LoadResponse,
     * basically opening the info page from a link.
     * */
    open suspend fun load(url: String): LoadResponse? {
        throw NotImplementedError()
    }

    /**
     * Largely redundant feature for most providers.
     *
     * This job runs in the background when a link is playing in exoplayer.
     * First implemented to do polling for sflix to keep the link from getting expired.
     *
     * This function might be updated to include exoplayer timestamps etc in the future
     * if the need arises.
     * */
    // @WorkerThread
    open suspend fun extractorVerifierJob(extractorData: String?) {
        throw NotImplementedError()
    }

    /**Callback is fired once a link is found, will return true if method is executed successfully
     * @param data dataUrl string returned from [load] function.
     * @see newMovieLoadResponse
     * @see newTvSeriesLoadResponse
     * @see newLiveStreamLoadResponse
     * @see newAnimeLoadResponse
     * @see newTorrentLoadResponse
     * */
    // @WorkerThread
    open suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        throw NotImplementedError()
    }

    /** An okhttp interceptor for used in OkHttpDataSource */
    open fun getVideoInterceptor(extractorLink: ExtractorLink): Interceptor? {
        return null
    }

    /**
     * Get the load() url based on a sync ID like IMDb or MAL.
     * Only contains SyncIds based on supportedSyncUrls.
     **/
    open suspend fun getLoadUrl(name: SyncIdName, id: String): String? {
        return null
    }
}

/** Might need a different implementation for desktop*/
fun base64Decode(string: String): String {
    return String(base64DecodeArray(string), Charsets.ISO_8859_1)
}

@OptIn(ExperimentalEncodingApi::class)
fun base64DecodeArray(string: String): ByteArray {
    return Base64.decode(string)
}

@OptIn(ExperimentalEncodingApi::class)
fun base64Encode(array: ByteArray): String {
    return Base64.encode(array)
}

fun MainAPI.fixUrlNull(url: String?): String? {
    if (url.isNullOrEmpty()) {
        return null
    }
    return fixUrl(url)
}

fun MainAPI.fixUrl(url: String): String {
    if (url.startsWith("http") ||
        // Do not fix JSON objects and arrays when passed as urls.
        url.startsWith("{\"") || url.startsWith("[")
    ) {
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

/** Sort the urls based on quality
 * @param urls Set of [ExtractorLink]
 * */
fun sortUrls(urls: Set<ExtractorLink>): List<ExtractorLink> {
    return urls.sortedBy { t -> -t.quality }
}

/** Capitalize the first letter of string.
 * @param str String to be capitalized
 * @return non-nullable String
 * @see capitalizeStringNullable
 * */
fun capitalizeString(str: String): String {
    return capitalizeStringNullable(str) ?: str
}

/** Capitalize the first letter of string.
 * @param str String to be capitalized
 * @return nullable String
 * @see capitalizeString
 * */
fun capitalizeStringNullable(str: String?): String? {
    if (str == null)
        return null
    return try {
        str.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    } catch (e: Exception) {
        str
    }
}

fun fixTitle(str: String): String {
    return str.split(" ").joinToString(" ") {
        it.lowercase()
            .replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase(Locale.getDefault()) else it }
    }
}

/**
 * Get rhino context in a safe way as it needs to be initialized on the main thread.
 *
 * Make sure you get the scope using: val scope: Scriptable = rhino.initSafeStandardObjects()
 *
 * Use like the following: rhino.evaluateString(scope, js, "JavaScript", 1, null)
 **/
suspend fun getRhinoContext(): org.mozilla.javascript.Context {
    return Coroutines.mainWork {
        val rhino = org.mozilla.javascript.Context.enter()
        rhino.initSafeStandardObjects()
        rhino.setInterpretedMode(true)
        rhino
    }
}

/** https://www.imdb.com/title/tt2861424/ -> tt2861424
 * @param url Imdb Url you need to get the Id from.
 * @return imdb id formatted string
 * @see imdbUrlToIdNullable
 * */
fun imdbUrlToId(url: String): String? {
    return Regex("/title/(tt[0-9]*)").find(url)?.groupValues?.get(1)
        ?: Regex("tt[0-9]{5,}").find(url)?.groupValues?.get(0)
}

/** https://www.imdb.com/title/tt2861424/ -> tt2861424
 * @param url Imdb Url you need to get the Id from.
 * @return imdb id formatted nullable string
 * @see imdbUrlToId
 * */
fun imdbUrlToIdNullable(url: String?): String? {
    if (url == null) return null
    return imdbUrlToId(url)
}

/** enum class determines provider type:
 *
 * MetaProvider: When data is fetched from a 3rd party site like imdb
 *
 * DirectProvider: When all data is from the site
 * */
enum class ProviderType {
    MetaProvider,
    DirectProvider,
}

/** enum class determines VPN status (Non, MightBeNeeded or Torrent) */
enum class VPNStatus {
    None,
    MightBeNeeded,
    Torrent,
}

/** enum class determines Show status (Completed or Ongoing) */
enum class ShowStatus {
    Completed,
    Ongoing,
}

enum class DubStatus(val id: Int) {
    None(-1),
    Dubbed(1),
    Subbed(0),
}

/** This is the primary way to store score/rating. Use Score.from or Score.from10 to parse the score
 * as it does not have a public constructor. Use toInt/toFloat to get back the score.
 *
 * Internally it stores it as an int up to 10^9 to represent up to 10 significant digits. So think
 * of this as a decimal class specifically for ratings.
 * */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
class Score private constructor(
    /** Decimal between [0, 10^9] representing the min score and max score respectively */
    @JsonProperty("data")
    private val data: Int,
) {
    override fun hashCode(): Int = this.data.hashCode()
    override fun equals(other: Any?): Boolean = other is Score && this.data == other.data

    fun toOld(): Int = toInt(10000)

    fun toByte(maxScore: Int): Byte = toLong(maxScore).toByte()

    fun toInt(maxScore: Int = 10): Int = toLong(maxScore).toInt()

    fun toLong(maxScore: Int = 10): Long = (data.toLong() * maxScore.toLong()) / MAX.toLong()

    fun toFloat(maxScore: Int = 10): Float =
        (data.toFloat() / MAX.toFloat()) * maxScore.toFloat()

    fun toDouble(maxScore: Int = 10): Double =
        (data.toDouble() / MAX.toDouble()) * maxScore.toDouble()

    override fun toString(): String = this.toString(10)

    /** Formats the rating to a human readable format (with no rounding)
     *
     * However it may also return null if the score is less than the minimum score,
     * this is to avoid 0.0/10.0 in case of default = 0
     * */
    @Throws(IllegalArgumentException::class)
    fun toStringNull(
        minScore: Double,
        maxScore: Int,
        decimals: Int = 1,
        removeTrailingZeros: Boolean = true,
        decimalChar: Char = '.'
    ): String? {
        if (toDouble() < minScore) return null
        return toString(maxScore, decimals, removeTrailingZeros, decimalChar)
    }

    /** Formats the rating to a human readable format (with no rounding) */
    @Throws(IllegalArgumentException::class)
    fun toString(
        maxScore: Int,
        decimals: Int = 1,
        removeTrailingZeros: Boolean = true,
        decimalChar: Char = '.'
    ): String {
        require(maxScore in 1..1000) {
            "maxScore ∈ [1,1000]"
        }
        require(decimals in 0..MAX_ZEROS) {
            "decimals ∈ [0,$MAX_ZEROS]"
        }
        var number = data.toLong() * maxScore.toLong()
        val chars = CharArray(MAX_ZEROS + 6)

        for (i in chars.indices) {
            chars[i] = (number % 10L).toInt().digitToChar()
            number /= 10L
        }

        var trailingZeros = MAX_ZEROS - decimals
        for (i in (MAX_ZEROS - decimals) until chars.size) {
            if (chars[i] != '0') {
                break
            }
            trailingZeros += 1
        }

        var leadingZeros = 0
        for (i in chars.indices.reversed()) {
            if (chars[i] != '0') {
                break
            }
            leadingZeros += 1
        }

        val stringBuilder = StringBuilder()
        for (i in maxOf(MAX_ZEROS, (chars.size - leadingZeros - 1)) downTo MAX_ZEROS) {
            stringBuilder.append(chars[i])
        }

        val end = if (removeTrailingZeros) {
            maxOf(MAX_ZEROS - decimals, trailingZeros)
        } else {
            MAX_ZEROS - decimals
        }

        if (end <= MAX_ZEROS - 1) {
            stringBuilder.append(decimalChar)
            for (i in MAX_ZEROS - 1 downTo end) {
                stringBuilder.append(chars[i])
            }
        }

        return stringBuilder.toString()
    }

    companion object {
        const val MAX: Int = 1000_000_000
        const val MIN: Int = 0
        const val MAX_ZEROS: Int = 9
        private const val TAG: String = "Score"

        fun fromOld(value: Int?): Score? {
            if (value == null) return null
            if (value < 0 || value > 10000) {
                com.lagradost.api.Log.w(TAG, "old: $value ∉ [0, 10000]")
                return null
            }
            return Score(value * 100_000)
        }

        /** `value ∈ [0, maxScore]` */
        fun from(value: Int?, maxScore: Int): Score? {
            if (value == null) {
                return null
            }
            if (value < 0 || value > maxScore) {
                com.lagradost.api.Log.w(TAG, "fromInt: $value ∉ [0, $maxScore]")
                return null
            }
            return Score((MAX / maxScore) * value)
        }

        /** `value ∈ [0.0, maxScore]` */
        fun from(value: Double?, maxScore: Int): Score? {
            if (value == null) {
                return null
            }
            if (value < 0.0 || value > maxScore) {
                com.lagradost.api.Log.w(TAG, "fromDouble: $value ∉ [0.0, $maxScore]")
                return null
            }
            return Score(((MAX / maxScore).toDouble() * value).roundToInt())
        }

        /** `value ∈ [0.0f, maxScore]` */
        fun from(value: Float?, maxScore: Int): Score? {
            if (value == null) {
                return null
            }
            if (value < 0.0 || value > maxScore) {
                com.lagradost.api.Log.w(TAG, "fromFloat: $value ∉ [0.0f, $maxScore]")
                return null
            }
            return Score(((MAX / maxScore).toFloat() * value).roundToInt())
        }

        /** `value ∈ ["0.0", maxScore]` */
        fun from(value: String?, maxScore: Int): Score? =
            from(value?.trim()?.toDoubleOrNull()?.absoluteValue, maxScore)

        /** `value ∈ [0, 5]` */
        fun from5(value: Int?): Score? = from(value, 5)

        /** `value ∈ [0, 10]` */
        fun from10(value: Int?): Score? = from(value, 10)

        /** `value ∈ [0, 100]` */
        fun from100(value: Int?): Score? = from(value, 100)

        /** `value ∈ [0.0, 5.0]` */
        fun from5(value: Double?): Score? = from(value, 5)

        /** `value ∈ [0.0, 10.0]` */
        fun from10(value: Double?): Score? = from(value, 10)

        /** `value ∈ [0.0, 100.0]` */
        fun from100(value: Double?): Score? = from(value, 100)

        /** `value ∈ [0.0f, 5.0f]` */
        fun from5(value: Float?): Score? = from(value, 5)

        /** `value ∈ [0.0f, 10.0f]` */
        fun from10(value: Float?): Score? = from(value, 10)

        /** `value ∈ [0.0f, 100.0f]` */
        fun from100(value: Float?): Score? = from(value, 100)

        /** `value ∈ ["0.0", "5.0"]` */
        fun from5(value: String?): Score? = from(value, 5)

        /** `value ∈ ["0.0", "10.0"]` */
        fun from10(value: String?): Score? = from(value, 10)

        /** `value ∈ ["0.0", "100.0"]` */
        fun from100(value: String?): Score? = from(value, 100)
    }
}

@Suppress("UNUSED_PARAMETER")
enum class TvType(value: Int?) {
    Movie(1),
    AnimeMovie(2),
    TvSeries(3),
    Cartoon(4),
    Anime(5),
    OVA(6),
    Torrent(7),
    Documentary(8),
    AsianDrama(9),
    Live(10),
    NSFW(11),
    Others(12),
    Music(13),
    AudioBook(14),

    /** Won't load the built in player, make your own interaction */
    CustomMedia(15),

    Audio(16),
    Podcast(17),
}

enum class AutoDownloadMode(val value: Int) {
    Disable(0),
    FilterByLang(1),
    All(2),
    NsfwOnly(3);

    companion object {
        infix fun getEnum(value: Int): AutoDownloadMode? =
            entries.firstOrNull { it.value == value }
    }
}

/** Extension function of [TvType] to check if the type is Movie.
 * @return If the type is AnimeMovie, Live, Movie, Torrent returns true otherwise returns false.
 * */
fun TvType.isMovieType(): Boolean {
    return when (this) {
        TvType.AnimeMovie,
        TvType.Live,
        TvType.Movie,
        TvType.Torrent -> true

        else -> false
    }
}

/** Extension function of [TvType] to check if the type is Audio.
 * @return If the type is Audio, AudioBook, Music, PodCast returns true otherwise returns false.
 * */
fun TvType.isAudioType(): Boolean {
    return when (this) {
        TvType.Audio,
        TvType.AudioBook,
        TvType.Music,
        TvType.Podcast -> true

        else -> false
    }
}

/** Extension function of [TvType] to check if the type is Live stream.
 * @return If the type is Live returns true, otherwise returns false.
 * */
fun TvType.isLiveStream(): Boolean {
    return this == TvType.Live
}

/** Extension function of [TvType] to check if the type has an Anime opening.
 * @return If the type is Anime or OVA returns true otherwise returns false.
 * */
fun TvType.isAnimeOp(): Boolean {
    return this == TvType.Anime || this == TvType.OVA
}

/** Data class for the Subtitle file info.
 * @property lang Subtitle file language.
 * @property url Subtitle file url to download/load the file.
 * @see newSubtitleFile
 * */
@ConsistentCopyVisibility
data class SubtitleFile private constructor(
    var lang: String,
    var url: String,
    var headers: Map<String, String>?
) {
    @Deprecated("Use newSubtitleFile method", level = DeprecationLevel.WARNING)
    constructor(lang: String, url: String) : this(lang = lang, url = url, headers = null)

    /** Language code to properly filter auto select / download subtitles */
    val langTag: String?
        get() = fromCodeToLangTagIETF(lang) ?: fromLanguageToTagIETF(lang, true)

    /** Backwards compatible copy */
    fun copy(
        lang: String = this.lang, url: String = this.url
    ): SubtitleFile = SubtitleFile(lang = lang, url = url, headers = this.headers)
}

// No `MainAPI.` to be able to use this in extractors
suspend fun newSubtitleFile(
    lang: String,
    url: String,
    initializer: suspend SubtitleFile.() -> Unit = { }
): SubtitleFile {
    @Suppress("DEPRECATION")
    val builder = SubtitleFile(
        lang, url
    )
    builder.initializer()

    return builder
}

/** Data class for the Homepage response info.
 * @property items List of [HomePageList] items.
 * @property hasNext if there is a next page or not.
 * */
data class HomePageResponse
@Deprecated("Use newHomePageResponse method", level = DeprecationLevel.WARNING)
constructor(
    val items: List<HomePageList>,
    val hasNext: Boolean = false
)

/** Data class for the Homepage list info.
 * @property name name of the category shows on homepage UI.
 * @property list list of [SearchResponse] items that will be added to the category.
 * @property isHorizontalImages here you can control how the items' cards will be appeared on the UI (Horizontal or Vertical) cards.
 * */
data class HomePageList(
    val name: String,
    var list: List<SearchResponse>,
    val isHorizontalImages: Boolean = false
)

/** Data class for the Search results.
 * @property items list of [SearchResponse] items that will be added to the search row.
 * @property hasNext if there is a next page or not.
 * */
data class SearchResponseList
@Deprecated("Use newSearchResponseList method", level = DeprecationLevel.ERROR)
constructor(
    val items: List<SearchResponse>,
    val hasNext: Boolean = false
)

/** enum class holds search quality.
 *
 * [Movie release types](https://en.wikipedia.org/wiki/Pirated_movie_release_types)**/
@Suppress("UNUSED_PARAMETER")
enum class SearchQuality(value: Int?) {
    Cam(1),
    CamRip(2),
    HdCam(3),
    Telesync(4), // TS
    WorkPrint(5),
    Telecine(6), // TC
    HQ(7),
    HD(8),
    HDR(9), // high dynamic range
    BlueRay(10),
    DVD(11),
    SD(12),
    FourK(13),
    UHD(14),
    SDR(15), // standard dynamic range
    WebRip(16)
}

/** Returns [SearchQuality] from String.
 * @param string String text that will be converted into [SearchQuality].
 * */
fun getQualityFromString(string: String?): SearchQuality? {
    //Add anything to here if you find a site that uses some specific naming convention
    val check = (string ?: return null).trim().lowercase().replace(" ", "")

    return when (check) {
        "cam" -> SearchQuality.Cam
        "camrip" -> SearchQuality.CamRip
        "hdcam" -> SearchQuality.HdCam
        "hdtc" -> SearchQuality.HdCam
        "hdts" -> SearchQuality.HdCam
        "highquality" -> SearchQuality.HQ
        "hq" -> SearchQuality.HQ
        "highdefinition" -> SearchQuality.HD
        "hdrip" -> SearchQuality.HD
        "hd" -> SearchQuality.HD
        "hdtv" -> SearchQuality.HD
        "rip" -> SearchQuality.CamRip
        "telecine" -> SearchQuality.Telecine
        "tc" -> SearchQuality.Telecine
        "telesync" -> SearchQuality.Telesync
        "ts" -> SearchQuality.Telesync
        "dvd" -> SearchQuality.DVD
        "dvdrip" -> SearchQuality.DVD
        "dvdscr" -> SearchQuality.DVD
        "blueray" -> SearchQuality.BlueRay
        "bluray" -> SearchQuality.BlueRay
        "blu" -> SearchQuality.BlueRay
        "fhd" -> SearchQuality.HD
        "br" -> SearchQuality.BlueRay
        "standard" -> SearchQuality.SD
        "sd" -> SearchQuality.SD
        "4k" -> SearchQuality.FourK
        "uhd" -> SearchQuality.UHD // may also be 4k or 8k
        "blue" -> SearchQuality.BlueRay
        "wp" -> SearchQuality.WorkPrint
        "workprint" -> SearchQuality.WorkPrint
        "webrip" -> SearchQuality.WebRip
        "webdl" -> SearchQuality.WebRip
        "web" -> SearchQuality.WebRip
        "hdr" -> SearchQuality.HDR
        "sdr" -> SearchQuality.SDR
        else -> null
    }
}


/**
 * For APIs using the mainUrl in the prefix for `MainAPI::load`,
 * this function replaces the `scheme`, `host` and `port` from an old link to the new mainUrl.
 *
 * https://en.wikipedia.org/wiki/Uniform_Resource_Identifier
 * ```text
 *           userinfo       host      port
 *           ┌──┴───┐ ┌──────┴──────┐ ┌┴─┐
 *   https://john.doe@www.example.com:1234/forum/questions/?tag=networking&order=newest#:~:text=whatever
 *   └─┬─┘   └─────────────┬─────────────┘└───────┬───────┘ └────────────┬────────────┘ └───────┬───────┘
 *   scheme            authority                path                   query                 fragment
 * ```
 */
fun MainAPI.updateUrl(url: String): String {
    try {
        val original = URI(url)
        val updated = URI(mainUrl)

        // URI(String scheme, String userInfo, String host, int port, String path, String query, String fragment)
        return URI(
            updated.scheme,
            original.userInfo,
            updated.host,
            updated.port,
            original.path,
            original.query,
            original.fragment
        ).toString()
    } catch (t: Throwable) {
        logError(t)
        return url
    }
}

/** Abstract interface of SearchResponse. */
interface SearchResponse {
    val name: String
    val url: String
    val apiName: String
    var type: TvType?
    var posterUrl: String?
    var posterHeaders: Map<String, String>?
    var id: Int?
    var quality: SearchQuality?
    var score: Score?
}

fun MainAPI.newTorrentSearchResponse(
    name: String,
    url: String,
    type: TvType = TvType.Torrent,
    fix: Boolean = true,
    initializer: TorrentSearchResponse.() -> Unit = { },
): TorrentSearchResponse {
    @Suppress("DEPRECATION_ERROR")
    val builder = TorrentSearchResponse(
        name = name,
        url = if (fix) fixUrl(url) else url,
        apiName = this.name,
        type = type,
        // The initializer will handle this
        posterUrl = null
    )
    builder.initializer()
    return builder
}

fun MainAPI.newMovieSearchResponse(
    name: String,
    url: String,
    type: TvType = TvType.Movie,
    fix: Boolean = true,
    initializer: MovieSearchResponse.() -> Unit = { },
): MovieSearchResponse {
    @Suppress("DEPRECATION_ERROR")
    val builder = MovieSearchResponse(name, if (fix) fixUrl(url) else url, this.name, type)
    builder.initializer()

    return builder
}

fun MainAPI.newLiveSearchResponse(
    name: String,
    url: String,
    type: TvType = TvType.Live,
    fix: Boolean = true,
    initializer: LiveSearchResponse.() -> Unit = { },
): LiveSearchResponse {
    @Suppress("DEPRECATION_ERROR")
    val builder = LiveSearchResponse(
        name = name,
        url = if (fix) fixUrl(url) else url,
        apiName = this.name,
        type = type
    )
    builder.initializer()
    return builder
}

fun MainAPI.newTvSeriesSearchResponse(
    name: String,
    url: String,
    type: TvType = TvType.TvSeries,
    fix: Boolean = true,
    initializer: TvSeriesSearchResponse.() -> Unit = { },
): TvSeriesSearchResponse {
    @Suppress("DEPRECATION_ERROR")
    val builder = TvSeriesSearchResponse(name, if (fix) fixUrl(url) else url, this.name, type)
    builder.initializer()

    return builder
}

fun MainAPI.newAnimeSearchResponse(
    name: String,
    url: String,
    type: TvType = TvType.Anime,
    fix: Boolean = true,
    initializer: AnimeSearchResponse.() -> Unit = { },
): AnimeSearchResponse {
    @Suppress("DEPRECATION_ERROR")
    val builder = AnimeSearchResponse(name, if (fix) fixUrl(url) else url, this.name, type)
    builder.initializer()

    return builder
}

/** Extension function that adds quality to [SearchResponse]
 * @param quality as string
 * */
fun SearchResponse.addQuality(quality: String) {
    this.quality = getQualityFromString(quality)
}

/** Extension function that adds poster to [SearchResponse]
 * @param url nullable string for poster url
 * @param headers Optional <String, String> map of request headers
 * */
fun SearchResponse.addPoster(url: String?, headers: Map<String, String>? = null) {
    this.posterUrl = url
    this.posterHeaders = headers
}

/** Extension function that adds poster to [LoadResponse]
 * @param url nullable string for poster url
 * @param headers Optional <String, String> map of request headers
 * */
fun LoadResponse.addPoster(url: String?, headers: Map<String, String>? = null) {
    this.posterUrl = url
    this.posterHeaders = headers
}

/** enum class of Actor roles (Main, Supporting, Background).*/
enum class ActorRole {
    Main,
    Supporting,
    Background,
}

/** Data class hold Actor personal information
 * @property name Actor name.
 * @property image Url nullable String to Actor image (Optional).
 * */
data class Actor(
    val name: String,
    val image: String? = null,
)

/** Data class hold Actor information
 * @property actor [Actor] personal info, name & image.
 * @property role [ActorRole] (Optional).
 * @property roleString Actor role as a string (Optional).
 * @property voiceActor Voice [Actor] personal info, can be used in case of Animation for voice actors. (Optional).
 * */
data class ActorData(
    val actor: Actor,
    val role: ActorRole? = null,
    val roleString: String? = null,
    val voiceActor: Actor? = null,
)

/** Data class of [SearchResponse] interface for Anime.
 * @see newAnimeSearchResponse
 * */
data class AnimeSearchResponse
@Deprecated("Use newAnimeSearchResponse", level = DeprecationLevel.ERROR)
constructor(
    override val name: String,
    override val url: String,
    override val apiName: String,
    override var type: TvType? = null,

    override var posterUrl: String? = null,
    var year: Int? = null,
    var dubStatus: EnumSet<DubStatus>? = null,

    var otherName: String? = null,
    var episodes: MutableMap<DubStatus, Int> = mutableMapOf(),

    override var id: Int? = null,
    override var quality: SearchQuality? = null,
    override var posterHeaders: Map<String, String>? = null,
    override var score: Score? = null,
) : SearchResponse {
    @Suppress("DEPRECATION_ERROR")
    @Deprecated(
        "Use newAnimeSearchResponse",
        level = DeprecationLevel.ERROR
    )
    constructor(
        name: String,
        url: String,
        apiName: String,
        type: TvType? = null,

        posterUrl: String? = null,
        year: Int? = null,
        dubStatus: EnumSet<DubStatus>? = null,

        otherName: String? = null,
        episodes: MutableMap<DubStatus, Int> = mutableMapOf(),

        id: Int? = null,
        quality: SearchQuality? = null,
        posterHeaders: Map<String, String>? = null,
    ) : this(
        name,
        url,
        apiName,
        type,
        posterUrl,
        year,
        dubStatus,
        otherName,
        episodes,
        id,
        quality,
        posterHeaders, null
    )
}

fun AnimeSearchResponse.addDubStatus(status: DubStatus, episodes: Int? = null) {
    this.dubStatus = dubStatus?.also { it.add(status) } ?: EnumSet.of(status)
    if (this.type?.isMovieType() != true)
        if (episodes != null && episodes > 0)
            this.episodes[status] = episodes
}

fun AnimeSearchResponse.addDubStatus(isDub: Boolean, episodes: Int? = null) {
    addDubStatus(if (isDub) DubStatus.Dubbed else DubStatus.Subbed, episodes)
}

fun AnimeSearchResponse.addDub(episodes: Int?) {
    if (episodes == null || episodes <= 0) return
    addDubStatus(DubStatus.Dubbed, episodes)
}

fun AnimeSearchResponse.addSub(episodes: Int?) {
    if (episodes == null || episodes <= 0) return
    addDubStatus(DubStatus.Subbed, episodes)
}

fun AnimeSearchResponse.addDubStatus(
    dubExist: Boolean,
    subExist: Boolean,
    dubEpisodes: Int? = null,
    subEpisodes: Int? = null
) {
    if (dubExist)
        addDubStatus(DubStatus.Dubbed, dubEpisodes)

    if (subExist)
        addDubStatus(DubStatus.Subbed, subEpisodes)
}

fun AnimeSearchResponse.addDubStatus(status: String, episodes: Int? = null) {
    if (status.contains("(dub)", ignoreCase = true)) {
        addDubStatus(DubStatus.Dubbed, episodes)
    } else if (status.contains("(sub)", ignoreCase = true)) {
        addDubStatus(DubStatus.Subbed, episodes)
    }
}

/** Data class of [SearchResponse] interface for Torrent.
 * @see newTorrentSearchResponse
 * */
data class TorrentSearchResponse
@Deprecated("Use newTorrentSearchResponse", level = DeprecationLevel.ERROR)
constructor(
    override val name: String,
    override val url: String,
    override val apiName: String,
    override var type: TvType?,

    override var posterUrl: String?,
    override var id: Int? = null,
    override var quality: SearchQuality? = null,
    override var posterHeaders: Map<String, String>? = null,
    override var score: Score? = null,
) : SearchResponse {
    @Suppress("DEPRECATION_ERROR")
    @Deprecated(
        "Use newTorrentSearchResponse",
        level = DeprecationLevel.ERROR
    )
    constructor(
        name: String,
        url: String,
        apiName: String,
        type: TvType?,
        posterUrl: String?,
        id: Int? = null,
        quality: SearchQuality? = null,
        posterHeaders: Map<String, String>? = null
    ) : this(name, url, apiName, type, posterUrl, id, quality, posterHeaders, null)
}

/** Data class of [SearchResponse] interface for Movies.
 * @see newMovieSearchResponse
 * */
data class MovieSearchResponse
@Deprecated("Use newMovieSearchResponse", level = DeprecationLevel.ERROR)
constructor(
    override val name: String,
    override val url: String,
    override val apiName: String,
    override var type: TvType? = null,

    override var posterUrl: String? = null,
    var year: Int? = null,
    override var id: Int? = null,
    override var quality: SearchQuality? = null,
    override var posterHeaders: Map<String, String>? = null,
    override var score: Score? = null,
) : SearchResponse {
    @Suppress("DEPRECATION_ERROR")
    @Deprecated(
        "Use newMovieSearchResponse",
        level = DeprecationLevel.ERROR
    )
    constructor(
        name: String,
        url: String,
        apiName: String,
        type: TvType?,
        posterUrl: String?,
        year: Int? = null,
        id: Int? = null,
        quality: SearchQuality? = null,
        posterHeaders: Map<String, String>? = null
    ) : this(name, url, apiName, type, posterUrl, id, year, quality, posterHeaders, null)
}

/** Data class of [SearchResponse] interface for Live streams.
 * @see newLiveSearchResponse
 * */
data class LiveSearchResponse
@Deprecated("Use newLiveSearchResponse", level = DeprecationLevel.ERROR)
constructor(
    override val name: String,
    override val url: String,
    override val apiName: String,
    override var type: TvType? = null,

    override var posterUrl: String? = null,
    override var id: Int? = null,
    override var quality: SearchQuality? = null,
    override var posterHeaders: Map<String, String>? = null,
    var lang: String? = null,
    override var score: Score? = null,
) : SearchResponse {
    @Suppress("DEPRECATION_ERROR")
    @Deprecated(
        "Use newLiveSearchResponse",
        level = DeprecationLevel.ERROR
    )
    constructor(
        name: String,
        url: String,
        apiName: String,
        type: TvType?,
        posterUrl: String?,
        id: Int? = null,
        quality: SearchQuality? = null,
        posterHeaders: Map<String, String>? = null,
        lang: String? = null,
    ) : this(name, url, apiName, type, posterUrl, id, quality, posterHeaders, lang, null)
}

/** Data class of [SearchResponse] interface for Tv series.
 * @see newTvSeriesSearchResponse
 * */
data class TvSeriesSearchResponse
@Deprecated("Use newTvSeriesSearchResponse", level = DeprecationLevel.ERROR)
constructor(
    override val name: String,
    override val url: String,
    override val apiName: String,
    override var type: TvType? = null,

    override var posterUrl: String? = null,
    var year: Int? = null,
    var episodes: Int? = null,
    override var id: Int? = null,
    override var quality: SearchQuality? = null,
    override var posterHeaders: Map<String, String>? = null,
    override var score: Score? = null,
) : SearchResponse {
    @Suppress("DEPRECATION_ERROR")
    @Deprecated(
        "Use newTvSeriesSearchResponse",
        level = DeprecationLevel.ERROR
    )
    constructor(
        name: String,
        url: String,
        apiName: String,
        type: TvType?,
        posterUrl: String?,
        year: Int? = null,
        episodes: Int? = null,
        id: Int? = null,
        quality: SearchQuality? = null,
        posterHeaders: Map<String, String>? = null,
    ) : this(
        name,
        url,
        apiName,
        type,
        posterUrl,
        year,
        episodes,
        id,
        quality,
        posterHeaders,
        null
    )
}

/** Data class of Trailer data.
 * @property extractorUrl Url string of the Trailer video.
 * @property referer Nullable string of referer to be used in network request.
 * @property raw determines if [extractorUrl] should be used as direct Trailer video link instead of extracting it.
 * */
data class TrailerData(
    val extractorUrl: String,
    val referer: String?,
    val raw: Boolean,
    val headers: Map<String, String> = mapOf(),
    // var mirrors: List<ExtractorLink>,
    // var subtitles: List<SubtitleFile> = emptyList(),
)

/** Abstract interface of LoadResponse responses
 * @property name Title of the media, appears on result page.
 * @property url Url of the media.
 * @property apiName Plugin name, appears on result page.
 * @property type [TvType] of the media .
 * @property posterUrl Url of the media poster, appears on Top of result page.
 * @property year Year of the media, appears on result page.
 * @property plot Plot of the media, appears on result page.
 * @property score Rating of the media, appears on result page.
 * Use it with addScore or by assigning a score like `Score.from(string/float/int/double, 10)` or `Score.from10(string/float/int/double)`
 * @property tags Tags of the media, appears on result page.
 * @property duration duration of the media, appears on result page.
 * @property trailers list of the media [TrailerData], used to load trailers.
 * @property recommendations list of the [SearchResponse] related to media, appears on result page.
 * @property actors list of the [ActorData] casted in the media, appears on result page.
 * @property comingSoon determines if the media is released or coming soon.
 * @property syncData Online sync services compatible with the media.
 * @property posterHeaders headers map used by network request to get the poster.
 * @property backgroundPosterUrl Url of the media background poster.
 * @property contentRating content rating of the media, appears on result page.
 * @property uniqueUrl The key used for storing the persistent data about an entry.
 * On older versions `url` was used instead, but this was added to support JSON that can change as the url parameter.
 *
 * If you have JSON that can change you can set `url = jsonObject.toJson()` and `uniqueId = jsonObject.id.toString()`
 * */
interface LoadResponse {
    var name: String
    var url: String
    var apiName: String
    var type: TvType
    var posterUrl: String?
    var year: Int?
    var plot: String?

    var score: Score?
    var tags: List<String>?
    var duration: Int? // in minutes
    var trailers: MutableList<TrailerData>

    var recommendations: List<SearchResponse>?
    var actors: List<ActorData>?
    var comingSoon: Boolean
    var syncData: MutableMap<String, String>
    var posterHeaders: Map<String, String>?
    var backgroundPosterUrl: String?
    var contentRating: String?

    var uniqueUrl: String

    @Deprecated(
        "`rating` is the old scoring system, use score instead",
        replaceWith = ReplaceWith("score")
    )
    var rating: Int?
        set(value) {
            this.score = Score.fromOld(value)
        }
        get() = score?.toOld()

    companion object {
        var malIdPrefix = "" //malApi.idPrefix
        var aniListIdPrefix = "" //aniListApi.idPrefix
        var simklIdPrefix = "" //simklApi.idPrefix
        var isTrailersEnabled = true

        /**
         * The ID string is a way to keep a collection of services in one single ID using a map
         * This adds a database service (like imdb) to the string and returns the new string.
         */
        fun addIdToString(idString: String?, database: SimklSyncServices, id: String?): String? {
            if (id == null) return idString
            return (readIdFromString(idString) + mapOf(database to id)).toJson()
        }

        /** Read the id string to get all other ids */
        fun readIdFromString(idString: String?): Map<SimklSyncServices, String> {
            return tryParseJson(idString) ?: return emptyMap()
        }

        fun LoadResponse.isMovie(): Boolean {
            return this.type.isMovieType() || this is MovieLoadResponse
        }

        @JvmName("addActorNames")
        fun LoadResponse.addActors(actors: List<String>?) {
            this.actors = actors?.map { ActorData(Actor(it)) }
        }

        @JvmName("addActors")
        fun LoadResponse.addActors(actors: List<Pair<Actor, String?>>?) {
            this.actors = actors?.map { (actor, role) -> ActorData(actor, roleString = role) }
        }

        @JvmName("addActorsRole")
        fun LoadResponse.addActors(actors: List<Pair<Actor, ActorRole?>>?) {
            this.actors = actors?.map { (actor, role) -> ActorData(actor, role = role) }
        }

        /**
         * Internal helper function to add simkl ids from other databases.
         */
        private fun LoadResponse.addSimklId(
            database: SimklSyncServices,
            id: String?
        ) {
            safe {
                this.syncData[simklIdPrefix] =
                    addIdToString(this.syncData[simklIdPrefix], database, id.toString())
                        ?: return@safe
            }
        }

        @JvmName("addActorsOnly")
        fun LoadResponse.addActors(actors: List<Actor>?) {
            this.actors = actors?.map { actor -> ActorData(actor) }
        }

        fun LoadResponse.getMalId(): String? {
            return this.syncData[malIdPrefix]
        }

        fun LoadResponse.getAniListId(): String? {
            return this.syncData[aniListIdPrefix]
        }

        fun LoadResponse.getImdbId(): String? {
            return safe {
                readIdFromString(this.syncData[simklIdPrefix])[SimklSyncServices.Imdb]
            }
        }

        fun LoadResponse.getTMDbId(): String? {
            return safe {
                readIdFromString(this.syncData[simklIdPrefix])[SimklSyncServices.Tmdb]
            }
        }

        fun LoadResponse.addMalId(id: Int?) {
            this.syncData[malIdPrefix] = (id ?: return).toString()
            this.addSimklId(SimklSyncServices.Mal, id.toString())
        }

        fun LoadResponse.addAniListId(id: Int?) {
            this.syncData[aniListIdPrefix] = (id ?: return).toString()
            this.addSimklId(SimklSyncServices.AniList, id.toString())
        }

        fun LoadResponse.addSimklId(id: Int?) {
            this.addSimklId(SimklSyncServices.Simkl, id.toString())
        }

        fun LoadResponse.addImdbUrl(url: String?) {
            addImdbId(imdbUrlToIdNullable(url))
        }

        /**better to call addTrailer with multiple trailers directly instead of calling this multiple times*/
        @Suppress("RedundantSuspendModifier")
        suspend fun LoadResponse.addTrailer(
            trailerUrl: String?,
            referer: String? = null,
            addRaw: Boolean = false
        ) {
            if (!isTrailersEnabled || trailerUrl.isNullOrBlank()) return
            this.trailers.add(TrailerData(trailerUrl, referer, addRaw))
            /*val links = arrayListOf<ExtractorLink>()
            val subs = arrayListOf<SubtitleFile>()
            if (!loadExtractor(
                    trailerUrl,
                    referer,
                    { subs.add(it) },
                    { links.add(it) }) && addRaw
            ) {
                this.trailers.add(
                    TrailerData(
                        listOf(
                            ExtractorLink(
                                "",
                                "Trailer",
                                trailerUrl,
                                referer ?: "",
                                Qualities.Unknown.value,
                                trailerUrl.contains(".m3u8")
                            )
                        ), listOf()
                    )
                )
            } else {
                this.trailers.add(TrailerData(links, subs))
            }*/
        }

        /*
        fun LoadResponse.addTrailer(newTrailers: List<ExtractorLink>) {
            trailers.addAll(newTrailers.map { TrailerData(listOf(it)) })
        }*/

        @Suppress("RedundantSuspendModifier")
        suspend fun LoadResponse.addTrailer(
            trailerUrl: String?,
            referer: String? = null,
            addRaw: Boolean = false,
            headers: Map<String, String> = mapOf()
        ) {
            if (!isTrailersEnabled || trailerUrl.isNullOrBlank()) return
            this.trailers.add(TrailerData(trailerUrl, referer, addRaw, headers))
        }

        @Suppress("RedundantSuspendModifier")
        suspend fun LoadResponse.addTrailer(
            trailerUrls: List<String>?,
            referer: String? = null,
            addRaw: Boolean = false
        ) {
            if (!isTrailersEnabled || trailerUrls == null) return
            trailers.addAll(trailerUrls.map { TrailerData(it, referer, addRaw) })
            /*val trailers = trailerUrls.filter { it.isNotBlank() }.amap { trailerUrl ->
                val links = arrayListOf<ExtractorLink>()
                val subs = arrayListOf<SubtitleFile>()
                if (!loadExtractor(
                        trailerUrl,
                        referer,
                        { subs.add(it) },
                        { links.add(it) }) && addRaw
                ) {
                    arrayListOf(
                        ExtractorLink(
                            "",
                            "Trailer",
                            trailerUrl,
                            referer ?: "",
                            Qualities.Unknown.value,
                            trailerUrl.contains(".m3u8")
                        )
                    ) to arrayListOf()
                } else {
                    links to subs
                }
            }.map { (links, subs) -> TrailerData(links, subs) }
            this.trailers.addAll(trailers)*/
        }

        fun LoadResponse.addImdbId(id: String?) {
            // TODO add IMDb sync
            this.addSimklId(SimklSyncServices.Imdb, id)
        }

        @Deprecated("Outdated API due to misspelling", ReplaceWith("addTraktId(id)"))
        fun LoadResponse.addTrackId(id: String?) {
            this.addTraktId(id)
        }

        @Deprecated("Outdated API due to missing capitalization", ReplaceWith("addKitsuId(id)"))
        fun LoadResponse.addkitsuId(id: String?) {
            this.addKitsuId(id)
        }

        @Suppress("UNUSED_PARAMETER")
        fun LoadResponse.addTraktId(id: String?) {
            // TODO add Trakt sync
        }

        @Suppress("UNUSED_PARAMETER")
        fun LoadResponse.addKitsuId(id: String?) {
            // TODO add Kitsu sync
        }

        fun LoadResponse.addTMDbId(id: String?) {
            // TODO add TMDb sync
            this.addSimklId(SimklSyncServices.Tmdb, id)
        }

        fun LoadResponse.addScore(score: String?, maxValue: Int = 10) {
            this.score = Score.from(score, maxValue)
        }

        fun LoadResponse.addScore(score: Score?) {
            this.score = score
        }

        fun LoadResponse.addRating(text: String?) {
            this.score = Score.from10(text)
        }

        @Deprecated("Use addScore", replaceWith = ReplaceWith("addScore"))
        fun LoadResponse.addRating(value: Int?) {
            this.score = Score.fromOld(value)
        }

        fun LoadResponse.addDuration(input: String?) {
            this.duration = getDurationFromString(input) ?: this.duration
        }
    }
}

fun getDurationFromString(input: String?): Int? {
    val cleanInput = input?.trim()?.replace(" ", "") ?: return null
    //Use first as sometimes the text passes on the 2 other Regex, but failed to provide accurate return value
    Regex("(\\d+\\shr)|(\\d+\\shour)|(\\d+\\smin)|(\\d+\\ssec)").findAll(input).let { values ->
        var seconds = 0
        values.forEach {
            val timeText = it.value
            if (timeText.isNotBlank()) {
                val time = timeText.filter { s -> s.isDigit() }.trim().toInt()
                val scale = timeText.filter { s -> !s.isDigit() }.trim()
                //println("Scale: $scale")
                val timeval = when (scale) {
                    "hr", "hour" -> time * 60 * 60
                    "min" -> time * 60
                    "sec" -> time
                    else -> 0
                }
                seconds += timeval
            }
        }
        if (seconds > 0) {
            return seconds / 60
        }
    }
    Regex("([0-9]*)h.*?([0-9]*)m").find(cleanInput)?.groupValues?.let { values ->
        if (values.size == 3) {
            val hours = values[1].toIntOrNull()
            val minutes = values[2].toIntOrNull()
            if (minutes != null && hours != null) {
                return hours * 60 + minutes
            }
        }
    }
    Regex("([0-9]*)m").find(cleanInput)?.groupValues?.let { values ->
        if (values.size == 2) {
            val returnValue = values[1].toIntOrNull()
            if (returnValue != null) {
                return returnValue
            }
        }
    }
    return null
}

/** Extension function of [LoadResponse] to check if it's Episode based.
 * @return True if the response is [EpisodeResponse] and its type is Episode based.
 * */
fun LoadResponse?.isEpisodeBased(): Boolean {
    if (this == null) return false
    return this is EpisodeResponse && this.type.isEpisodeBased()
}

/** Extension function of [LoadResponse] to check if it's Anime based.
 * @return True if the response type is Anime or OVA.
 * @see TvType
 * */
fun LoadResponse?.isAnimeBased(): Boolean {
    if (this == null) return false
    return (this.type == TvType.Anime || this.type == TvType.OVA) // && (this is AnimeLoadResponse)
}

/**
 * Extension function to determine if the [TvType] is episode-based.
 * This function checks if the type corresponds to an episode-based format. Episode-based types will be placed
 * in subfolders that include the sanitized title name. This check is used for other logic as well.
 *
 * @return true if the [TvType] is episode-based, otherwise false.
 */
fun TvType?.isEpisodeBased(): Boolean {
    return when (this) {
        TvType.Anime,
        TvType.AsianDrama,
        TvType.Cartoon,
        TvType.TvSeries -> true

        else -> false
    }
}

/**
 * Extension function to get the folder prefix based on the [TvType].
 * Non-episode-based types will return a base folder name, while episode-based types will
 * have their files placed in subfolders using a sanitized title name.
 *
 * For the actual folder path, refer to `ResultViewModel2().getFolder()`, which combines
 * the folder prefix and, if necessary, the sanitized name to a sub-folder. The folder prefix
 * will be used in the root directory of the configured downloads directory.
 *
 * @return the folder prefix corresponding to the [TvType], which is used as the root directory.
 */
fun TvType.getFolderPrefix(): String {
    return when (this) {
        TvType.Anime -> "Anime"
        TvType.AnimeMovie -> "Movies"
        TvType.AsianDrama -> "AsianDramas"
        TvType.Audio -> "Audio"
        TvType.AudioBook -> "AudioBooks"
        TvType.Cartoon -> "Cartoons"
        TvType.CustomMedia -> "Media"
        TvType.Documentary -> "Documentaries"
        TvType.Live -> "LiveStreams"
        TvType.Movie -> "Movies"
        TvType.Music -> "Music"
        TvType.NSFW -> "NSFW"
        TvType.OVA -> "OVAs"
        TvType.Others -> "Others"
        TvType.Podcast -> "Podcasts"
        TvType.Torrent -> "Torrents"
        TvType.TvSeries -> "TVSeries"
    }
}

/** Data class holds next airing Episode info.
 * @param episode Next airing Episode number.
 * @param unixTime Next airing Time in Unix time format.
 * @param season Season number of next airing episode (Optional).
 * */
data class NextAiring(
    val episode: Int,
    val unixTime: Long,
    val season: Int? = null,
) {
    /**
     * Secondary constructor for backwards compatibility without season.
     *  TODO Remove this constructor after there is a new stable release and extensions are updated to support season.
     */
    constructor(
        episode: Int,
        unixTime: Long,
    ) : this(
        episode,
        unixTime,
        null
    )
}

/** Data class holds season info.
 * @param season To be mapped with episode season, not shown in UI if displaySeason is defined
 * @param name To be shown next to the season like "Season $displaySeason $name" but if displaySeason is null then "$name"
 * @param displaySeason What to be displayed next to the season name, if null then the name is the only thing shown.
 * */
data class SeasonData(
    val season: Int,
    val name: String? = null,
    val displaySeason: Int? = null, // will use season if null
)

/** Abstract interface of EpisodeResponse */
interface EpisodeResponse {
    var showStatus: ShowStatus?
    var nextAiring: NextAiring?
    var seasonNames: List<SeasonData>?
    fun getLatestEpisodes(): Map<DubStatus, Int?>

    /** Count all episodes in all previous seasons up until this episode to get a total count.
     * Example:
     *      Season 1: 10 episodes.
     *      Season 2: 6 episodes.
     *
     * getTotalEpisodeIndex(episode = 3, season = 2) -> 10 + 3 = 13
     * */
    fun getTotalEpisodeIndex(episode: Int, season: Int): Int
}

@JvmName("addSeasonNamesString")
fun EpisodeResponse.addSeasonNames(names: List<String>) {
    this.seasonNames = if (names.isEmpty()) null else names.mapIndexed { index, s ->
        SeasonData(
            season = index + 1,
            s
        )
    }
}

@JvmName("addSeasonNamesSeasonData")
fun EpisodeResponse.addSeasonNames(names: List<SeasonData>) {
    this.seasonNames = names.ifEmpty { null }
}

/** Data class of [LoadResponse] interface for Torrent.
 * @see newTorrentLoadResponse
 */
data class TorrentLoadResponse
@Deprecated("Use newTorrentLoadResponse method", level = DeprecationLevel.ERROR)
constructor(
    override var name: String,
    override var url: String,
    override var apiName: String,
    var magnet: String?,
    var torrent: String?,
    override var plot: String?,
    override var type: TvType = TvType.Torrent,
    override var posterUrl: String? = null,
    override var year: Int? = null,
    override var score: Score? = null,
    override var tags: List<String>? = null,
    override var duration: Int? = null,
    override var trailers: MutableList<TrailerData> = mutableListOf(),
    override var recommendations: List<SearchResponse>? = null,
    override var actors: List<ActorData>? = null,
    override var comingSoon: Boolean = false,
    override var syncData: MutableMap<String, String> = mutableMapOf(),
    override var posterHeaders: Map<String, String>? = null,
    override var backgroundPosterUrl: String? = null,
    override var contentRating: String? = null,
    override var uniqueUrl: String = url
) : LoadResponse {
    /**
     * Secondary constructor for backwards compatibility without contentRating.
     * Remove this constructor after there is a new stable release and extensions are updated to support contentRating.
     */
    @Suppress("DEPRECATION_ERROR")
    @Deprecated(
        "Use newTorrentLoadResponse method with contentRating included",
        level = DeprecationLevel.ERROR
    )
    constructor(
        name: String,
        url: String,
        apiName: String,
        magnet: String?,
        torrent: String?,
        plot: String?,
        type: TvType = TvType.Torrent,
        posterUrl: String? = null,
        year: Int? = null,
        rating: Int? = null,
        tags: List<String>? = null,
        duration: Int? = null,
        trailers: MutableList<TrailerData> = mutableListOf(),
        recommendations: List<SearchResponse>? = null,
        actors: List<ActorData>? = null,
        comingSoon: Boolean = false,
        syncData: MutableMap<String, String> = mutableMapOf(),
        posterHeaders: Map<String, String>? = null,
        backgroundPosterUrl: String? = null,
    ) : this(
        name,
        url,
        apiName,
        magnet,
        torrent,
        plot,
        type,
        posterUrl,
        year,
        Score.fromOld(rating),
        tags,
        duration,
        trailers,
        recommendations,
        actors,
        comingSoon,
        syncData,
        posterHeaders,
        backgroundPosterUrl,
        null
    )
}

suspend fun MainAPI.newTorrentLoadResponse(
    name: String,
    url: String,
    magnet: String? = null,
    torrent: String? = null,
    initializer: suspend TorrentLoadResponse.() -> Unit = { }
): TorrentLoadResponse {
    @Suppress("DEPRECATION_ERROR")
    val builder = TorrentLoadResponse(
        name = name,
        url = url,
        apiName = this.name,
        magnet = magnet,
        torrent = torrent,
        // The initializer will handle this
        plot = null,
        comingSoon = magnet.isNullOrBlank() && torrent.isNullOrBlank()
    )
    builder.initializer()
    return builder
}

/** Data class of [LoadResponse] interface for Anime.
 * @see newAnimeLoadResponse
 * */
data class AnimeLoadResponse
@Deprecated("Use newAnimeLoadResponse method", level = DeprecationLevel.ERROR)
constructor(
    var engName: String? = null,
    var japName: String? = null,
    override var name: String,
    override var url: String,
    override var apiName: String,
    override var type: TvType,

    override var posterUrl: String? = null,
    override var year: Int? = null,

    var episodes: MutableMap<DubStatus, List<Episode>> = mutableMapOf(),
    override var showStatus: ShowStatus? = null,

    override var plot: String? = null,
    override var tags: List<String>? = null,
    var synonyms: List<String>? = null,

    override var score: Score? = null,
    override var duration: Int? = null,
    override var trailers: MutableList<TrailerData> = mutableListOf(),
    override var recommendations: List<SearchResponse>? = null,
    override var actors: List<ActorData>? = null,
    override var comingSoon: Boolean = false,
    override var syncData: MutableMap<String, String> = mutableMapOf(),
    override var posterHeaders: Map<String, String>? = null,
    override var nextAiring: NextAiring? = null,
    override var seasonNames: List<SeasonData>? = null,
    override var backgroundPosterUrl: String? = null,
    override var contentRating: String? = null,
    override var uniqueUrl: String = url
) : LoadResponse, EpisodeResponse {

    override fun getLatestEpisodes(): Map<DubStatus, Int?> {
        return episodes.map { (status, episodes) ->
            val maxSeason = episodes.maxOfOrNull { it.season ?: Int.MIN_VALUE }
                .takeUnless { it == Int.MIN_VALUE }
            status to episodes
                .filter { it.season == maxSeason }
                .maxOfOrNull { it.episode ?: Int.MIN_VALUE }
                .takeUnless { it == Int.MIN_VALUE }
        }.toMap()
    }

    override fun getTotalEpisodeIndex(episode: Int, season: Int): Int {
        val displayMap = this.seasonNames?.associate { it.season to it.displaySeason } ?: emptyMap()

        return this.episodes.maxOf { (_, episodes) ->
            episodes.count { episodeData ->
                // Prioritize display season as actual season may be something random to fit multiple seasons into one.
                val episodeSeason =
                    displayMap[episodeData.season] ?: episodeData.season ?: Int.MIN_VALUE
                // Count all episodes from season 1 to below the current season.
                episodeSeason in 1..<season
            }
        } + episode
    }

    /**
     * Secondary constructor for backwards compatibility without contentRating.
     * Remove this constructor after there is a new stable release and extensions are updated to support contentRating.
     */
    @Suppress("DEPRECATION_ERROR")
    @Deprecated(
        "Use newAnimeLoadResponse method with contentRating included",
        level = DeprecationLevel.ERROR
    )
    constructor(
        engName: String? = null,
        japName: String? = null,
        name: String,
        url: String,
        apiName: String,
        type: TvType,
        posterUrl: String? = null,
        year: Int? = null,
        episodes: MutableMap<DubStatus, List<Episode>> = mutableMapOf(),
        showStatus: ShowStatus? = null,
        plot: String? = null,
        tags: List<String>? = null,
        synonyms: List<String>? = null,
        rating: Int? = null,
        duration: Int? = null,
        trailers: MutableList<TrailerData> = mutableListOf(),
        recommendations: List<SearchResponse>? = null,
        actors: List<ActorData>? = null,
        comingSoon: Boolean = false,
        syncData: MutableMap<String, String> = mutableMapOf(),
        posterHeaders: Map<String, String>? = null,
        nextAiring: NextAiring? = null,
        seasonNames: List<SeasonData>? = null,
        backgroundPosterUrl: String? = null,
    ) : this(
        engName,
        japName,
        name,
        url,
        apiName,
        type,
        posterUrl,
        year,
        episodes,
        showStatus,
        plot,
        tags,
        synonyms,
        Score.fromOld(rating),
        duration,
        trailers,
        recommendations,
        actors,
        comingSoon,
        syncData,
        posterHeaders,
        nextAiring,
        seasonNames,
        backgroundPosterUrl,
        null
    )
}

/**
 * If episodes already exist appends the list.
 * */
fun AnimeLoadResponse.addEpisodes(status: DubStatus, episodes: List<Episode>?) {
    if (episodes.isNullOrEmpty()) return
    this.episodes[status] = (this.episodes[status] ?: emptyList()) + episodes
}

suspend fun MainAPI.newAnimeLoadResponse(
    name: String,
    url: String,
    type: TvType,
    comingSoonIfNone: Boolean = true,
    initializer: suspend AnimeLoadResponse.() -> Unit = { },
): AnimeLoadResponse {
    @Suppress("DEPRECATION_ERROR")
    val builder = AnimeLoadResponse(name = name, url = url, apiName = this.name, type = type)
    builder.initializer()
    if (comingSoonIfNone) {
        builder.comingSoon = true
        for (key in builder.episodes.keys)
            if (!builder.episodes[key].isNullOrEmpty()) {
                builder.comingSoon = false
                break
            }
    }
    return builder
}

/** Data class of [LoadResponse] interface for Live streams.
 * @see newLiveStreamLoadResponse
 * */
data class LiveStreamLoadResponse
@Deprecated("Use newLiveStreamLoadResponse method", level = DeprecationLevel.ERROR)
constructor(
    override var name: String,
    override var url: String,
    override var apiName: String,
    var dataUrl: String,

    override var posterUrl: String? = null,
    override var year: Int? = null,
    override var plot: String? = null,

    override var type: TvType = TvType.Live,
    override var score: Score? = null,
    override var tags: List<String>? = null,
    override var duration: Int? = null,
    override var trailers: MutableList<TrailerData> = mutableListOf(),
    override var recommendations: List<SearchResponse>? = null,
    override var actors: List<ActorData>? = null,
    override var comingSoon: Boolean = false,
    override var syncData: MutableMap<String, String> = mutableMapOf(),
    override var posterHeaders: Map<String, String>? = null,
    override var backgroundPosterUrl: String? = null,
    override var contentRating: String? = null,
    override var uniqueUrl: String = url
) : LoadResponse {
    /**
     * Secondary constructor for backwards compatibility without contentRating.
     * Remove this constructor after there is a new stable release and extensions are updated to support contentRating.
     */
    @Suppress("DEPRECATION_ERROR")
    @Deprecated(
        "Use newLiveStreamLoadResponse method with contentRating included",
        level = DeprecationLevel.ERROR
    )
    constructor(
        name: String,
        url: String,
        apiName: String,
        dataUrl: String,
        posterUrl: String? = null,
        year: Int? = null,
        plot: String? = null,
        type: TvType = TvType.Live,
        rating: Int? = null,
        tags: List<String>? = null,
        duration: Int? = null,
        trailers: MutableList<TrailerData> = mutableListOf(),
        recommendations: List<SearchResponse>? = null,
        actors: List<ActorData>? = null,
        comingSoon: Boolean = false,
        syncData: MutableMap<String, String> = mutableMapOf(),
        posterHeaders: Map<String, String>? = null,
        backgroundPosterUrl: String? = null,
    ) : this(
        name,
        url,
        apiName,
        dataUrl,
        posterUrl,
        year,
        plot,
        type,
        Score.fromOld(rating),
        tags,
        duration,
        trailers,
        recommendations,
        actors,
        comingSoon,
        syncData,
        posterHeaders,
        backgroundPosterUrl,
        null
    )
}

suspend fun MainAPI.newLiveStreamLoadResponse(
    name: String,
    url: String,
    dataUrl: String,
    initializer: suspend LiveStreamLoadResponse.() -> Unit = { }
): LiveStreamLoadResponse {
    @Suppress("DEPRECATION_ERROR")
    val builder = LiveStreamLoadResponse(
        name = name,
        url = url,
        apiName = this.name,
        dataUrl = dataUrl,
        comingSoon = dataUrl.isBlank()
    )
    builder.initializer()
    return builder
}

/** Data class of [LoadResponse] interface for Movies.
 * @see newMovieLoadResponse
 * */
data class MovieLoadResponse
@Deprecated("Use newMovieLoadResponse method", level = DeprecationLevel.ERROR)
constructor(
    override var name: String,
    override var url: String,
    override var apiName: String,
    override var type: TvType,
    var dataUrl: String,

    override var posterUrl: String? = null,
    override var year: Int? = null,
    override var plot: String? = null,

    override var score: Score? = null,
    override var tags: List<String>? = null,
    override var duration: Int? = null,
    override var trailers: MutableList<TrailerData> = mutableListOf(),
    override var recommendations: List<SearchResponse>? = null,
    override var actors: List<ActorData>? = null,
    override var comingSoon: Boolean = false,
    override var syncData: MutableMap<String, String> = mutableMapOf(),
    override var posterHeaders: Map<String, String>? = null,
    override var backgroundPosterUrl: String? = null,
    override var contentRating: String? = null,
    override var uniqueUrl: String = url
) : LoadResponse {
    /**
     * Secondary constructor for backwards compatibility without contentRating.
     * Remove this constructor after there is a new stable release and extensions are updated to support contentRating.
     */
    @Suppress("DEPRECATION_ERROR")
    @Deprecated(
        "Use newMovieLoadResponse method with contentRating included",
        level = DeprecationLevel.ERROR
    )
    constructor(
        name: String,
        url: String,
        apiName: String,
        type: TvType,
        dataUrl: String,
        posterUrl: String? = null,
        year: Int? = null,
        plot: String? = null,
        rating: Int? = null,
        tags: List<String>? = null,
        duration: Int? = null,
        trailers: MutableList<TrailerData> = mutableListOf(),
        recommendations: List<SearchResponse>? = null,
        actors: List<ActorData>? = null,
        comingSoon: Boolean = false,
        syncData: MutableMap<String, String> = mutableMapOf(),
        posterHeaders: Map<String, String>? = null,
        backgroundPosterUrl: String? = null,
    ) : this(
        name,
        url,
        apiName,
        type,
        dataUrl,
        posterUrl,
        year,
        plot,
        Score.fromOld(rating),
        tags,
        duration,
        trailers,
        recommendations,
        actors,
        comingSoon,
        syncData,
        posterHeaders,
        backgroundPosterUrl,
        null
    )
}

suspend fun <T> MainAPI.newMovieLoadResponse(
    name: String,
    url: String,
    type: TvType,
    data: T?,
    initializer: suspend MovieLoadResponse.() -> Unit = { }
): MovieLoadResponse {
    // just in case
    if (data is String) return newMovieLoadResponse(
        name,
        url,
        type,
        dataUrl = data,
        initializer = initializer
    )
    val dataUrl = data?.toJson() ?: ""

    @Suppress("DEPRECATION_ERROR")
    val builder = MovieLoadResponse(
        name = name,
        url = url,
        apiName = this.name,
        type = type,
        dataUrl = dataUrl,
        comingSoon = dataUrl.isBlank()
    )
    builder.initializer()
    return builder
}

suspend fun MainAPI.newMovieLoadResponse(
    name: String,
    url: String,
    type: TvType,
    dataUrl: String,
    initializer: suspend MovieLoadResponse.() -> Unit = { }
): MovieLoadResponse {
    @Suppress("DEPRECATION_ERROR")
    val builder = MovieLoadResponse(
        name = name,
        url = url,
        apiName = this.name,
        type = type,
        dataUrl = dataUrl,
        comingSoon = dataUrl.isBlank()
    )
    builder.initializer()
    return builder
}

/** Episode information that will be passed to LoadLinks function & showed on UI
 * @property data string used as main LoadLinks fun parameter.
 * @property name Name of the Episode.
 * @property season Season number.
 * @property episode Episode number.
 * @property posterUrl URL of Episode's poster image.
 * @property score Episode rating.
 * @property date Episode air date, see addDate.
 * @property runTime Episode runtime in seconds.
 * @see newEpisode
 * */
data class Episode
@Deprecated("Use newEpisode method", level = DeprecationLevel.ERROR)
constructor(
    var data: String,
    var name: String? = null,
    var season: Int? = null,
    var episode: Int? = null,
    var posterUrl: String? = null,
    var score: Score? = null,
    var description: String? = null,
    var date: Long? = null,
    var runTime: Int? = null,
) {
    @Deprecated(
        "`rating` is the old scoring system, use score instead",
        replaceWith = ReplaceWith("score")
    )
    var rating: Int?
        set(value) {
            this.score = Score.from(value, 100)
        }
        get() = score?.toInt(100)

    /**
     * Secondary constructor for backwards compatibility without runTime.
     *  TODO Remove this constructor after there is a new stable release and extensions are updated to support runTime.
     */
    @Suppress("DEPRECATION_ERROR")
    @Deprecated("Use newEpisode method", level = DeprecationLevel.ERROR)
    constructor(
        data: String,
        name: String? = null,
        season: Int? = null,
        episode: Int? = null,
        posterUrl: String? = null,
        rating: Int? = null,
        description: String? = null,
        date: Long? = null,
    ) : this(
        data, name, season, episode, posterUrl, Score.fromOld(rating), description, date, null
    )
}

fun Episode.addDate(date: String?, format: String = "yyyy-MM-dd") {
    try {
        this.date = SimpleDateFormat(format).parse(date ?: return)?.time
    } catch (e: Exception) {
        logError(e)
    }
}

fun Episode.addDate(date: Date?) {
    this.date = date?.time
}

fun MainAPI.newEpisode(
    url: String,
    initializer: Episode.() -> Unit = { },
    fix: Boolean = true,
): Episode {
    @Suppress("DEPRECATION_ERROR")
    val builder = Episode(
        data = if (fix) fixUrl(url) else url
    )
    builder.initializer()
    return builder
}

fun <T> MainAPI.newEpisode(
    data: T,
    initializer: Episode.() -> Unit = { }
): Episode {
    if (data is String) return newEpisode(
        url = data,
        initializer = initializer
    ) // just in case java is wack

    @Suppress("DEPRECATION_ERROR")
    val builder = Episode(
        data = data?.toJson() ?: throw ErrorLoadingException("invalid newEpisode")
    )
    builder.initializer()
    return builder
}

interface IDownloadableMinimum {
    val url: String
    val referer: String
    val headers: Map<String, String>
}

/**
 * Set of sync services simkl is compatible with.
 * Add more as required: https://simkl.docs.apiary.io/#reference/search/id-lookup/get-items-by-id
 */
enum class SimklSyncServices(val originalName: String) {
    Simkl("simkl"),
    Imdb("imdb"),
    Tmdb("tmdb"),
    AniList("anilist"),
    Mal("mal"),
}

/** Data class of [LoadResponse] interface for Tv series.
 * @see newTvSeriesLoadResponse
 * */
data class TvSeriesLoadResponse
@Deprecated("Use newTvSeriesLoadResponse method", level = DeprecationLevel.ERROR)
constructor(
    override var name: String,
    override var url: String,
    override var apiName: String,
    override var type: TvType,
    var episodes: List<Episode>,

    override var posterUrl: String? = null,
    override var year: Int? = null,
    override var plot: String? = null,

    override var showStatus: ShowStatus? = null,
    override var score: Score? = null,
    override var tags: List<String>? = null,
    override var duration: Int? = null,
    override var trailers: MutableList<TrailerData> = mutableListOf(),
    override var recommendations: List<SearchResponse>? = null,
    override var actors: List<ActorData>? = null,
    override var comingSoon: Boolean = false,
    override var syncData: MutableMap<String, String> = mutableMapOf(),
    override var posterHeaders: Map<String, String>? = null,
    override var nextAiring: NextAiring? = null,
    override var seasonNames: List<SeasonData>? = null,
    override var backgroundPosterUrl: String? = null,
    override var contentRating: String? = null,
    override var uniqueUrl: String = url
) : LoadResponse, EpisodeResponse {
    override fun getLatestEpisodes(): Map<DubStatus, Int?> {
        val maxSeason =
            episodes.maxOfOrNull { it.season ?: Int.MIN_VALUE }.takeUnless { it == Int.MIN_VALUE }
        val max = episodes
            .filter { it.season == maxSeason }
            .maxOfOrNull { it.episode ?: Int.MIN_VALUE }
            .takeUnless { it == Int.MIN_VALUE }
        return mapOf(DubStatus.None to max)
    }

    override fun getTotalEpisodeIndex(episode: Int, season: Int): Int {
        val displayMap = this.seasonNames?.associate { it.season to it.displaySeason } ?: emptyMap()

        return episodes.count { episodeData ->
            // Prioritize display season as actual season may be something random to fit multiple seasons into one.
            val episodeSeason =
                displayMap[episodeData.season] ?: episodeData.season ?: Int.MIN_VALUE
            // Count all episodes from season 1 to below the current season.
            episodeSeason in 1..<season
        } + episode
    }

    /**
     * Secondary constructor for backwards compatibility without contentRating.
     * Remove this constructor after there is a new stable release and extensions are updated to support contentRating.
     */
    @Suppress("DEPRECATION_ERROR")
    @Deprecated(
        "Use newTvSeriesLoadResponse method with contentRating included",
        level = DeprecationLevel.ERROR
    )
    constructor(
        name: String,
        url: String,
        apiName: String,
        type: TvType,
        episodes: List<Episode>,
        posterUrl: String? = null,
        year: Int? = null,
        plot: String? = null,
        showStatus: ShowStatus? = null,
        rating: Int? = null,
        tags: List<String>? = null,
        duration: Int? = null,
        trailers: MutableList<TrailerData> = mutableListOf(),
        recommendations: List<SearchResponse>? = null,
        actors: List<ActorData>? = null,
        comingSoon: Boolean = false,
        syncData: MutableMap<String, String> = mutableMapOf(),
        posterHeaders: Map<String, String>? = null,
        nextAiring: NextAiring? = null,
        seasonNames: List<SeasonData>? = null,
        backgroundPosterUrl: String? = null,
    ) : this(
        name,
        url,
        apiName,
        type,
        episodes,
        posterUrl,
        year,
        plot,
        showStatus,
        Score.fromOld(rating),
        tags,
        duration,
        trailers,
        recommendations,
        actors,
        comingSoon,
        syncData,
        posterHeaders,
        nextAiring,
        seasonNames,
        backgroundPosterUrl,
        null
    )
}

suspend fun MainAPI.newTvSeriesLoadResponse(
    name: String,
    url: String,
    type: TvType,
    episodes: List<Episode>,
    initializer: suspend TvSeriesLoadResponse.() -> Unit = { }
): TvSeriesLoadResponse {
    @Suppress("DEPRECATION_ERROR")
    val builder = TvSeriesLoadResponse(
        name = name,
        url = url,
        apiName = this.name,
        type = type,
        episodes = episodes,
        comingSoon = episodes.isEmpty(),
    )
    builder.initializer()
    return builder
}

fun fetchUrls(text: String?): List<String> {
    if (text.isNullOrEmpty()) {
        return listOf()
    }
    val linkRegex =
        Regex("""(https?://(www\.)?[-a-zA-Z0-9@:%._+~#=]{1,256}\.[a-zA-Z0-9()]{1,6}\b([-a-zA-Z0-9()@:%_+.~#?&/=]*))""")
    return linkRegex.findAll(text).map { it.value.trim().removeSurrounding("\"") }.toList()
}

fun String?.toRatingInt(): Int? =
    this?.replace(" ", "")?.trim()?.toDoubleOrNull()?.absoluteValue?.times(1000f)?.toInt()

data class Tracker(
    val malId: Int? = null,
    val aniId: String? = null,
    val image: String? = null,
    val cover: String? = null,
)

data class AniSearch(
    @JsonProperty("data") var data: Data? = Data()
) {
    data class Data(
        @JsonProperty("Page") var page: Page? = Page()
    ) {
        data class Page(
            @JsonProperty("media") var media: ArrayList<Media> = arrayListOf()
        ) {
            data class Media(
                @JsonProperty("title") var title: Title? = null,
                @JsonProperty("id") var id: Int? = null,
                @JsonProperty("idMal") var idMal: Int? = null,
                @JsonProperty("seasonYear") var seasonYear: Int? = null,
                @JsonProperty("format") var format: String? = null,
                @JsonProperty("coverImage") var coverImage: CoverImage? = null,
                @JsonProperty("bannerImage") var bannerImage: String? = null,
            ) {
                data class CoverImage(
                    @JsonProperty("extraLarge") var extraLarge: String? = null,
                    @JsonProperty("large") var large: String? = null,
                )

                data class Title(
                    @JsonProperty("romaji") var romaji: String? = null,
                    @JsonProperty("english") var english: String? = null,
                ) {
                    fun isMatchingTitles(title: String?): Boolean {
                        if (title == null) return false
                        return english.equals(title, true) || romaji.equals(title, true)
                    }
                }
            }
        }
    }
}

/**
 * used for the getTracker() method
 **/
enum class TrackerType {
    MOVIE,
    TV,
    TV_SHORT,
    ONA,
    OVA,
    SPECIAL,
    MUSIC;

    companion object {
        fun getTypes(type: TvType): Set<TrackerType> {
            return when (type) {
                TvType.Movie -> setOf(MOVIE)
                TvType.AnimeMovie -> setOf(MOVIE)
                TvType.TvSeries -> setOf(TV, TV_SHORT)
                TvType.Anime -> setOf(TV, TV_SHORT, ONA, OVA)
                TvType.OVA -> setOf(OVA, SPECIAL, ONA)
                TvType.Others -> setOf(MUSIC)
                else -> emptySet()
            }
        }
    }
}
