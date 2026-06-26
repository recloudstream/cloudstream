package com.lagradost.cloudstream3.extractors

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class StreamM4u : XStreamCdn() {
    override val name = "StreamM4u"
    override val mainUrl = "https://streamm4u.club"
}

class Fembed9hd : XStreamCdn() {
    override val name = "Fembed9hd"
    override val mainUrl = "https://fembed9hd.com"
}

class Cdnplayer : XStreamCdn() {
    override val name = "Cdnplayer"
    override val mainUrl = "https://cdnplayer.online"
}

class Kotakajair : XStreamCdn() {
    override val name = "Kotakajair"
    override val mainUrl = "https://kotakajair.xyz"
}

class FEnet : XStreamCdn() {
    override val name = "FEnet"
    override val mainUrl = "https://fembed.net"
}

class Rasacintaku : XStreamCdn() {
    override val mainUrl = "https://rasa-cintaku-semakin-berantai.xyz"
}

class LayarKaca : XStreamCdn() {
    override val name = "LayarKaca-xxi"
    override val mainUrl = "https://layarkacaxxi.icu"
}

class DBfilm : XStreamCdn() {
    override val name = "DBfilm"
    override val mainUrl = "https://dbfilm.bar"
}

class Luxubu : XStreamCdn() {
    override val name = "FE"
    override val mainUrl = "https://www.luxubu.review"
}

class FEmbed : XStreamCdn() {
    override val name = "FEmbed"
    override val mainUrl = "https://www.fembed.com"
}

class Fplayer : XStreamCdn() {
    override val name = "Fplayer"
    override val mainUrl = "https://fplayer.info"
}

class FeHD : XStreamCdn() {
    override val name = "FeHD"
    override val mainUrl = "https://fembed-hd.com"
    override val domainUrl = "fembed-hd.com"
}

open class XStreamCdn : ExtractorApi() {
    override val name = "XStreamCdn"
    override val mainUrl = "https://embedsito.com"
    override val requiresReferer = false
    open val domainUrl = "embedsito.com"

    override fun getExtractorUrl(id: String): String {
        return "$domainUrl/api/source/$id"
    }

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val headers = mapOf(
            "Referer" to url,
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; rv:78.0) Gecko/20100101 Firefox/78.0",
        )

        val id = url.trimEnd('/').split("/").last()
        val newUrl = "https://$domainUrl/api/source/$id"
        app.post(newUrl, headers = headers).let { res ->
            val sources = tryParseJson<ResponseJson?>(res.text)
            sources?.let {
                if (it.success && it.data != null) {
                    it.data.map { source ->
                        callback.invoke(
                            newExtractorLink(
                                name,
                                name = name,
                                source.file,
                            ) {
                                this.referer = url
                                this.quality = getQualityFromName(source.label)
                            }
                        )
                    }
                }
            }

            val userData = sources?.player?.posterFile?.split("/")?.get(2)
            sources?.captions?.map {
                subtitleCallback.invoke(
                    newSubtitleFile(
                        it?.language.toString(),
                        "$mainUrl/asset/userdata/$userData/caption/${it?.hash}/${it?.id}.${it?.extension}"
                    )
                )
            }
        }
    }

    @Serializable
    private data class ResponseData(
        @JsonProperty("file") @SerialName("file") val file: String,
        @JsonProperty("label") @SerialName("label") val label: String,
    )

    @Serializable
    private data class Player(
        @JsonProperty("poster_file") @SerialName("poster_file") val posterFile: String? = null,
    )

    @Serializable
    private data class ResponseJson(
        @JsonProperty("success") @SerialName("success") val success: Boolean,
        @JsonProperty("player") @SerialName("player") val player: Player? = null,
        @JsonProperty("data") @SerialName("data") val data: List<ResponseData>?,
        @JsonProperty("captions") @SerialName("captions") val captions: List<Captions?>?,
    )

    @Serializable
    private data class Captions(
        @JsonProperty("id") @SerialName("id") val id: String,
        @JsonProperty("hash") @SerialName("hash") val hash: String,
        @JsonProperty("language") @SerialName("language") val language: String,
        @JsonProperty("extension") @SerialName("extension") val extension: String,
    )
}
