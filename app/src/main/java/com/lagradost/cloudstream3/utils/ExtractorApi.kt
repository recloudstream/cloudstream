package com.lagradost.cloudstream3.utils

import com.lagradost.cloudstream3.mvvm.normalSafeApiCall
import com.lagradost.cloudstream3.utils.extractors.*

data class ExtractorLink(
    val source: String,
    val name: String,
    val url: String,
    val referer: String,
    val quality: Int,
    val isM3u8: Boolean = false,
)

fun ExtractorLink.getId(): Int {
    return url.hashCode()
}

enum class Qualities(var value: Int) {
    Unknown(0),
    SD(-1), // 360p - 480p
    HD(1), // 720p
    FullHd(2), // 1080p
    UHD(3) // 4k
}

fun getQualityFromName(qualityName: String): Int {
    return when (qualityName.replace("p", "").replace("P", "")) {
        "360" -> Qualities.SD
        "480" -> Qualities.SD
        "720" -> Qualities.HD
        "1080" -> Qualities.FullHd
        "1440" -> Qualities.UHD // I KNOW KINDA MISLEADING
        "2160" -> Qualities.UHD
        "4k" -> Qualities.UHD
        "4K" -> Qualities.UHD
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

val extractorApis: Array<ExtractorApi> = arrayOf(
    //AllProvider(),
    Shiro(),
    WcoStream(),
    Mp4Upload(),
    StreamTape(),
    MixDrop(),
    XStreamCdn(),
    StreamSB(),
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
