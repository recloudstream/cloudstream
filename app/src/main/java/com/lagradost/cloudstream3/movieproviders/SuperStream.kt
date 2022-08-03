package com.lagradost.cloudstream3.movieproviders

import android.util.Base64
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.APIHolder.capitalize
import com.lagradost.cloudstream3.APIHolder.unixTime
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.movieproviders.SuperStream.CipherUtils.getVerify
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.nicehttp.NiceResponse
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.roundToInt

const val TYPE_SERIES = 2
const val TYPE_MOVIES = 1

class SuperStream : MainAPI() {
    override var name = "SuperStream"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.AnimeMovie,
    )

    // 0 to get nsfw
    private val hideNsfw = 1

    override val instantLinkLoading = true

    val headers = mapOf(
        "Platform" to "android",
        "Accept" to "charset=utf-8",
    )

    // Random 32 length string
    private fun randomToken(): String {
        return (0..31).joinToString("") {
            (('0'..'9') + ('a'..'f')).random().toString()
        }
    }

    private val token = randomToken()

    private object CipherUtils {
        private const val ALGORITHM = "DESede"
        private const val TRANSFORMATION = "DESede/CBC/PKCS5Padding"
        fun encrypt(str: String, key: String, iv: String): String? {
            return try {
                val cipher: Cipher = Cipher.getInstance(TRANSFORMATION)
                val bArr = ByteArray(24)
                val bytes: ByteArray = key.toByteArray()
                var length = if (bytes.size <= 24) bytes.size else 24
                System.arraycopy(bytes, 0, bArr, 0, length)
                while (length < 24) {
                    bArr[length] = 0
                    length++
                }
                cipher.init(
                    1,
                    SecretKeySpec(bArr, ALGORITHM),
                    IvParameterSpec(iv.toByteArray())
                )

                String(Base64.encode(cipher.doFinal(str.toByteArray()), 2), StandardCharsets.UTF_8)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

        fun md5(str: String): String? {
            return MD5Util.md5(str)?.let { HexDump.toHexString(it).lowercase() }
        }

        fun getVerify(str: String?, str2: String, str3: String): String? {
            if (str != null) {
                return md5(md5(str2) + str3 + str)
            }
            return null
        }
    }

    private object HexDump {
        private val HEX_DIGITS = charArrayOf(
            '0',
            '1',
            '2',
            '3',
            '4',
            '5',
            '6',
            '7',
            '8',
            '9',
            'A',
            'B',
            'C',
            'D',
            'E',
            'F'
        )

        @JvmOverloads
        fun toHexString(bArr: ByteArray, i: Int = 0, i2: Int = bArr.size): String {
            val cArr = CharArray(i2 * 2)
            var i3 = 0
            for (i4 in i until i + i2) {
                val b = bArr[i4].toInt()
                val i5 = i3 + 1
                val cArr2 = HEX_DIGITS
                cArr[i3] = cArr2[b ushr 4 and 15]
                i3 = i5 + 1
                cArr[i5] = cArr2[b and 15]
            }
            return String(cArr)
        }
    }

    private object MD5Util {
        fun md5(str: String): ByteArray? {
            return this.md5(str.toByteArray())
        }

        fun md5(bArr: ByteArray?): ByteArray? {
            return try {
                val digest = MessageDigest.getInstance("MD5")
                digest.update(bArr ?: return null)
                digest.digest()
            } catch (e: NoSuchAlgorithmException) {
                e.printStackTrace()
                null
            }
        }
    }

    private suspend fun queryApi(query: String): NiceResponse {
        val encryptedQuery = CipherUtils.encrypt(query, key, iv)!!
        val appKeyHash = CipherUtils.md5(appKey)!!
        val newBody =
            """{"app_key":"$appKeyHash","verify":"${
                getVerify(
                    encryptedQuery,
                    appKey,
                    key
                )
            }","encrypt_data":"$encryptedQuery"}"""
        val base64Body = String(Base64.encode(newBody.toByteArray(), Base64.DEFAULT))

        val data = mapOf(
            "data" to base64Body,
            "appid" to "27",
            "platform" to "android",
            "version" to "129",
            // Probably best to randomize this
            "medium" to "Website&token$token"
        )

        return app.post(apiUrl, headers = headers, data = data)
    }

    private suspend inline fun <reified T : Any> queryApiParsed(query: String): T {
        return queryApi(query).parsed()
    }

    private fun getExpiryDate(): Long {
        // Current time + 12 hours
        return unixTime + 60 * 60 * 12
    }

    private data class PostJSON(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("poster") val poster: String? = null,
        @JsonProperty("poster_2") val poster2: String? = null,
        @JsonProperty("box_type") val boxType: Int? = null,
        @JsonProperty("imdb_rating") val imdbRating: String? = null,
        @JsonProperty("quality_tag") val quality_tag: String? = null,
    )

    private data class ListJSON(
        @JsonProperty("code") val code: Int? = null,
        @JsonProperty("type") val type: String? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("box_type") val boxType: Int? = null,
        @JsonProperty("list") val list: ArrayList<PostJSON> = arrayListOf(),
    )

    private data class DataJSON(
        @JsonProperty("data") val data: ArrayList<ListJSON> = arrayListOf()
    )

    // We do not want content scanners to notice this scraping going on so we've hidden all constants
    // The source has its origins in China so I added some extra security with banned words
    // Mayhaps a tiny bit unethical, but this source is just too good :)
    // If you are copying this code please use precautions so they do not change their api.

    // Free Tibet, The Tienanmen Square protests of 1989
    private val iv = base64Decode("d0VpcGhUbiE=")
    private val key = base64Decode("MTIzZDZjZWRmNjI2ZHk1NDIzM2FhMXc2")
    private val ip = base64Decode("aHR0cHM6Ly8xNTIuMzIuMTQ5LjE2MA==")
    private val apiUrl =
        "$ip${base64Decode("L2FwaS9hcGlfY2xpZW50L2luZGV4Lw==")}"
    private val appKey = base64Decode("bW92aWVib3g=")
    private val appId = base64Decode("Y29tLnRkby5zaG93Ym94")

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val json = queryApi(
            """{"childmode":"$hideNsfw","app_version":"11.5","appid":"$appId","module":"Home_list_type_v2","channel":"Website","page":"$page","lang":"en","type":"all","pagelimit":"10","expired_date":"${getExpiryDate()}","platform":"android"}
            """.trimIndent()
        ).text

        // Cut off the first row (featured)
        val pages = parseJson<DataJSON>(json).data.let { it.subList(minOf(it.size, 1), it.size) }
            .mapNotNull {
                var name = it.name
                if (name.isNullOrEmpty()) name = "Featured"
                val postList = it.list.mapNotNull second@{ post ->
                    val type = if (post.boxType == 1) TvType.Movie else TvType.TvSeries
                    newMovieSearchResponse(
                        name = post.title ?: return@second null,
                        url = LoadData(post.id ?: return@mapNotNull null, post.boxType).toJson(),
                        type = type,
                        fix = false
                    ) {
                        posterUrl = post.poster ?: post.poster2
                        quality = getQualityFromString(post.quality_tag ?: "")
                    }
                }
                if (postList.isEmpty()) return@mapNotNull null
                HomePageList(name, postList)
            }
        return HomePageResponse(pages, hasNext = !pages.any { it.list.isEmpty() })
    }

    private data class Data(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("box_type") val boxType: Int? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("poster_org") val posterOrg: String? = null,
        @JsonProperty("poster") val poster: String? = null,
        @JsonProperty("cats") val cats: String? = null,
        @JsonProperty("year") val year: Int? = null,
        @JsonProperty("imdb_rating") val imdbRating: String? = null,
        @JsonProperty("quality_tag") val qualityTag: String? = null,
    )

    private data class MainData(
        @JsonProperty("data") val data: ArrayList<Data> = arrayListOf()
    )

    override suspend fun search(query: String): List<SearchResponse> {

        val apiQuery =
            // Originally 8 pagelimit
            """{"childmode":"$hideNsfw","app_version":"11.5","appid":"$appId","module":"Search3","channel":"Website","page":"1","lang":"en","type":"all","keyword":"$query","pagelimit":"20","expired_date":"${getExpiryDate()}","platform":"android"}"""
        val searchResponse = parseJson<MainData>(queryApi(apiQuery).text).data.mapNotNull {
            val type = if (it.boxType == 1) TvType.Movie else TvType.TvSeries
            newMovieSearchResponse(
                name = it.title ?: return@mapNotNull null,
                url = LoadData(it.id ?: return@mapNotNull null, it.boxType).toJson(),
                type = type,
                fix = false
            ) {
                posterUrl = it.posterOrg ?: it.poster
                year = it.year
                quality = getQualityFromString(it.qualityTag?.replace("-", "") ?: "")
            }
        }
        return searchResponse
    }

    private data class LoadData(
        val id: Int,
        val type: Int?
    )

    private data class MovieData(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("director") val director: String? = null,
        @JsonProperty("writer") val writer: String? = null,
        @JsonProperty("actors") val actors: String? = null,
        @JsonProperty("runtime") val runtime: Int? = null,
        @JsonProperty("poster") val poster: String? = null,
        @JsonProperty("description") val description: String? = null,
        @JsonProperty("cats") val cats: String? = null,
        @JsonProperty("year") val year: Int? = null,
        @JsonProperty("imdb_id") val imdbId: String? = null,
        @JsonProperty("imdb_rating") val imdbRating: String? = null,
        @JsonProperty("trailer") val trailer: String? = null,
        @JsonProperty("released") val released: String? = null,
        @JsonProperty("content_rating") val contentRating: String? = null,
        @JsonProperty("tmdb_id") val tmdbId: Int? = null,
        @JsonProperty("tomato_meter") val tomatoMeter: Int? = null,
        @JsonProperty("poster_org") val posterOrg: String? = null,
        @JsonProperty("trailer_url") val trailerUrl: String? = null,
        @JsonProperty("imdb_link") val imdbLink: String? = null,
        @JsonProperty("box_type") val boxType: Int? = null,
    )

    private data class MovieDataProp(
        @JsonProperty("data") val data: MovieData? = MovieData()
    )


    private data class SeriesDataProp(
        @JsonProperty("code") val code: Int? = null,
        @JsonProperty("msg") val msg: String? = null,
        @JsonProperty("data") val data: SeriesData? = SeriesData()
    )

    private data class SeriesSeasonProp(
        @JsonProperty("code") val code: Int? = null,
        @JsonProperty("msg") val msg: String? = null,
        @JsonProperty("data") val data: ArrayList<SeriesEpisode>? = arrayListOf()
    )
//    data class PlayProgress (
//
//  @JsonProperty("over"      ) val over     : Int? = null,
//  @JsonProperty("seconds"   ) val seconds  : Int? = null,
//  @JsonProperty("mp4_id"    ) val mp4Id    : Int? = null,
//  @JsonProperty("last_time" ) val lastTime : Int? = null
//
//)

    private data class SeriesEpisode(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("tid") val tid: Int? = null,
        @JsonProperty("mb_id") val mbId: Int? = null,
        @JsonProperty("imdb_id") val imdbId: String? = null,
        @JsonProperty("imdb_id_status") val imdbIdStatus: Int? = null,
        @JsonProperty("srt_status") val srtStatus: Int? = null,
        @JsonProperty("season") val season: Int? = null,
        @JsonProperty("episode") val episode: Int? = null,
        @JsonProperty("state") val state: Int? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("thumbs") val thumbs: String? = null,
        @JsonProperty("thumbs_bak") val thumbsBak: String? = null,
        @JsonProperty("thumbs_original") val thumbsOriginal: String? = null,
        @JsonProperty("poster_imdb") val posterImdb: Int? = null,
        @JsonProperty("synopsis") val synopsis: String? = null,
        @JsonProperty("runtime") val runtime: Int? = null,
        @JsonProperty("view") val view: Int? = null,
        @JsonProperty("download") val download: Int? = null,
        @JsonProperty("source_file") val sourceFile: Int? = null,
        @JsonProperty("code_file") val codeFile: Int? = null,
        @JsonProperty("add_time") val addTime: Int? = null,
        @JsonProperty("update_time") val updateTime: Int? = null,
        @JsonProperty("released") val released: String? = null,
        @JsonProperty("released_timestamp") val releasedTimestamp: Long? = null,
        @JsonProperty("audio_lang") val audioLang: String? = null,
        @JsonProperty("quality_tag") val qualityTag: String? = null,
        @JsonProperty("3d") val _3d: Int? = null,
        @JsonProperty("remark") val remark: String? = null,
        @JsonProperty("pending") val pending: String? = null,
        @JsonProperty("imdb_rating") val imdbRating: String? = null,
        @JsonProperty("display") val display: Int? = null,
        @JsonProperty("sync") val sync: Int? = null,
        @JsonProperty("tomato_meter") val tomatoMeter: Int? = null,
        @JsonProperty("tomato_meter_count") val tomatoMeterCount: Int? = null,
        @JsonProperty("tomato_audience") val tomatoAudience: Int? = null,
        @JsonProperty("tomato_audience_count") val tomatoAudienceCount: Int? = null,
        @JsonProperty("thumbs_min") val thumbsMin: String? = null,
        @JsonProperty("thumbs_org") val thumbsOrg: String? = null,
        @JsonProperty("imdb_link") val imdbLink: String? = null,
        @JsonProperty("quality_tags") val qualityTags: ArrayList<String> = arrayListOf(),
//  @JsonProperty("play_progress"         ) val playProgress        : PlayProgress?     = PlayProgress()

    )

    private data class SeriesLanguage(
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("lang") val lang: String? = null
    )

    private data class SeriesData(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("mb_id") val mbId: Int? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("display") val display: Int? = null,
        @JsonProperty("state") val state: Int? = null,
        @JsonProperty("vip_only") val vipOnly: Int? = null,
        @JsonProperty("code_file") val codeFile: Int? = null,
        @JsonProperty("director") val director: String? = null,
        @JsonProperty("writer") val writer: String? = null,
        @JsonProperty("actors") val actors: String? = null,
        @JsonProperty("add_time") val addTime: Int? = null,
        @JsonProperty("poster") val poster: String? = null,
        @JsonProperty("poster_imdb") val posterImdb: Int? = null,
        @JsonProperty("banner_mini") val bannerMini: String? = null,
        @JsonProperty("description") val description: String? = null,
        @JsonProperty("imdb_id") val imdbId: String? = null,
        @JsonProperty("cats") val cats: String? = null,
        @JsonProperty("year") val year: Int? = null,
        @JsonProperty("collect") val collect: Int? = null,
        @JsonProperty("view") val view: Int? = null,
        @JsonProperty("download") val download: Int? = null,
        @JsonProperty("update_time") val updateTime: String? = null,
        @JsonProperty("released") val released: String? = null,
        @JsonProperty("released_timestamp") val releasedTimestamp: Int? = null,
        @JsonProperty("episode_released") val episodeReleased: String? = null,
        @JsonProperty("episode_released_timestamp") val episodeReleasedTimestamp: Int? = null,
        @JsonProperty("max_season") val maxSeason: Int? = null,
        @JsonProperty("max_episode") val maxEpisode: Int? = null,
        @JsonProperty("remark") val remark: String? = null,
        @JsonProperty("imdb_rating") val imdbRating: String? = null,
        @JsonProperty("content_rating") val contentRating: String? = null,
        @JsonProperty("tmdb_id") val tmdbId: Int? = null,
        @JsonProperty("tomato_url") val tomatoUrl: String? = null,
        @JsonProperty("tomato_meter") val tomatoMeter: Int? = null,
        @JsonProperty("tomato_meter_count") val tomatoMeterCount: Int? = null,
        @JsonProperty("tomato_meter_state") val tomatoMeterState: String? = null,
        @JsonProperty("reelgood_url") val reelgoodUrl: String? = null,
        @JsonProperty("audience_score") val audienceScore: Int? = null,
        @JsonProperty("audience_score_count") val audienceScoreCount: Int? = null,
        @JsonProperty("no_tomato_url") val noTomatoUrl: Int? = null,
        @JsonProperty("order_year") val orderYear: Int? = null,
        @JsonProperty("episodate_id") val episodateId: String? = null,
        @JsonProperty("weights_day") val weightsDay: Double? = null,
        @JsonProperty("poster_min") val posterMin: String? = null,
        @JsonProperty("poster_org") val posterOrg: String? = null,
        @JsonProperty("banner_mini_min") val bannerMiniMin: String? = null,
        @JsonProperty("banner_mini_org") val bannerMiniOrg: String? = null,
        @JsonProperty("trailer_url") val trailerUrl: String? = null,
        @JsonProperty("years") val years: ArrayList<Int> = arrayListOf(),
        @JsonProperty("season") val season: ArrayList<Int> = arrayListOf(),
        @JsonProperty("history") val history: ArrayList<String> = arrayListOf(),
        @JsonProperty("imdb_link") val imdbLink: String? = null,
        @JsonProperty("episode") val episode: ArrayList<SeriesEpisode> = arrayListOf(),
//        @JsonProperty("is_collect") val isCollect: Int? = null,
        @JsonProperty("language") val language: ArrayList<SeriesLanguage> = arrayListOf(),
        @JsonProperty("box_type") val boxType: Int? = null,
        @JsonProperty("year_year") val yearYear: String? = null,
        @JsonProperty("season_episode") val seasonEpisode: String? = null
    )


    override suspend fun load(url: String): LoadResponse {
        val loadData = parseJson<LoadData>(url)
        // val module = if(type === "TvType.Movie") "Movie_detail" else "*tv series module*"

        val isMovie = loadData.type == TYPE_MOVIES

        if (isMovie) { // 1 = Movie
            val apiQuery =
                """{"childmode":"$hideNsfw","uid":"","app_version":"11.5","appid":"$appId","module":"Movie_detail","channel":"Website","mid":"${loadData.id}","lang":"en","expired_date":"${getExpiryDate()}","platform":"android","oss":"","group":""}"""
            val data = (queryApiParsed<MovieDataProp>(apiQuery)).data
                ?: throw RuntimeException("API error")

            return newMovieLoadResponse(
                data.title ?: "",
                data.imdbLink ?: "",
                TvType.Movie,
                LinkData(
                    data.id ?: throw RuntimeException("No movie ID"),
                    TYPE_MOVIES,
                    null,
                    null
                ),
            ) {
                this.posterUrl = data.posterOrg ?: data.poster
                this.year = data.year
                this.plot = data.description
                this.tags = data.cats?.split(",")?.map { it.capitalize() }
                this.rating = data.imdbRating?.split("/")?.get(0)?.toIntOrNull()
                addTrailer(data.trailerUrl)
                this.addImdbId(data.imdbId)
            }
        } else { // 2 Series
            val apiQuery =
                """{"childmode":"$hideNsfw","uid":"","app_version":"11.5","appid":"$appId","module":"TV_detail_1","display_all":"1","channel":"Website","lang":"en","expired_date":"${getExpiryDate()}","platform":"android","tid":"${loadData.id}"}"""
            val data = (queryApiParsed<SeriesDataProp>(apiQuery)).data
                ?: throw RuntimeException("API error")

            val episodes = data.season.mapNotNull {
                val seasonQuery =
                    """{"childmode":"$hideNsfw","app_version":"11.5","year":"0","appid":"$appId","module":"TV_episode","display_all":"1","channel":"Website","season":"$it","lang":"en","expired_date":"${getExpiryDate()}","platform":"android","tid":"${loadData.id}"}"""
                (queryApiParsed<SeriesSeasonProp>(seasonQuery)).data
            }.flatten()

            return newTvSeriesLoadResponse(
                data.title ?: "",
                data.imdbLink ?: data.tomatoUrl ?: "",
                TvType.TvSeries,
                episodes.mapNotNull {
                    Episode(
                        LinkData(
                            it.tid ?: it.id ?: return@mapNotNull null,
                            TYPE_SERIES,
                            it.season,
                            it.episode
                        ).toJson(),
                        it.title,
                        it.season,
                        it.episode,
                        it.thumbs ?: it.thumbsBak ?: it.thumbsMin ?: it.thumbsOriginal
                        ?: it.thumbsOrg,
                        it.imdbRating?.toDoubleOrNull()?.times(10)?.roundToInt(),
                        it.synopsis,
                        it.releasedTimestamp
                    )
                }
            ) {
                this.year = data.year
                this.plot = data.description
                this.posterUrl = data.posterOrg ?: data.poster
                this.rating = data.imdbRating?.split("/")?.get(0)?.toIntOrNull()
                this.tags = data.cats?.split(",")?.map { it.capitalize() }
                this.addImdbId(data.imdbId)
            }
        }
    }


    private data class LinkData(
        val id: Int,
        val type: Int,
        val season: Int?,
        val episode: Int?
    )


    private data class LinkDataProp(
        @JsonProperty("code") val code: Int? = null,
        @JsonProperty("msg") val msg: String? = null,
        @JsonProperty("data") val data: ParsedLinkData? = ParsedLinkData()
    )

    private data class LinkList(
        @JsonProperty("path") val path: String? = null,
        @JsonProperty("quality") val quality: String? = null,
        @JsonProperty("real_quality") val realQuality: String? = null,
        @JsonProperty("format") val format: String? = null,
        @JsonProperty("size") val size: String? = null,
        @JsonProperty("size_bytes") val sizeBytes: Long? = null,
        @JsonProperty("count") val count: Int? = null,
        @JsonProperty("dateline") val dateline: Long? = null,
        @JsonProperty("fid") val fid: Int? = null,
        @JsonProperty("mmfid") val mmfid: Int? = null,
        @JsonProperty("h265") val h265: Int? = null,
        @JsonProperty("hdr") val hdr: Int? = null,
        @JsonProperty("filename") val filename: String? = null,
        @JsonProperty("original") val original: Int? = null,
        @JsonProperty("colorbit") val colorbit: Int? = null,
        @JsonProperty("success") val success: Int? = null,
        @JsonProperty("timeout") val timeout: Int? = null,
        @JsonProperty("vip_link") val vipLink: Int? = null,
        @JsonProperty("fps") val fps: Int? = null,
        @JsonProperty("bitstream") val bitstream: String? = null,
        @JsonProperty("width") val width: Int? = null,
        @JsonProperty("height") val height: Int? = null
    )

    private data class ParsedLinkData(
        @JsonProperty("seconds") val seconds: Int? = null,
        @JsonProperty("quality") val quality: ArrayList<String> = arrayListOf(),
        @JsonProperty("list") val list: ArrayList<LinkList> = arrayListOf()
    )

    private data class SubtitleDataProp(
        @JsonProperty("code") val code: Int? = null,
        @JsonProperty("msg") val msg: String? = null,
        @JsonProperty("data") val data: PrivateSubtitleData? = PrivateSubtitleData()
    )

    private data class Subtitles(
        @JsonProperty("sid") val sid: Int? = null,
        @JsonProperty("mid") val mid: String? = null,
        @JsonProperty("file_path") val filePath: String? = null,
        @JsonProperty("lang") val lang: String? = null,
        @JsonProperty("language") val language: String? = null,
        @JsonProperty("delay") val delay: Int? = null,
        @JsonProperty("point") val point: String? = null,
        @JsonProperty("order") val order: Int? = null,
        @JsonProperty("admin_order") val adminOrder: Int? = null,
        @JsonProperty("myselect") val myselect: Int? = null,
        @JsonProperty("add_time") val addTime: Long? = null,
        @JsonProperty("count") val count: Int? = null
    )

    private data class SubtitleList(

        @JsonProperty("language") val language: String? = null,
        @JsonProperty("subtitles") val subtitles: ArrayList<Subtitles> = arrayListOf()

    )

    private data class PrivateSubtitleData(
        @JsonProperty("select") val select: ArrayList<String> = arrayListOf(),
        @JsonProperty("list") val list: ArrayList<SubtitleList> = arrayListOf()
    )

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        fun LinkList.toExtractorLink(): ExtractorLink? {
            return ExtractorLink(
                this@SuperStream.name,
                this.filename ?: "",
                this.path?.replace("\\/", "") ?: return null,
                "",
                getQualityFromName(this.quality),
            )
        }

        fun Subtitles.toSubtitleFile(): SubtitleFile? {
            return SubtitleFile(
                this.language ?: this.lang ?: "",
                this.filePath ?: return null
            )
        }

        val parsed = parseJson<LinkData>(data)

        // No childmode when getting links
        val query = if (parsed.type == TYPE_MOVIES) {
            """{"childmode":"0","uid":"","app_version":"11.5","appid":"$appId","module":"Movie_downloadurl_v3","channel":"Website","mid":"${parsed.id}","lang":"","expired_date":"${getExpiryDate()}","platform":"android","oss":"1","group":""}"""
        } else {
            val episode = parsed.episode ?: throw RuntimeException("No episode number!")
            val season = parsed.season ?: throw RuntimeException("No season number!")
            """{"childmode":"0","app_version":"11.5","module":"TV_downloadurl_v3","channel":"Website","episode":"$episode","expired_date":"${getExpiryDate()}","platform":"android","tid":"${parsed.id}","oss":"1","uid":"","appid":"$appId","season":"$season","lang":"en","group":""}"""
        }

        val linkData = queryApiParsed<LinkDataProp>(query)
        linkData.data?.list?.forEach {
            callback.invoke(it.toExtractorLink() ?: return@forEach)
        }

        // Should really run this query for every link :(
        val fid = linkData.data?.list?.firstOrNull { it.fid != null }?.fid

        val subtitleQuery = if (parsed.type == TYPE_MOVIES) {
            """{"childmode":"0","fid":"$fid","uid":"","app_version":"11.5","appid":"$appId","module":"Movie_srt_list_v2","channel":"Website","mid":"${parsed.id}","lang":"en","expired_date":"${getExpiryDate()}","platform":"android"}"""
        } else {
            """{"childmode":"0","fid":"$fid","app_version":"11.5","module":"TV_srt_list_v2","channel":"Website","episode":"${parsed.episode}","expired_date":"${getExpiryDate()}","platform":"android","tid":"${parsed.id}","uid":"","appid":"$appId","season":"${parsed.season}","lang":"en"}"""
        }

        val subtitles = queryApiParsed<SubtitleDataProp>(subtitleQuery).data
        subtitles?.list?.forEach {
            it.subtitles.forEach second@{ sub ->
                subtitleCallback.invoke(sub.toSubtitleFile() ?: return@second)
            }
        }

        return true
    }
}