package com.lagradost.cloudstream3.utils

import com.lagradost.cloudstream3.utils.extractors.MixDrop
import com.lagradost.cloudstream3.utils.extractors.Mp4Upload
import com.lagradost.cloudstream3.utils.extractors.Shiro
import com.lagradost.cloudstream3.cloudstream3.extractors.StreamTape
import com.lagradost.cloudstream3.utils.extractors.XStreamCdn

data class ExtractorLink(
    val source: String,
    val name: String,
    val url: String,
    val referer: String,
    val quality: Int,
    val isM3u8: Boolean = false,
)

fun ExtractorLink.getId() : Int {
    return url.hashCode()
}

enum class Qualities(var value: Int) {
    Unknown(0),
    SD(-1), // 360p - 480p
    HD(1), // 720p
    FullHd(2), // 1080p
    UHD(3) // 4k
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
    Mp4Upload(),
    StreamTape(),
    MixDrop(),
    XStreamCdn()
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

    abstract fun getUrl(url: String, referer: String? = null): List<ExtractorLink>?

    open fun getExtractorUrl(id: String): String {
        return id
    }
}