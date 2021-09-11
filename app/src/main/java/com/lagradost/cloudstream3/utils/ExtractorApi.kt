package com.lagradost.cloudstream3.utils

import com.lagradost.cloudstream3.extractors.*
import com.lagradost.cloudstream3.mvvm.normalSafeApiCall

data class ExtractorLink(
    val source: String,
    val name: String,
    override val url: String,
    override val referer: String,
    val quality: Int,
    val isM3u8: Boolean = false,
    override val headers: Map<String, String> = mapOf()
) : VideoDownloadManager.IDownloadableMinimum

data class ExtractorSubtitleLink(
    val name: String,
    override val url: String,
    override val referer: String,
    override val headers: Map<String, String> = mapOf()
) : VideoDownloadManager.IDownloadableMinimum

enum class Qualities(var value: Int) {
    Unknown(0),
    P360(-2), // 360p
    P480(-1), // 480p
    P720(1), // 720p
    P1080(2), // 1080p
    P1440(3), // 1440p
    P2160(4) // 4k or 2160p
}

fun getQualityFromName(qualityName: String): Int {
    return when (qualityName.replace("p", "").replace("P", "")) {
        "360" -> Qualities.P360
        "480" -> Qualities.P480
        "720" -> Qualities.P720
        "1080" -> Qualities.P1080
        "1440" -> Qualities.P1440
        "2160" -> Qualities.P2160
        "4k" -> Qualities.P2160
        "4K" -> Qualities.P2160
        else -> Qualities.Unknown
    }.value
}

private val packedRegex = Regex("""eval\(function\(p,a,c,k,e,.*\)\)""")
fun getPacked(string: String): String? {
    return packedRegex.find(string)?.value
}

fun getAndUnpack(string: String): String? {
    val packedText = getPacked(string)
    return JsUnpacker(packedText).unpack()
}

fun loadExtractor(url: String, referer: String?, callback: (ExtractorLink) -> Unit) {
    for (extractor in extractorApis) {
        if (url.startsWith(extractor.mainUrl)) {
            extractor.getSafeUrl(url, referer)?.forEach(callback)
            return
        }
    }
}

val extractorApis: Array<ExtractorApi> = arrayOf(
    //AllProvider(),
    Shiro(),
    WcoStream(),
    Mp4Upload(),
    StreamTape(),
    MixDrop(),
    XStreamCdn(),
    StreamSB(),
    Streamhub(),

    // dood extractors
    DoodToExtractor(),
    DoodSoExtractor(),
    DoodLaExtractor(),
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

abstract class ExtractorApi {
    abstract val name: String
    abstract val mainUrl: String
    abstract val requiresReferer: Boolean

    fun getSafeUrl(url: String, referer: String? = null): List<ExtractorLink>? {
        return normalSafeApiCall { getUrl(url, referer) }
    }

    /**
     * Will throw errors, use getSafeUrl if you don't want to handle the exception yourself
     */
    abstract fun getUrl(url: String, referer: String? = null): List<ExtractorLink>?

    open fun getExtractorUrl(id: String): String {
        return id
    }
}
