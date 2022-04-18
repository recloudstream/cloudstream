package com.lagradost.cloudstream3.utils

import android.net.Uri
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.*
import com.lagradost.cloudstream3.mvvm.suspendSafeApiCall
import kotlinx.coroutines.delay
import org.jsoup.Jsoup

data class ExtractorLink(
    val source: String,
    val name: String,
    override val url: String,
    override val referer: String,
    val quality: Int,
    val isM3u8: Boolean = false,
    override val headers: Map<String, String> = mapOf(),
    /** Used for getExtractorVerifierJob() */
    val extractorData: String? = null
) : VideoDownloadManager.IDownloadableMinimum

data class ExtractorUri(
    val uri: Uri,
    val name: String,

    val basePath: String? = null,
    val relativePath: String? = null,
    val displayName: String? = null,

    val id: Int? = null,
    val parentId: Int? = null,
    val episode: Int? = null,
    val season: Int? = null,
    val headerName: String? = null,
    val tvType: TvType? = null,
)

data class ExtractorSubtitleLink(
    val name: String,
    override val url: String,
    override val referer: String,
    override val headers: Map<String, String> = mapOf()
) : VideoDownloadManager.IDownloadableMinimum

enum class Qualities(var value: Int) {
    Unknown(400),
    P144(144), // 144p
    P240(240), // 240p
    P360(360), // 360p
    P480(480), // 480p
    P720(720), // 720p
    P1080(1080), // 1080p
    P1440(1440), // 1440p
    P2160(2160); // 4k or 2160p

    companion object {
        fun getStringByInt(qual: Int?): String {
            return when (qual) {
                0 -> "Auto"
                Unknown.value -> ""
                P2160.value -> "4K"
                else -> "${qual}p"
            }
        }
    }
}

fun getQualityFromName(qualityName: String?): Int {
    if (qualityName == null)
        return Qualities.Unknown.value

    val match = qualityName.lowercase().replace("p", "").trim()
    return when (match) {
        "4k" -> Qualities.P2160
        else -> null
    }?.value ?: match.toIntOrNull() ?: Qualities.Unknown.value
}

private val packedRegex = Regex("""eval\(function\(p,a,c,k,e,.*\)\)""")
fun getPacked(string: String): String? {
    return packedRegex.find(string)?.value
}

fun getAndUnpack(string: String): String {
    val packedText = getPacked(string)
    return JsUnpacker(packedText).unpack() ?: string
}

/**
 * Tries to load the appropriate extractor based on link, returns true if any extractor is loaded.
 * */
suspend fun loadExtractor(
    url: String,
    referer: String? = null,
    callback: (ExtractorLink) -> Unit
): Boolean {
    for (extractor in extractorApis) {
        if (url.startsWith(extractor.mainUrl)) {
            extractor.getSafeUrl(url, referer)?.forEach(callback)
            return true
        }
    }
    return false
}

val extractorApis: Array<ExtractorApi> = arrayOf(
    //AllProvider(),
    WcoStream(),
    Vidstreamz(),
    Vizcloud(),
    Vizcloud2(),
    VizcloudOnline(),
    VizcloudXyz(),
    VizcloudLive(),
    VizcloudInfo(),
    MwvnVizcloudInfo(),
    VizcloudDigital(),
    Mp4Upload(),
    StreamTape(),
    MixDrop(),
    Mcloud(),
    XStreamCdn(),
    StreamSB(),
    StreamSB1(),
    StreamSB2(),
    StreamSB3(),
    StreamSB4(),
    StreamSB5(),
    StreamSB6(),
    StreamSB7(),
    StreamSB8(),
    StreamSB9(),
    StreamSB10(),
    // Streamhub(), cause Streamhub2() works
    Streamhub2(),

    FEmbed(),
    FeHD(),
    Fplayer(),
    //  WatchSB(), 'cause StreamSB.kt works
    Uqload(),
    Uqload1(),
    Evoload(),
    Evoload1(),
    VoeExtractor(),
    // UpstreamExtractor(), GenericM3U8.kt works

    Tomatomatela(),
    Cinestart(),
    OkRu(),

    // dood extractors
    DoodToExtractor(),
    DoodSoExtractor(),
    DoodLaExtractor(),
    DoodWsExtractor(),

    AsianLoad(),

    // GenericM3U8(),
    Jawcloud(),
    Zplayer(),
    ZplayerV2(),
    Upstream(),


    // StreamSB.kt works
    //  SBPlay(),
    //  SBPlay1(),
    //  SBPlay2(),

    PlayerVoxzer(),
)

fun getExtractorApiFromName(name: String): ExtractorApi {
    for (api in extractorApis) {
        if (api.name == name) return api
    }
    return extractorApis[0]
}

fun requireReferer(name: String): Boolean {
    return getExtractorApiFromName(name).requiresReferer
}

fun httpsify(url: String): String {
    return if (url.startsWith("//")) "https:$url" else url
}

suspend fun getPostForm(requestUrl: String, html: String): String? {
    val document = Jsoup.parse(html)
    val inputs = document.select("Form > input")
    if (inputs.size < 4) return null
    var op: String? = null
    var id: String? = null
    var mode: String? = null
    var hash: String? = null

    for (input in inputs) {
        val value = input.attr("value") ?: continue
        when (input.attr("name")) {
            "op" -> op = value
            "id" -> id = value
            "mode" -> mode = value
            "hash" -> hash = value
            else -> Unit
        }
    }
    if (op == null || id == null || mode == null || hash == null) {
        return null
    }
    delay(5000) // ye this is needed, wont work with 0 delay

    return app.post(
        requestUrl,
        headers = mapOf(
            "content-type" to "application/x-www-form-urlencoded",
            "referer" to requestUrl,
            "user-agent" to USER_AGENT,
            "accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9"
        ),
        data = mapOf("op" to op, "id" to id, "mode" to mode, "hash" to hash)
    ).text
}

abstract class ExtractorApi {
    abstract val name: String
    abstract val mainUrl: String
    abstract val requiresReferer: Boolean

    suspend fun getSafeUrl(url: String, referer: String? = null): List<ExtractorLink>? {
        return suspendSafeApiCall { getUrl(url, referer) }
    }

    /**
     * Will throw errors, use getSafeUrl if you don't want to handle the exception yourself
     */
    abstract suspend fun getUrl(url: String, referer: String? = null): List<ExtractorLink>?

    open fun getExtractorUrl(id: String): String {
        return id
    }
}
