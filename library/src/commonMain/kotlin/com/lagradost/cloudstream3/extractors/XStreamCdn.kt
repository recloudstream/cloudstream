package com.lagradost.cloudstream3.extractors

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName

class StreamM4u : XStreamCdn() {
    override val name: String = "StreamM4u"
    override val mainUrl: String = "https://streamm4u.club"
}

class Fembed9hd : XStreamCdn() {
    override var mainUrl = "https://fembed9hd.com"
    override var name = "Fembed9hd"
}

class Cdnplayer: XStreamCdn() {
    override val name: String = "Cdnplayer"
    override val mainUrl: String = "https://cdnplayer.online"
}

class Kotakajair: XStreamCdn() {
    override val name: String = "Kotakajair"
    override val mainUrl: String = "https://kotakajair.xyz"
}

class FEnet: XStreamCdn() {
    override val name: String = "FEnet"
    override val mainUrl: String = "https://fembed.net"
}

class Rasacintaku: XStreamCdn() {
    override val mainUrl: String = "https://rasa-cintaku-semakin-berantai.xyz"
}

class LayarKaca: XStreamCdn() {
    override val name: String = "LayarKaca-xxi"
    override val mainUrl: String = "https://layarkacaxxi.icu"
}

class DBfilm: XStreamCdn() {
    override val name: String = "DBfilm"
    override val mainUrl: String = "https://dbfilm.bar"
}

class Luxubu : XStreamCdn(){
    override val name: String = "FE"
    override val mainUrl: String = "https://www.luxubu.review"
}

class FEmbed: XStreamCdn() {
    override val name: String = "FEmbed"
    override val mainUrl: String = "https://www.fembed.com"
}

class Fplayer: XStreamCdn() {
    override val name: String = "Fplayer"
    override val mainUrl: String = "https://fplayer.info"
}

class FeHD: XStreamCdn() {
    override val name: String = "FeHD"
    override val mainUrl: String = "https://fembed-hd.com"
    override var domainUrl: String = "fembed-hd.com"
}

open class XStreamCdn : ExtractorApi() {
    override val name: String = "XStreamCdn"
    override val mainUrl: String = "https://embedsito.com"
    override val requiresReferer = false
    open var domainUrl: String = "embedsito.com"

    private data class ResponseData(
        @JsonProperty("file") val file: String,
        @JsonProperty("label") val label: String,
        //val type: String // Mp4
    )

    private data class Player(
        @JsonProperty("poster_file") val poster_file: String? = null,
    )

    private data class ResponseJson(
        @JsonProperty("success") val success: Boolean,
        @JsonProperty("player") val player: Player? = null,
        @JsonProperty("data") val data: List<ResponseData>?,
        @JsonProperty("captions") val captions: List<Captions?>?,
    )

    private data class Captions(
        @JsonProperty("id") val id: String,
        @JsonProperty("hash") val hash: String,
        @JsonProperty("language") val language: String,
        @JsonProperty("extension") val extension: String
    )

    override fun getExtractorUrl(id: String): String {
        return "$domainUrl/api/source/$id"
    }

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val headers = mapOf(
            "Referer" to url,
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; rv:78.0) Gecko/20100101 Firefox/78.0",
        )
        val id = url.trimEnd('/').split("/").last()
        val newUrl = "https://${domainUrl}/api/source/${id}"
        app.post(newUrl, headers = headers).let { res ->
            val sources = tryParseJson<ResponseJson?>(res.text)
            sources?.let {
                if (it.success && it.data != null) {
                    it.data.map { source ->
                        callback.invoke(
                            ExtractorLink(
                                name,
                                name = name,
                                source.file,
                                url,
                                getQualityFromName(source.label),
                            )
                        )
                    }
                }
            }

            val userData = sources?.player?.poster_file?.split("/")?.get(2)
            sources?.captions?.map {
                subtitleCallback.invoke(
                    SubtitleFile(
                        it?.language.toString(),
                        "$mainUrl/asset/userdata/$userData/caption/${it?.hash}/${it?.id}.${it?.extension}"
                    )
                )
            }
        }
    }
}